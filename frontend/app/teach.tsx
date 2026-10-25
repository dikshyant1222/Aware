import { File } from 'expo-file-system';
import { setAudioModeAsync, useAudioRecorder } from 'expo-audio';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, TextInput } from 'react-native';

import { submitTeachSample } from '@/api';
import { AWARE_RECORDING_OPTIONS, useAware } from '@/aware/AwareContext';
import { Card } from '@/components/Card';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { colors, spacing, type } from '@/theme';

const REASONS: Record<string, string> = {
  too_short: 'The sample was too short.',
  too_quiet: 'The sound was too quiet. Move closer and try again.',
  clipped: 'The sound was too loud and distorted. Move farther away.',
  silence: 'Most of the sample was silent.',
  speech: 'The sample contained mostly speech. Use a non-speech sound.',
  noisy: 'The sound was unclear against the background noise.',
  mismatch: 'The second sample did not match the first. Try both samples again.',
};

const wait = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds));

export default function TeachScreen() {
  const recorder = useAudioRecorder(AWARE_RECORDING_OPTIONS);
  const { isListening, stopListening, startListening, saveTaughtSound } = useAware();
  const resumeRef = useRef(isListening);
  const [name, setName] = useState('');
  const [step, setStep] = useState<1 | 2>(1);
  const [firstFingerprint, setFirstFingerprint] = useState<number[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('Name the sound, then record it twice in the same conditions.');
  const [complete, setComplete] = useState(false);

  useEffect(() => {
    const shouldResume = resumeRef.current;
    if (shouldResume) void stopListening();
    return () => { if (shouldResume) void startListening(); };
  }, [startListening, stopListening]);

  const record = async () => {
    if (name.trim().length < 2) {
      setMessage('Enter a name with at least two characters.');
      return;
    }
    setBusy(true);
    setMessage(`Recording sample ${step} for 3 seconds… Make the sound now.`);
    let uri: string | null = null;
    try {
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true, interruptionMode: 'mixWithOthers' });
      await recorder.prepareToRecordAsync(AWARE_RECORDING_OPTIONS);
      recorder.record();
      await wait(3_000);
      await recorder.stop();
      uri = recorder.uri;
      if (!uri) throw new Error('No recording was created.');
      setMessage('Checking sample quality…');
      const result = await submitTeachSample(uri, name.trim(), step, firstFingerprint ?? undefined);
      if (!result.ok) {
        setMessage(result.message || (result.reason ? REASONS[result.reason] : 'That sample could not be used.'));
        if (result.reason === 'mismatch') { setStep(1); setFirstFingerprint(null); }
        return;
      }
      if (!result.fingerprint) throw new Error('The service did not return a sound fingerprint.');
      if (step === 1) {
        setFirstFingerprint(result.fingerprint);
        setStep(2);
        setMessage('First sample passed. Record the same sound once more.');
      } else {
        await saveTaughtSound({ name: name.trim(), created_at: new Date().toISOString(), fingerprint: result.fingerprint });
        setComplete(true);
        setMessage(`Saved ${name.trim()}${result.similarity ? ` · ${Math.round(result.similarity * 100)}% match` : ''}.`);
      }
    } catch (caught) {
      setMessage(caught instanceof Error ? caught.message : 'Teaching was interrupted.');
    } finally {
      if (uri) {
        try { const file = new File(uri); if (file.exists) file.delete(); } catch { /* best effort */ }
      }
      setBusy(false);
    }
  };

  return (
    <Screen>
      <Text style={styles.title}>Teach one custom sound</Text>
      <Text style={styles.body}>Use a distinctive non-speech sound. Aware saves a normalized fingerprint on this phone, never the recording.</Text>
      <Card>
        <Text style={styles.label}>Sound name</Text>
        <TextInput accessibilityLabel="Custom sound name" value={name} editable={!busy && step === 1 && !complete} onChangeText={setName} placeholder="Example: Kitchen timer" placeholderTextColor={colors.disabled} style={styles.input} maxLength={40} />
      </Card>
      <Card>
        <Text style={styles.step}>Sample {complete ? 'complete' : `${step} of 2`}</Text>
        <Text accessibilityLiveRegion="polite" style={styles.message}>{message}</Text>
        {!complete ? <PrimaryButton label={busy ? 'Recording / checking…' : `Record sample ${step}`} icon="record-circle-outline" loading={busy} onPress={() => void record()} /> : null}
      </Card>
      <Text style={styles.tip}>For best results: keep the phone at a similar distance, avoid speech, and minimize background TV, music, or traffic.</Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { color: colors.text, fontSize: type.title, fontWeight: '900' },
  body: { color: colors.muted, fontSize: type.body, lineHeight: 27 },
  label: { color: colors.text, fontSize: type.label, fontWeight: '800' },
  input: { minHeight: 56, borderWidth: 2, borderColor: colors.border, borderRadius: 12, color: colors.text, fontSize: type.body, paddingHorizontal: spacing.md, backgroundColor: colors.background },
  step: { color: colors.primary, fontSize: type.heading, fontWeight: '900' },
  message: { color: colors.text, fontSize: type.body, lineHeight: 27 },
  tip: { color: colors.muted, fontSize: type.label, lineHeight: 23 },
});
