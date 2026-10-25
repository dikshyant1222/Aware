import AsyncStorage from '@react-native-async-storage/async-storage';

import type { CustomSound, EventRecord } from '@/api';
import { sortEvents } from '@/utils/eventHistory';

export const EVENTS_KEY = 'aware.events';
export const CUSTOM_SOUND_KEY = 'aware.customSound';

export async function loadEvents(): Promise<EventRecord[]> {
  const value = await AsyncStorage.getItem(EVENTS_KEY);
  return value ? sortEvents(JSON.parse(value) as EventRecord[]) : [];
}

export async function saveEvents(events: EventRecord[]): Promise<void> {
  await AsyncStorage.setItem(EVENTS_KEY, JSON.stringify(sortEvents(events)));
}

export async function loadCustomSound(): Promise<CustomSound | null> {
  const value = await AsyncStorage.getItem(CUSTOM_SOUND_KEY);
  return value ? JSON.parse(value) as CustomSound : null;
}

export async function saveCustomSound(sound: CustomSound | null): Promise<void> {
  if (sound) await AsyncStorage.setItem(CUSTOM_SOUND_KEY, JSON.stringify(sound));
  else await AsyncStorage.removeItem(CUSTOM_SOUND_KEY);
}
