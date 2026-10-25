import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useAware } from '@/aware/AwareContext';
import { escalationMessage } from '@/escalation';
import { ENVIRONMENT_LABELS, SOUND_BY_ID } from '@/sounds';
import { colors, radius, spacing, type } from '@/theme';

export function AlertOverlay() {
  const { currentAlert, queuedAlerts, acknowledge, escalation, trustedContact } = useAware();
  if (!currentAlert) return null;

  const { detection, metrics, environment, demo } = currentAlert;
  const palette = detection.urgency === 'critical'
    ? { background: colors.criticalDark, accent: colors.critical }
    : detection.urgency === 'high'
      ? { background: colors.highDark, accent: colors.high }
      : { background: colors.attentionDark, accent: colors.attention };

  return (
    <Modal visible animationType="fade" statusBarTranslucent onRequestClose={() => undefined}>
      <SafeAreaView style={[styles.safe, { backgroundColor: palette.background }]}>
        <ScrollView contentContainerStyle={styles.container} accessibilityViewIsModal>
          {demo ? <Text style={styles.demo}>DEMO ALERT</Text> : null}
          <MaterialCommunityIcons name={SOUND_BY_ID[detection.class_id].icon} size={104} color={palette.accent} accessibilityElementsHidden />
          <Text accessibilityRole="alert" style={[styles.name, { color: palette.accent }]}>{detection.label}</Text>
          <Text style={styles.confidence}>{detection.confidence === 'likely' ? 'LIKELY' : 'POSSIBLE'}</Text>

          <View style={styles.details}>
            <Detail label="Model signal" value={`${Math.round(detection.score * 100)}%`} />
            <Detail label="Supporting evidence" value={`${detection.frames_supporting} of ${detection.frames_total} frames`} />
            <Detail label="Environment" value={ENVIRONMENT_LABELS[environment]} />
            <Detail label="Signal-to-noise" value={`${Math.round(metrics.snr_db)} dB`} />
            <Detail label="Source" value={detection.source} />
          </View>

          {queuedAlerts > 0 ? <Text style={styles.queued}>{queuedAlerts} more alert{queuedAlerts === 1 ? '' : 's'} waiting</Text> : null}
          {detection.critical && !demo && (trustedContact.enabled || escalation) ? <Text accessibilityLiveRegion="polite" style={styles.queued}>{escalationMessage(escalation)}</Text> : null}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Acknowledge ${detection.label} alert`}
            onPress={acknowledge}
            style={({ pressed }) => [styles.acknowledge, { borderColor: palette.accent }, pressed && styles.pressed]}
          >
            <Text style={styles.acknowledgeText}>ACKNOWLEDGE</Text>
          </Pressable>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailRow}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.lg, gap: spacing.md },
  demo: { color: colors.text, fontWeight: '900', fontSize: type.label, letterSpacing: 2, borderWidth: 2, borderColor: colors.text, paddingHorizontal: spacing.md, paddingVertical: spacing.xs, borderRadius: radius.sm },
  name: { fontSize: 42, lineHeight: 48, fontWeight: '900', textAlign: 'center' },
  confidence: { color: colors.text, fontSize: type.heading, fontWeight: '900', letterSpacing: 3 },
  details: { width: '100%', maxWidth: 520, backgroundColor: '#00000066', borderRadius: radius.md, padding: spacing.md, gap: spacing.sm },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.md },
  detailLabel: { flex: 1, color: colors.muted, fontSize: type.label },
  detailValue: { flex: 1, color: colors.text, fontSize: type.label, fontWeight: '800', textAlign: 'right', textTransform: 'capitalize' },
  queued: { color: colors.text, fontSize: type.label, fontWeight: '700' },
  acknowledge: { width: '100%', maxWidth: 520, minHeight: 68, borderWidth: 3, borderRadius: radius.md, backgroundColor: colors.background, justifyContent: 'center', padding: spacing.md },
  acknowledgeText: { color: colors.text, fontSize: type.heading, fontWeight: '900', textAlign: 'center' },
  pressed: { opacity: 0.75 },
});
