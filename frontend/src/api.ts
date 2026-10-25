import type { Confidence, Environment, Sensitivity, SoundId, Urgency } from '@/sounds';

const configuredUrl = process.env.EXPO_PUBLIC_BACKEND_URL?.trim();
export const BACKEND_URL = configuredUrl?.replace(/\/$/, '') ?? '';

export type TopLabel = { name: string; score: number };
export type Detection = {
  class_id: SoundId;
  label: string;
  urgency: Urgency;
  critical: boolean;
  confidence: Confidence;
  score: number;
  frames_supporting: number;
  frames_total: number;
  source: 'raw' | 'denoised' | 'custom';
  top_labels: TopLabel[];
  ai_verified: boolean | null;
};

export type Metrics = {
  rms_db: number;
  peak: number;
  clipping: number;
  noise_floor_db: number;
  snr_db: number;
  duration_s: number;
};

export type AnalyzeResponse = {
  detection: Detection | null;
  environment: Environment;
  metrics: Metrics;
  should_alert_now: boolean;
  needs_confirmation: boolean;
  demo?: boolean;
  fallback?: boolean;
};

export type EventRecord = {
  id: string;
  class_id: SoundId;
  label: string;
  urgency: Urgency;
  confidence: Confidence;
  score?: number;
  details: string;
  muted: boolean;
  demo: boolean;
  detected_at?: string;
  first_detected_at: string;
  last_detected_at: string;
  repeat_count: number;
  max_score: number;
  best_confidence: Confidence;
  jev_grouped: boolean;
  jev_probability: number | null;
  grouped: boolean;
};

export type EventInput = {
  class_id: SoundId;
  label: string;
  urgency: Urgency;
  confidence: Confidence;
  score: number;
  details: string;
  muted: boolean;
  demo: boolean;
  detected_at: string;
};

export type TeachResult = {
  ok: boolean;
  reason?: 'too_short' | 'too_quiet' | 'clipped' | 'silence' | 'speech' | 'noisy' | 'mismatch';
  message?: string;
  fingerprint?: number[];
  similarity?: number;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (!BACKEND_URL) throw new Error('Set EXPO_PUBLIC_BACKEND_URL to the Aware backend address.');
  let response: Response;
  try {
    response = await fetch(`${BACKEND_URL}${path}`, init);
  } catch {
    throw new Error('Aware could not reach the listening service. Check the backend URL and network.');
  }
  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(body || `Aware service returned ${response.status}.`);
  }
  return response.json() as Promise<T>;
}

function audioPart(uri: string, name: string) {
  return { uri, name, type: 'audio/mp4' } as unknown as Blob;
}

export async function analyzeAudio(
  uri: string,
  sensitivity: Record<SoundId, Sensitivity>,
  enabled: SoundId[],
  aiVerify: boolean,
  customSound?: CustomSound | null,
): Promise<AnalyzeResponse> {
  const form = new FormData();
  form.append('file', audioPart(uri, 'aware-segment.m4a'));
  form.append('sensitivity', JSON.stringify(sensitivity));
  form.append('enabled', JSON.stringify(enabled));
  form.append('ai_verify', String(aiVerify));
  if (customSound) {
    form.append('custom_fingerprint', JSON.stringify(customSound.fingerprint));
    form.append('custom_name', customSound.name);
  }
  return request('/api/listen/analyze', { method: 'POST', body: form });
}

export type CustomSound = { name: string; created_at: string; fingerprint: number[] };
export type ServiceStatus = { model_loaded: boolean; jev_configured: boolean; groq_configured: boolean };
export type GroupResult = { probability: number | null; same_occurrence: boolean };
export type EventText = Pick<EventInput, 'class_id' | 'label' | 'urgency' | 'confidence' | 'details' | 'muted'>;

export function getStatus(): Promise<ServiceStatus> {
  return request('/api/status');
}

export function triggerDemo(classId: SoundId, customName?: string): Promise<AnalyzeResponse> {
  const query = customName ? `?custom_name=${encodeURIComponent(customName)}` : '';
  return request(`/api/demo/sound/${classId}${query}`, { method: 'POST' });
}

function eventText(event: EventText): EventText {
  const { class_id, label, urgency, confidence, details, muted } = event;
  return { class_id, label, urgency, confidence, details, muted };
}

export async function groupEvents(incoming: EventText, previous: EventText, secondsApart: number): Promise<GroupResult> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 3_000);
  try {
    return await request('/api/events/group', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ new: eventText(incoming), previous: eventText(previous), seconds_apart: secondsApart }),
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timeout);
  }
}

export async function submitTeachSample(
  uri: string,
  name: string,
  step: 1 | 2,
  firstFingerprint?: number[],
): Promise<TeachResult> {
  const form = new FormData();
  form.append('file', audioPart(uri, `aware-teach-${step}.m4a`));
  form.append('name', name);
  form.append('step', String(step));
  if (firstFingerprint) form.append('first_fingerprint', JSON.stringify(firstFingerprint));
  return request('/api/teach/sample', { method: 'POST', body: form });
}
