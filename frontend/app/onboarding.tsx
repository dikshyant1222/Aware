import { MaterialCommunityIcons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { useAware } from '@/aware/AwareContext';
import { Card } from '@/components/Card';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { colors, spacing, type } from '@/theme';

export default function OnboardingScreen() {
  const { requestPermissions, completeOnboarding } = useAware();
  const [step, setStep] = useState(0);
  const [microphone, setMicrophone] = useState(false);
  const [notifications, setNotifications] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const allow = async () => {
    setBusy(true);
    const result = await requestPermissions();
    setMicrophone(result.microphone);
    setNotifications(result.notifications);
    setBusy(false);
    if (result.microphone) setStep(2);
    else setError('Microphone permission is required for Aware to listen.');
  };

  const start = async () => {
    setBusy(true);
    await completeOnboarding();
    setBusy(false);
    router.replace('/(tabs)');
  };

  return (
    <Screen scroll={false} contentStyle={styles.content}>
      <View style={styles.progress} accessibilityLabel={`Onboarding step ${step + 1} of 3`}>
        {[0, 1, 2].map((value) => <View key={value} style={[styles.progressItem, value <= step && styles.progressActive]} />)}
      </View>

      {step === 0 ? (
        <>
          <MaterialCommunityIcons name="ear-hearing" size={104} color={colors.primary} />
          <Text style={styles.title}>Stay aware of important sounds</Text>
          <Text style={styles.body}>Aware turns fire alarms, sirens, doorbells, baby cries, vehicle horns, and one custom sound into clear visual and vibration alerts.</Text>
          <Card><Text style={styles.note}>Aware supports awareness, but it is not a certified safety device and requires a network connection.</Text></Card>
          <PrimaryButton label="Continue" icon="arrow-right" onPress={() => setStep(1)} />
        </>
      ) : null}

      {step === 1 ? (
        <>
          <MaterialCommunityIcons name="shield-check-outline" size={96} color={colors.primary} />
          <Text style={styles.title}>Allow listening and alerts</Text>
          <Text style={styles.body}>Microphone access lets Aware analyze 2-second sound segments. Notification access delivers class-specific vibration alerts in the background.</Text>
          <Card>
            <Permission icon="microphone" label="Microphone" allowed={microphone} required />
            <Permission icon="bell-outline" label="Notifications" allowed={notifications} />
          </Card>
          {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
          <PrimaryButton label="Allow permissions" loading={busy} onPress={() => void allow()} />
        </>
      ) : null}

      {step === 2 ? (
        <>
          <MaterialCommunityIcons name="check-decagram" size={104} color={colors.primary} />
          <Text style={styles.title}>Ready to listen</Text>
          <Text style={styles.body}>Starting Aware begins listening immediately. Android will show a persistent microphone notification while background listening is active.</Text>
          <PrimaryButton label="Start Aware" icon="microphone" loading={busy} onPress={() => void start()} />
        </>
      ) : null}
    </Screen>
  );
}

function Permission({ icon, label, allowed, required = false }: { icon: 'microphone' | 'bell-outline'; label: string; allowed: boolean; required?: boolean }) {
  return (
    <View style={styles.permission}>
      <MaterialCommunityIcons name={icon} size={28} color={allowed ? colors.primary : colors.muted} />
      <Text style={styles.permissionLabel}>{label}{required ? ' · required' : ''}</Text>
      <Text style={[styles.permissionStatus, allowed && styles.allowed]}>{allowed ? 'Allowed' : 'Needs access'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { justifyContent: 'center', alignItems: 'center' },
  progress: { position: 'absolute', top: spacing.lg, left: spacing.lg, right: spacing.lg, flexDirection: 'row', gap: spacing.sm },
  progressItem: { flex: 1, height: 6, borderRadius: 3, backgroundColor: colors.border },
  progressActive: { backgroundColor: colors.primary },
  title: { color: colors.text, fontSize: type.title, lineHeight: 42, fontWeight: '900', textAlign: 'center' },
  body: { color: colors.muted, fontSize: type.body, lineHeight: 28, textAlign: 'center' },
  note: { color: colors.text, fontSize: type.label, lineHeight: 23, textAlign: 'center' },
  error: { color: colors.danger, fontSize: type.label, fontWeight: '700', textAlign: 'center' },
  permission: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  permissionLabel: { flex: 1, color: colors.text, fontSize: type.label, fontWeight: '800' },
  permissionStatus: { color: colors.high, fontSize: type.small, fontWeight: '800' },
  allowed: { color: colors.primary },
});
