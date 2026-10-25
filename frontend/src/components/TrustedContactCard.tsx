import { useEffect, useState } from 'react';
import { Alert, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { useAware } from '@/aware/AwareContext';
import { escalationMessage } from '@/escalation';
import { Card } from '@/components/Card';
import { PrimaryButton } from '@/components/PrimaryButton';
import { colors, radius, spacing, type } from '@/theme';

export function TrustedContactCard() {
  const { trustedContact, saveTrustedContact, escalation, cancelContactAlert } = useAware();
  const [draft, setDraft] = useState(trustedContact);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  useEffect(() => { setDraft(trustedContact); }, [trustedContact]);

  async function save(remove = false) {
    setBusy(true);
    setNotice('');
    try {
      await saveTrustedContact(remove ? { ...draft, enabled: false, name: '', phone: '', userName: '', consent: false } : draft);
      setNotice(remove ? 'Trusted contact removed.' : 'Trusted contact saved.');
    } catch (error) { setNotice(error instanceof Error ? error.message : 'Could not save contact.'); }
    finally { setBusy(false); }
  }

  return (
    <Card>
      <Text style={styles.heading}>Trusted contact</Text>
      <Text style={styles.body}>If you do not tap Acknowledge on a fire alarm or siren alert, Aware will text this person after your chosen wait. Demo alerts never send SMS.</Text>
      <Text style={styles.label}>Your name</Text>
      <TextInput accessibilityLabel="Your name for contact alerts" value={draft.userName} onChangeText={(userName) => setDraft({ ...draft, userName })} placeholder="Name your contact will recognize" placeholderTextColor={colors.muted} maxLength={80} style={styles.input} editable={!busy} />
      <Text style={styles.label}>Contact name</Text>
      <TextInput accessibilityLabel="Trusted contact name" value={draft.name} onChangeText={(name) => setDraft({ ...draft, name })} placeholder="Contact name" placeholderTextColor={colors.muted} maxLength={80} style={styles.input} editable={!busy} />
      <Text style={styles.label}>Mobile number, including country code</Text>
      <TextInput accessibilityLabel="Trusted contact phone number" value={draft.phone} onChangeText={(phone) => setDraft({ ...draft, phone })} placeholder="+18145551234" placeholderTextColor={colors.muted} keyboardType="phone-pad" autoComplete="tel" maxLength={25} style={styles.input} editable={!busy} />
      <View style={styles.row}>
        <Text style={styles.body}>This person has agreed to receive automated SMS alerts.</Text>
        <Switch accessibilityLabel="Contact has agreed to receive SMS alerts" value={draft.consent} disabled={busy} onValueChange={(consent) => setDraft({ ...draft, consent })} />
      </View>
      <Text style={styles.label}>Wait before texting</Text>
      <View style={styles.row}>
        {([30, 60, 120] as const).map((waitSeconds) => <PrimaryButton key={waitSeconds} label={`${waitSeconds}s`} variant={draft.waitSeconds === waitSeconds ? 'primary' : 'secondary'} disabled={busy} onPress={() => setDraft({ ...draft, waitSeconds })} style={styles.option} />)}
      </View>
      <View style={styles.row}>
        <Text style={styles.body}>Enable trusted-contact alerts</Text>
        <Switch accessibilityLabel="Enable trusted-contact alerts" value={draft.enabled} disabled={busy} onValueChange={(enabled) => setDraft({ ...draft, enabled })} />
      </View>
      <PrimaryButton label="Save contact" loading={busy} disabled={busy} onPress={() => void save()} />
      {trustedContact.phone ? <PrimaryButton label="Remove contact" variant="danger" disabled={busy} onPress={() => Alert.alert('Remove trusted contact?', 'This disables future contact alerts and cancels a pending SMS if it has not started sending.', [{ text: 'Keep contact', style: 'cancel' }, { text: 'Remove', style: 'destructive', onPress: () => void save(true) }])} /> : null}
      {notice ? <Text accessibilityRole="alert" style={styles.body}>{notice}</Text> : null}
      <Text accessibilityLiveRegion="polite" style={styles.body}>{escalationMessage(escalation)}</Text>
      {escalation && ['pending', 'registering', 'connection_error', 'cancel_retry'].includes(escalation.status) ? <PrimaryButton label="Cancel pending contact alert" variant="secondary" onPress={() => void cancelContactAlert().catch(() => setNotice('Could not cancel. Check your connection and try again.'))} /> : null}
      <Text style={styles.body}>SMS requires a connection to schedule or cancel. Once sending starts, a message cannot be recalled. Your contact’s number and your name are temporarily sent to the server; the SMS provider processes the message.</Text>
    </Card>
  );
}

const styles = StyleSheet.create({
  heading: { color: colors.text, fontSize: type.heading, fontWeight: '900' },
  label: { color: colors.text, fontSize: type.label, fontWeight: '700' },
  body: { color: colors.muted, fontSize: type.small, lineHeight: 21, flexShrink: 1 },
  input: { color: colors.text, backgroundColor: colors.background, borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, padding: spacing.md, minHeight: 50, fontSize: type.body },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  option: { flex: 1 },
});
