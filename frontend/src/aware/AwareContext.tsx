import AsyncStorage from '@react-native-async-storage/async-storage';
import { File } from 'expo-file-system';
import * as Notifications from 'expo-notifications';
import {
  getRecordingPermissionsAsync,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
  type RecordingOptions,
} from 'expo-audio';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type PropsWithChildren,
} from 'react';
import { AppState } from 'react-native';

import {
  analyzeAudio,
  groupEvents,
  triggerDemo as requestDemo,
  type AnalyzeResponse,
  type CustomSound,
  type EventInput,
  type EventRecord,
  type Detection,
  type Metrics,
} from '@/api';
import {
  configureNotificationChannels,
  notifyInBackground,
  requestNotificationPermission,
  stopVibration,
  vibrateInForeground,
} from '@/notify';
import {
  ENVIRONMENT_LABELS,
  SOUND_IDS,
  type Environment,
  type Sensitivity,
  type SoundId,
} from '@/sounds';
import { loadCustomSound, loadEvents, saveCustomSound, saveEvents } from '@/storage';
import { addToHistory } from '@/utils/eventHistory';
import {
  acknowledgeAlert,
  enqueueAlert,
  evaluateDetection,
  type AlertQueueState,
  type DetectionMemory,
} from '@/utils/alertLogic';

const STORAGE = {
  settings: '@aware/settings',
  onboarding: '@aware/onboarding',
  wasListening: '@aware/was-listening',
} as const;

export const AWARE_RECORDING_OPTIONS: RecordingOptions = {
  extension: '.m4a',
  sampleRate: 16_000,
  numberOfChannels: 1,
  bitRate: 64_000,
  android: {
    extension: '.m4a',
    sampleRate: 16_000,
    outputFormat: 'mpeg4',
    audioEncoder: 'aac',
    audioSource: 'unprocessed',
  },
  ios: {
    extension: '.m4a',
    sampleRate: 16_000,
    audioQuality: 96,
  },
  web: { mimeType: 'audio/webm', bitsPerSecond: 64_000 },
};

export type AwareSettings = {
  autoListen: boolean;
  aiVerify: boolean;
  enabled: Record<SoundId, boolean>;
  sensitivity: Record<SoundId, Sensitivity>;
};

const DEFAULT_SETTINGS: AwareSettings = {
  autoListen: true,
  aiVerify: false,
  enabled: Object.fromEntries(SOUND_IDS.map((id) => [id, true])) as Record<SoundId, boolean>,
  sensitivity: Object.fromEntries(SOUND_IDS.map((id) => [id, 'medium'])) as Record<SoundId, Sensitivity>,
};

export type AlertData = {
  id: string;
  detection: Detection;
  environment: Environment;
  metrics: Metrics;
  urgency: Detection['urgency'];
  receivedAt: number;
  details: string;
  demo: boolean;
  deliveredBy: 'foreground' | 'background' | 'queued';
};

type ContextValue = {
  hydrated: boolean;
  onboardingComplete: boolean;
  isListening: boolean;
  activity: 'starting' | 'listening' | 'analyzing' | 'stopped' | 'error';
  error: string | null;
  environment: Environment | null;
  metrics: Metrics | null;
  settings: AwareSettings;
  currentAlert: AlertData | null;
  queuedAlerts: number;
  taughtSound: CustomSound | null;
  events: EventRecord[];
  clearEvents: () => Promise<void>;
  saveTaughtSound: (sound: CustomSound | null) => Promise<void>;
  setAutoListen: (value: boolean) => void;
  setAiVerify: (value: boolean) => void;
  setSoundEnabled: (id: SoundId, value: boolean) => void;
  setSensitivity: (id: SoundId, value: Sensitivity) => void;
  requestPermissions: () => Promise<{ microphone: boolean; notifications: boolean }>;
  completeOnboarding: () => Promise<void>;
  startListening: () => Promise<void>;
  stopListening: () => Promise<void>;
  acknowledge: () => void;
  triggerDemo: (id: SoundId) => Promise<void>;
};

const AwareContext = createContext<ContextValue | null>(null);

const delay = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds));

function mergeSettings(value: Partial<AwareSettings> | null): AwareSettings {
  if (!value) return DEFAULT_SETTINGS;
  return {
    ...DEFAULT_SETTINGS,
    ...value,
    enabled: { ...DEFAULT_SETTINGS.enabled, ...value.enabled },
    sensitivity: { ...DEFAULT_SETTINGS.sensitivity, ...value.sensitivity },
  };
}

function removeLocalRecording(uri: string) {
  try {
    const file = new File(uri);
    if (file.exists) file.delete();
  } catch {
    // Cache cleanup is best-effort; analysis and alerts must keep running.
  }
}

export function AwareProvider({ children }: PropsWithChildren) {
  const recorder = useAudioRecorder(AWARE_RECORDING_OPTIONS);
  const [hydrated, setHydrated] = useState(false);
  const [onboardingComplete, setOnboardingComplete] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [activity, setActivity] = useState<ContextValue['activity']>('stopped');
  const [error, setError] = useState<string | null>(null);
  const [environment, setEnvironment] = useState<Environment | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [settings, setSettings] = useState<AwareSettings>(DEFAULT_SETTINGS);
  const [alerts, setAlerts] = useState<AlertQueueState<AlertData>>({ current: null, queued: [] });
  const [taughtSound, setTaughtSound] = useState<CustomSound | null>(null);
  const [events, setEvents] = useState<EventRecord[]>([]);
  const eventsRef = useRef<EventRecord[]>([]);
  const customSoundRef = useRef<CustomSound | null>(null);
  const historyQueueRef = useRef<Promise<void>>(Promise.resolve());
  const historyGenerationRef = useRef(0);

  const queueHistory = useCallback((operation: () => Promise<void>) => {
    const pending = historyQueueRef.current.then(operation);
    historyQueueRef.current = pending.catch(() => undefined);
    return pending;
  }, []);

  const logEvent = useCallback((event: EventInput, id: string) => {
    const generation = historyGenerationRef.current;
    return queueHistory(async () => {
      if (generation !== historyGenerationRef.current) return;
      const updated = await addToHistory(eventsRef.current, event, id, groupEvents);
      if (generation !== historyGenerationRef.current) return;
      await saveEvents(updated);
      eventsRef.current = updated;
      setEvents(updated);
    });
  }, [queueHistory]);

  const clearEvents = useCallback(() => {
    // Discard pending grouping replies so clearing cannot resurrect older events.
    historyGenerationRef.current += 1;
    return queueHistory(async () => {
      await saveEvents([]);
      eventsRef.current = [];
      setEvents([]);
    });
  }, [queueHistory]);

  const saveTaughtSound = useCallback(async (sound: CustomSound | null) => {
    await saveCustomSound(sound);
    customSoundRef.current = sound;
    setTaughtSound(sound);
  }, []);

  const settingsRef = useRef(settings);
  const alertRef = useRef(alerts);
  const memoryRef = useRef<DetectionMemory>({ confirmations: {}, cooldowns: {} });
  const appStateRef = useRef<string | null | undefined>(AppState.currentState);
  const stopRequestedRef = useRef(true);
  const startingRef = useRef(false);
  const restoreListeningRef = useRef(false);
  const restoreAttemptedRef = useRef(false);

  useEffect(() => {
    settingsRef.current = settings;
    if (hydrated) void AsyncStorage.setItem(STORAGE.settings, JSON.stringify(settings));
  }, [hydrated, settings]);

  useEffect(() => {
    alertRef.current = alerts;
  }, [alerts]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      appStateRef.current = nextState;
      if (nextState !== 'active') stopVibration();
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    void (async () => {
      await configureNotificationChannels().catch(() => undefined);
      const [storedSettings, onboarded, wasListening, storedEvents, customSound] = await Promise.all([
        AsyncStorage.getItem(STORAGE.settings),
        AsyncStorage.getItem(STORAGE.onboarding),
        AsyncStorage.getItem(STORAGE.wasListening),
        loadEvents(),
        loadCustomSound(),
      ]);
      const nextSettings = mergeSettings(storedSettings ? (JSON.parse(storedSettings) as Partial<AwareSettings>) : null);
      settingsRef.current = nextSettings;
      setSettings(nextSettings);
      setOnboardingComplete(onboarded === 'true');
      restoreListeningRef.current = nextSettings.autoListen || wasListening === 'true';
      eventsRef.current = storedEvents;
      setEvents(storedEvents);
      customSoundRef.current = customSound;
      setTaughtSound(customSound);
      setHydrated(true);
    })().catch((caught: unknown) => {
      setError(caught instanceof Error ? caught.message : 'Could not load data stored on this phone.');
    });
  }, []);

  const surfaceAlert = useCallback((response: AnalyzeResponse) => {
    const detection = response.detection;
    if (!detection) return;

    const decision = evaluateDetection(
      {
        classId: detection.class_id,
        shouldAlertNow: response.should_alert_now,
        needsConfirmation: response.needs_confirmation,
        now: Date.now(),
      },
      memoryRef.current,
    );
    memoryRef.current = decision.memory;
    if (!decision.alert) return;

    const details = `${detection.frames_supporting}/${detection.frames_total} frames · ${ENVIRONMENT_LABELS[response.environment]} · SNR ${Math.round(response.metrics.snr_db)} dB`;
    const incoming: AlertData = {
      id: `${Date.now()}-${detection.class_id}-${Math.random().toString(36).slice(2)}`,
      detection,
      environment: response.environment,
      metrics: response.metrics,
      urgency: detection.urgency,
      receivedAt: Date.now(),
      details,
      demo: response.demo === true,
      deliveredBy: 'queued',
    };

    const next = enqueueAlert(alertRef.current, incoming);
    const becameCurrent = next.current === incoming;
    if (appStateRef.current !== 'active') {
      incoming.deliveredBy = 'background';
      void notifyInBackground(detection, details, incoming.demo);
    } else if (becameCurrent) {
      incoming.deliveredBy = 'foreground';
      vibrateInForeground(detection);
    }
    alertRef.current = next;
    setAlerts(next);

    // Alert delivery is initiated first. Logging is intentionally fire-and-forget.
    void logEvent({
      class_id: detection.class_id,
      label: detection.label,
      urgency: detection.urgency,
      confidence: detection.confidence,
      score: detection.score,
      details,
      muted: false,
      demo: incoming.demo,
      detected_at: new Date(incoming.receivedAt).toISOString(),
    }, incoming.id).catch(() => setError('Could not save event history on this phone.'));
  }, [logEvent]);

  const runLoop = useCallback(async () => {
    while (!stopRequestedRef.current) {
      let uri: string | null = null;
      try {
        setActivity('listening');
        await recorder.prepareToRecordAsync(AWARE_RECORDING_OPTIONS);
        recorder.record();
        await delay(2_000);
        await recorder.stop();
        uri = recorder.uri;
        if (stopRequestedRef.current || !uri) break;

        setActivity('analyzing');
        const currentSettings = settingsRef.current;
        const enabled = SOUND_IDS.filter((id) => currentSettings.enabled[id]);
        const response = await analyzeAudio(uri, currentSettings.sensitivity, enabled, currentSettings.aiVerify, customSoundRef.current);
        setEnvironment(response.environment);
        setMetrics(response.metrics);
        surfaceAlert(response);
        setError(null);
      } catch (caught) {
        if (!stopRequestedRef.current) {
          setError(caught instanceof Error ? caught.message : 'Listening was interrupted.');
          setActivity('error');
          await delay(1_500);
        }
      } finally {
        if (uri) removeLocalRecording(uri);
      }
    }
    setIsListening(false);
    if (stopRequestedRef.current) setActivity('stopped');
  }, [recorder, surfaceAlert]);

  const startListening = useCallback(async () => {
    if (startingRef.current || !stopRequestedRef.current) return;
    startingRef.current = true;
    setActivity('starting');
    setError(null);
    try {
      const permission = await getRecordingPermissionsAsync();
      if (!permission.granted) throw new Error('Microphone permission is required to listen.');
      await setAudioModeAsync({
        allowsRecording: true,
        allowsBackgroundRecording: true,
        playsInSilentMode: true,
        interruptionMode: 'mixWithOthers',
      });
      stopRequestedRef.current = false;
      setIsListening(true);
      await AsyncStorage.setItem(STORAGE.wasListening, 'true');
      void runLoop();
    } catch (caught) {
      stopRequestedRef.current = true;
      setIsListening(false);
      setActivity('error');
      setError(caught instanceof Error ? caught.message : 'Unable to start listening.');
    } finally {
      startingRef.current = false;
    }
  }, [runLoop]);

  const stopListening = useCallback(async () => {
    stopRequestedRef.current = true;
    stopVibration();
    if (recorder.isRecording) await recorder.stop().catch(() => undefined);
    setIsListening(false);
    setActivity('stopped');
    await AsyncStorage.setItem(STORAGE.wasListening, 'false');
  }, [recorder]);

  useEffect(() => {
    if (!hydrated || !onboardingComplete || restoreAttemptedRef.current) return;
    restoreAttemptedRef.current = true;
    if (restoreListeningRef.current) void startListening();
  }, [hydrated, onboardingComplete, startListening]);

  const requestPermissions = useCallback(async () => {
    const microphone = (await requestRecordingPermissionsAsync()).granted;
    const notifications = await requestNotificationPermission().catch(() => false);
    return { microphone, notifications };
  }, []);

  const completeOnboarding = useCallback(async () => {
    await AsyncStorage.setItem(STORAGE.onboarding, 'true');
    setOnboardingComplete(true);
    restoreAttemptedRef.current = true;
    await startListening();
  }, [startListening]);

  const acknowledge = useCallback(() => {
    stopVibration();
    const next = acknowledgeAlert(alertRef.current);
    const newlyCurrent = next.current;
    if (newlyCurrent?.deliveredBy === 'queued' && appStateRef.current === 'active') {
      newlyCurrent.deliveredBy = 'foreground';
      vibrateInForeground(newlyCurrent.detection);
    }
    alertRef.current = next;
    setAlerts(next);
  }, []);

  const triggerDemo = useCallback(async (id: SoundId) => {
    setError(null);
    try {
      const response = await requestDemo(id, customSoundRef.current?.name);
      setEnvironment(response.environment);
      setMetrics(response.metrics);
      surfaceAlert({ ...response, demo: true });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Demo failed.');
    }
  }, [surfaceAlert]);

  const value: ContextValue = {
    hydrated,
    onboardingComplete,
    isListening,
    activity,
    error,
    environment,
    metrics,
    settings,
    currentAlert: alerts.current,
    queuedAlerts: alerts.queued.length,
    taughtSound,
    setAutoListen: (autoListen) => setSettings((current) => ({ ...current, autoListen })),
    setAiVerify: (aiVerify) => setSettings((current) => ({ ...current, aiVerify })),
    setSoundEnabled: (id, enabled) => setSettings((current) => ({ ...current, enabled: { ...current.enabled, [id]: enabled } })),
    setSensitivity: (id, sensitivity) => setSettings((current) => ({ ...current, sensitivity: { ...current.sensitivity, [id]: sensitivity } })),
    requestPermissions,
    completeOnboarding,
    startListening,
    stopListening,
    acknowledge,
    triggerDemo,
    events,
    clearEvents,
    saveTaughtSound,
  };

  return <AwareContext.Provider value={value}>{children}</AwareContext.Provider>;
}

export function useAware(): ContextValue {
  const value = useContext(AwareContext);
  if (!value) throw new Error('useAware must be used inside AwareProvider.');
  return value;
}

export async function notificationPermissionStatus(): Promise<boolean> {
  return (await Notifications.getPermissionsAsync()).granted;
}
