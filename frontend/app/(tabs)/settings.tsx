import { MaterialCommunityIcons } from '@expo/vector-icons';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, Pressable, StyleSheet, Switch, Text, Vibration, View } from 'react-native';

import { notificationPermissionStatus, useAware } from '@/aware/AwareContext';
import { Card } from '@/components/Card';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { TrustedContactCard } from '@/components/TrustedContactCard';
import { requestNotificationPermission } from '@/notify';
import { SENSITIVITY_LABELS, SOUNDS, type Sensitivity } from '@/sounds';
import { colors, radius, spacing, type } from '@/theme';

const SENSITIVITIES: Sensitivity[] = ['low', 'medium', 'high'];

export default function SettingsScreen() {
  const aware = useAware();
  const [notificationGranted, setNotificationGranted] = useState(false);

  useFocusEffect(useCallback(() => {
    void notificationPermissionStatus().then(setNotificationGranted);
  }, []));

  const confirmClear = () => Alert.alert('Clear event history?', 'All text summaries will be removed.', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Clear all', style: 'destructive', onPress: () => void aware.clearEvents().catch(() => Alert.alert('Could not clear history', 'Please try again.')) },
  ]);

  const confirmDeleteCustom = () => Alert.alert('Remove custom sound?', 'You can teach Aware a new sound later.', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Remove', style: 'destructive', onPress: () => void aware.saveTaughtSound(null).catch(() => Alert.alert('Could not remove custom sound', 'Please try again.')) },
  ]);

  return (
    <Screen>
      <Text style={styles.title}>Settings</Text>

      <Card>
        <SettingSwitch label="Auto-listen" detail="Start listening after launch" value={aware.settings.autoListen} onValueChange={aware.setAutoListen} />
        <View style={styles.divider} />
        <View style={styles.row}>
          <MaterialCommunityIcons name="shield-check-outline" size={28} color={colors.primary} />
          <View style={styles.copy}>
            <Text style={styles.label}>Android background protection</Text>
            <Text style={styles.detail}>{aware.isListening ? 'Active · microphone foreground service' : 'Ready · active while listening'}</Text>
          </View>
        </View>
      </Card>

      <TrustedContactCard />
      <Text style={styles.section}>Sounds</Text>
      {SOUNDS.map((sound) => (
        <Card key={sound.id}>
          <View style={styles.row}>
            <MaterialCommunityIcons name={sound.icon} size={30} color={sound.critical ? colors.critical : sound.urgency === 'high' ? colors.high : colors.text} />
            <View style={styles.copy}>
              <Text style={styles.label}>{sound.id === 'custom' && aware.taughtSound ? aware.taughtSound.name : sound.label}</Text>
              <Text style={styles.detail}>{sound.urgency} priority</Text>
            </View>
            <Switch accessibilityLabel={`Enable ${sound.label}`} value={aware.settings.enabled[sound.id]} onValueChange={(value) => aware.setSoundEnabled(sound.id, value)} trackColor={{ false: colors.disabled, true: colors.primaryDark }} thumbColor={aware.settings.enabled[sound.id] ? colors.primary : colors.muted} />
          </View>
          <Text style={styles.subLabel}>Sensitivity</Text>
          <View style={styles.segmented}>
            {SENSITIVITIES.map((sensitivity) => {
              const selected = aware.settings.sensitivity[sound.id] === sensitivity;
              return (
                <Pressable key={sensitivity} accessibilityRole="button" accessibilityState={{ selected }} accessibilityLabel={`${sound.label} sensitivity ${SENSITIVITY_LABELS[sensitivity]}`} onPress={() => aware.setSensitivity(sound.id, sensitivity)} style={[styles.segment, selected && styles.segmentSelected]}>
                  <Text style={[styles.segmentText, selected && styles.segmentTextSelected]}>{SENSITIVITY_LABELS[sensitivity]}</Text>
                </Pressable>
              );
            })}
          </View>
          <PrimaryButton label="Preview vibration" accessibilityLabel={`Preview ${sound.label} vibration`} icon="vibrate" variant="secondary" onPress={() => { Vibration.cancel(); Vibration.vibrate(sound.vibration, false); }} />
        </Card>
      ))}

      <Text style={styles.section}>Custom sound</Text>
      <Card>
        <Text style={styles.label}>{aware.taughtSound ? `Taught: ${aware.taughtSound.name}` : 'No custom sound taught yet'}</Text>
        <PrimaryButton label={aware.taughtSound ? 'Replace custom sound' : 'Teach a sound'} icon="ear-hearing" onPress={() => router.push('/teach')} />
        {aware.taughtSound ? <PrimaryButton label="Remove custom sound" variant="danger" onPress={confirmDeleteCustom} /> : null}
      </Card>

      <Text style={styles.section}>Permissions & intelligence</Text>
      <Card>
        <SettingSwitch label="AI label verification" detail="Optional Groq second opinion for ambiguous noncritical labels. Audio is never sent." value={aware.settings.aiVerify} onValueChange={aware.setAiVerify} />
        <View style={styles.divider} />
        <View style={styles.row}>
          <View style={styles.copy}>
            <Text style={styles.label}>Notification permission</Text>
            <Text style={styles.detail}>{notificationGranted ? 'Allowed' : 'Not allowed'}</Text>
          </View>
          {!notificationGranted ? <PrimaryButton label="Allow" onPress={() => void requestNotificationPermission().then(setNotificationGranted)} style={styles.inlineButton} /> : null}
        </View>
      </Card>

      <PrimaryButton label="Clear event history" icon="delete-outline" variant="danger" onPress={confirmClear} />
      <PrimaryButton label="Privacy information" icon="shield-lock-outline" variant="secondary" onPress={() => router.push('/privacy')} />
      <Text style={styles.disclaimer}>Aware depends on a network connection and is not a certified safety device.</Text>
    </Screen>
  );
}

function SettingSwitch({ label, detail, value, onValueChange }: { label: string; detail: string; value: boolean; onValueChange: (value: boolean) => void }) {
  return (
    <View style={styles.row}>
      <View style={styles.copy}><Text style={styles.label}>{label}</Text><Text style={styles.detail}>{detail}</Text></View>
      <Switch accessibilityLabel={label} value={value} onValueChange={onValueChange} trackColor={{ false: colors.disabled, true: colors.primaryDark }} thumbColor={value ? colors.primary : colors.muted} />
    </View>
  );
}

const styles = StyleSheet.create({
  title: { color: colors.text, fontSize: type.title, fontWeight: '900' },
  section: { color: colors.text, fontSize: type.heading, fontWeight: '900', marginTop: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  copy: { flex: 1 },
  label: { color: colors.text, fontSize: type.body, fontWeight: '800' },
  detail: { color: colors.muted, fontSize: type.small, lineHeight: 20, marginTop: 3, textTransform: 'capitalize' },
  subLabel: { color: colors.muted, fontSize: type.small, fontWeight: '700' },
  divider: { height: 1, backgroundColor: colors.border },
  segmented: { flexDirection: 'row', backgroundColor: colors.background, borderRadius: radius.sm, padding: 3, gap: 3 },
  segment: { flex: 1, minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm },
  segmentSelected: { backgroundColor: colors.primary },
  segmentText: { color: colors.muted, fontSize: type.label, fontWeight: '800' },
  segmentTextSelected: { color: colors.background },
  inlineButton: { minHeight: 46, paddingHorizontal: spacing.md },
  disclaimer: { color: colors.muted, fontSize: type.small, lineHeight: 20, textAlign: 'center' },
});
