import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { useAware } from '@/aware/AwareContext';
import { Card } from '@/components/Card';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { ENVIRONMENT_LABELS, SOUNDS } from '@/sounds';
import { colors, radius, spacing, type } from '@/theme';

export default function ListenScreen() {
  const { isListening, activity, error, environment, metrics, settings, startListening, stopListening, triggerDemo } = useAware();
  const [demoVisible, setDemoVisible] = useState(false);
  const [demoBusy, setDemoBusy] = useState<string | null>(null);
  const taps = useRef<number[]>([]);

  const handleTitleTap = () => {
    const now = Date.now();
    taps.current = [...taps.current.filter((time) => now - time < 3_000), now];
    if (taps.current.length >= 5) {
      taps.current = [];
      setDemoVisible((visible) => !visible);
    }
  };

  const runDemo = async (id: (typeof SOUNDS)[number]['id']) => {
    setDemoBusy(id);
    await triggerDemo(id);
    setDemoBusy(null);
  };

  return (
    <Screen>
      <Pressable accessibilityRole="header" accessibilityLabel="Aware" onPress={handleTitleTap}>
        <Text style={styles.title}>Aware</Text>
        <Text style={styles.subtitle}>Important sounds, made visible.</Text>
      </Pressable>

      <Card style={[styles.statusCard, isListening && styles.statusListening]}>
        <View style={styles.statusRow}>
          <View style={[styles.statusDot, { backgroundColor: isListening ? colors.primary : colors.disabled }]} />
          <View style={styles.statusCopy}>
            <Text style={styles.statusTitle}>{isListening ? 'Aware is listening' : 'Listening is off'}</Text>
            <Text style={styles.statusDetail}>{activity === 'analyzing' ? 'Analyzing the latest 2-second segment…' : isListening ? 'Monitoring in 2-second segments' : 'Start to monitor nearby sounds'}</Text>
          </View>
        </View>
        {isListening
          ? <PrimaryButton label="Stop listening" icon="stop-circle-outline" variant="secondary" onPress={() => void stopListening()} />
          : <PrimaryButton label="Start listening" icon="microphone" onPress={() => void startListening()} />}
      </Card>

      {error ? <Card style={styles.errorCard}><Text accessibilityRole="alert" style={styles.errorText}>{error}</Text></Card> : null}

      <View style={styles.metricsRow}>
        <Card style={styles.metricCard}>
          <MaterialCommunityIcons name="weather-windy" size={28} color={colors.primary} />
          <Text style={styles.metricLabel}>Environment</Text>
          <Text style={styles.metricValue}>{environment ? ENVIRONMENT_LABELS[environment] : 'Waiting…'}</Text>
        </Card>
        <Card style={styles.metricCard}>
          <MaterialCommunityIcons name="waveform" size={28} color={colors.primary} />
          <Text style={styles.metricLabel}>Noise floor</Text>
          <Text style={styles.metricValue}>{metrics ? `${Math.round(metrics.noise_floor_db)} dB` : 'Waiting…'}</Text>
        </Card>
      </View>

      <Text style={styles.sectionTitle}>Listening for</Text>
      <View style={styles.soundGrid}>
        {SOUNDS.filter((sound) => settings.enabled[sound.id]).map((sound) => (
          <View key={sound.id} style={styles.soundChip}>
            <MaterialCommunityIcons name={sound.icon} size={24} color={colors.text} />
            <Text style={styles.soundLabel}>{sound.label}</Text>
          </View>
        ))}
      </View>

      {demoVisible ? (
        <Card style={styles.demoCard}>
          <Text style={styles.sectionTitle}>Demo panel</Text>
          <Text style={styles.body}>Demo detections use the same confirmation, cooldown, alert, and event pipeline.</Text>
          {SOUNDS.map((sound) => (
            <PrimaryButton key={sound.id} label={`Demo: ${sound.label}`} variant="secondary" icon={sound.icon} loading={demoBusy === sound.id} disabled={demoBusy !== null && demoBusy !== sound.id} onPress={() => void runDemo(sound.id)} />
          ))}
        </Card>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { color: colors.text, fontSize: type.title, fontWeight: '900' },
  subtitle: { color: colors.muted, fontSize: type.body, marginTop: spacing.xs },
  statusCard: { padding: spacing.lg },
  statusListening: { borderColor: colors.primary, borderWidth: 2 },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, marginBottom: spacing.sm },
  statusDot: { width: 18, height: 18, borderRadius: 9 },
  statusCopy: { flex: 1 },
  statusTitle: { color: colors.text, fontSize: type.heading, fontWeight: '900' },
  statusDetail: { color: colors.muted, fontSize: type.label, marginTop: spacing.xs },
  errorCard: { backgroundColor: colors.criticalDark, borderColor: colors.critical },
  errorText: { color: colors.text, fontSize: type.body, fontWeight: '700' },
  metricsRow: { flexDirection: 'row', gap: spacing.md },
  metricCard: { flex: 1 },
  metricLabel: { color: colors.muted, fontSize: type.small },
  metricValue: { color: colors.text, fontSize: type.body, fontWeight: '800' },
  sectionTitle: { color: colors.text, fontSize: type.heading, fontWeight: '900' },
  soundGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  soundChip: { width: '48%', minHeight: 64, flexDirection: 'row', alignItems: 'center', gap: spacing.sm, backgroundColor: colors.surfaceRaised, borderRadius: radius.md, padding: spacing.sm, borderWidth: 1, borderColor: colors.border },
  soundLabel: { flex: 1, color: colors.text, fontSize: type.label, fontWeight: '700' },
  demoCard: { borderColor: colors.primary },
  body: { color: colors.muted, fontSize: type.label, lineHeight: 23 },
});
