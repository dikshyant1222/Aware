import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, View } from 'react-native';

import type { EventRecord } from '@/api';
import { useAware } from '@/aware/AwareContext';
import { Card } from '@/components/Card';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { SOUND_BY_ID } from '@/sounds';
import { colors, spacing, type } from '@/theme';

const time = (value: string) => new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date(value));
const day = (value: string) => new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(new Date(value));

export default function EventsScreen() {
  const { events, clearEvents, hydrated } = useAware();
  const loading = !hydrated;
  const [error, setError] = useState<string | null>(null);

  const confirmClear = () => Alert.alert(
    'Clear event history?',
    'This removes all text event summaries. It cannot be undone.',
    [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Clear all', style: 'destructive', onPress: () => void clearEvents().catch((caught: unknown) => setError(caught instanceof Error ? caught.message : 'Could not clear events.')) },
    ],
  );

  return (
    <Screen>
      <View style={styles.header}>
        <View style={styles.headerCopy}>
          <Text style={styles.title}>Events</Text>
          <Text style={styles.subtitle}>Saved on this phone. Text summaries only.</Text>
        </View>
        {events.length > 0 ? <PrimaryButton label="Clear all" accessibilityLabel="Clear all event history" variant="danger" onPress={confirmClear} style={styles.clear} /> : null}
      </View>

      {loading ? <ActivityIndicator size="large" color={colors.primary} /> : null}
      {error ? <Text accessibilityRole="alert" style={styles.error}>{error}</Text> : null}
      {!loading && events.length === 0 ? (
        <Card style={styles.empty}>
          <MaterialCommunityIcons name="history" size={56} color={colors.muted} />
          <Text style={styles.emptyTitle}>No events yet</Text>
          <Text style={styles.emptyText}>Detected sounds will appear here after their alert is delivered.</Text>
        </Card>
      ) : null}

      {events.map((event) => <EventRow key={event.id} event={event} />)}
    </Screen>
  );
}

function EventRow({ event }: { event: EventRecord }) {
  const sound = SOUND_BY_ID[event.class_id];
  const accent = event.urgency === 'critical' ? colors.critical : event.urgency === 'high' ? colors.high : colors.attention;
  const range = event.repeat_count > 1
    ? `${day(event.first_detected_at)} · ${time(event.first_detected_at)}–${time(event.last_detected_at)}`
    : `${day(event.last_detected_at)} · ${time(event.last_detected_at)}`;

  return (
    <Card style={[styles.event, { borderLeftColor: accent }]}>
      <View style={styles.eventTop}>
        <MaterialCommunityIcons name={sound.icon} size={34} color={accent} />
        <View style={styles.eventCopy}>
          <Text style={styles.eventLabel}>{event.label}</Text>
          <Text style={styles.eventTime}>{range}</Text>
        </View>
        {event.repeat_count > 1 ? <View style={styles.badge}><Text style={styles.badgeText}>×{event.repeat_count}</Text></View> : null}
      </View>
      <Text style={styles.meta}>{event.best_confidence.toUpperCase()} · {Math.round(event.max_score * 100)}% · {event.muted ? 'MUTED' : 'ALERTED'}</Text>
      <Text style={styles.details}>{event.details}</Text>
      <View style={styles.flags}>
        {event.jev_grouped ? <Text style={styles.jev}>JEV GROUPED · {Math.round((event.jev_probability ?? 0) * 100)}%</Text> : null}
        {event.demo ? <Text style={styles.demo}>DEMO</Text> : null}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  headerCopy: { flex: 1 },
  title: { color: colors.text, fontSize: type.title, fontWeight: '900' },
  subtitle: { color: colors.muted, fontSize: type.label },
  clear: { minHeight: 48, paddingHorizontal: spacing.md },
  error: { color: colors.danger, fontSize: type.body },
  empty: { alignItems: 'center', paddingVertical: spacing.xl },
  emptyTitle: { color: colors.text, fontSize: type.heading, fontWeight: '900' },
  emptyText: { color: colors.muted, fontSize: type.body, textAlign: 'center' },
  event: { borderLeftWidth: 6 },
  eventTop: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  eventCopy: { flex: 1 },
  eventLabel: { color: colors.text, fontSize: type.body, fontWeight: '900' },
  eventTime: { color: colors.muted, fontSize: type.small, marginTop: spacing.xs },
  badge: { backgroundColor: colors.primaryDark, borderRadius: 99, paddingHorizontal: spacing.sm, paddingVertical: spacing.xs },
  badgeText: { color: colors.primary, fontWeight: '900', fontSize: type.label },
  meta: { color: colors.text, fontSize: type.small, fontWeight: '800' },
  details: { color: colors.muted, fontSize: type.label },
  flags: { flexDirection: 'row', gap: spacing.sm },
  jev: { color: colors.primary, fontSize: type.small, fontWeight: '900' },
  demo: { color: colors.high, fontSize: type.small, fontWeight: '900' },
});
