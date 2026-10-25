import * as Notifications from 'expo-notifications';
import { AndroidImportance, AndroidNotificationVisibility } from 'expo-notifications';
import { Platform, Vibration } from 'react-native';

import type { Detection } from '@/api';
import { colors } from '@/theme';
import { SOUNDS, SOUND_BY_ID } from '@/sounds';

Notifications.setNotificationHandler(null);

const channelId = (classId: string) => `aware-${classId}`;

export async function configureNotificationChannels(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Promise.all(
    SOUNDS.map((sound) =>
      Notifications.setNotificationChannelAsync(channelId(sound.id), {
        name: sound.label,
        description: `Aware alerts for ${sound.label}`,
        importance: AndroidImportance.MAX,
        vibrationPattern: sound.vibration,
        enableVibrate: true,
        enableLights: true,
        lightColor: sound.critical ? colors.critical : sound.urgency === 'high' ? colors.high : colors.primary,
        lockscreenVisibility: AndroidNotificationVisibility.PUBLIC,
        sound: null,
      }),
    ),
  );
}

export async function requestNotificationPermission(): Promise<boolean> {
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;
  return (await Notifications.requestPermissionsAsync()).granted;
}

export async function getNotificationPermission(): Promise<boolean> {
  return (await Notifications.getPermissionsAsync()).granted;
}

export function vibrateInForeground(detection: Detection): void {
  const sound = SOUND_BY_ID[detection.class_id];
  Vibration.cancel();
  Vibration.vibrate(sound.vibration, detection.critical);
}

export function stopVibration(): void {
  Vibration.cancel();
}

export async function notifyInBackground(detection: Detection, details: string, demo: boolean): Promise<void> {
  await Notifications.scheduleNotificationAsync({
    content: {
      title: `${demo ? 'Demo · ' : ''}${detection.label}`,
      body: `${detection.confidence === 'likely' ? 'Likely' : 'Possible'} · ${details}`,
      data: { class_id: detection.class_id, demo },
      color: detection.critical ? colors.critical : detection.urgency === 'high' ? colors.high : colors.primary,
      sound: false,
    },
    trigger: Platform.OS === 'android' ? { channelId: channelId(detection.class_id) } : null,
  });
}
