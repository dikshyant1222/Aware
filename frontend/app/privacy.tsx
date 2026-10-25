import { MaterialCommunityIcons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

import { Card } from '@/components/Card';
import { Screen } from '@/components/Screen';
import { colors, spacing, type } from '@/theme';

const ITEMS = [
  ['account-alert-outline', 'Optional trusted-contact SMS', 'Your saved contact stays on this phone. For an enabled critical alert, the server temporarily stores the phone number, your name, and sound class to schedule SMS. The number and name are removed after cancellation or a send attempt; status records expire after 24 hours. Twilio processes the recipient and message. A scheduled SMS continues if the app closes; reconnect to cancel it.'],
  ['memory', 'Audio is decoded in memory', 'The backend deletes uploaded raw bytes immediately after decoding. Audio is never retained by the service.'],
  ['text-box-outline', 'Only text summaries are stored', 'Event class, label, confidence, context, and grouping metadata are saved only on this phone for history.'],
  ['account-check-outline', 'Jev receives text only', 'Duplicate grouping uses event metadata, never recordings. Jev never suppresses an alert.'],
  ['robot-outline', 'Groq receives label metadata only', 'Optional AI verification receives predicted labels, scores, and loudness—never audio.'],
  ['fingerprint', 'Custom sounds use a fingerprint', 'Aware saves one normalized fingerprint on this phone after two matching samples. It is sent with analysis requests and never retained by the service.'],
  ['microphone-off', 'You remain in control', 'Stopping Aware, force-stopping the app, or removing microphone permission stops listening.'],
] as const;

export default function PrivacyScreen() {
  return (
    <Screen>
      <Text style={styles.title}>Privacy by design</Text>
      <Text style={styles.body}>Aware processes short sound segments only to identify important environmental sounds.</Text>
      {ITEMS.map(([icon, heading, detail]) => (
        <Card key={heading}>
          <View style={styles.row}>
            <MaterialCommunityIcons name={icon} size={32} color={colors.primary} />
            <View style={styles.copy}><Text style={styles.heading}>{heading}</Text><Text style={styles.detail}>{detail}</Text></View>
          </View>
        </Card>
      ))}
      <Text style={styles.warning}>Background listening uses an Android microphone foreground service with a persistent system notification. Android may stop it because of device battery policies.</Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { color: colors.text, fontSize: type.title, fontWeight: '900' },
  body: { color: colors.muted, fontSize: type.body, lineHeight: 27 },
  row: { flexDirection: 'row', gap: spacing.md },
  copy: { flex: 1 },
  heading: { color: colors.text, fontSize: type.body, fontWeight: '900' },
  detail: { color: colors.muted, fontSize: type.label, lineHeight: 23, marginTop: spacing.xs },
  warning: { color: colors.high, fontSize: type.label, lineHeight: 23, fontWeight: '700' },
});
