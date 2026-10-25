import AsyncStorage from '@react-native-async-storage/async-storage';

import { CUSTOM_SOUND_KEY, EVENTS_KEY, loadCustomSound, loadEvents, saveCustomSound, saveEvents } from '@/storage';
import { addToHistory } from '@/utils/eventHistory';

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'));

beforeEach(async () => { await AsyncStorage.clear(); });

test('events survive a reload and clear-all is persisted as an empty JSON array', async () => {
  const events = await addToHistory([], {
    class_id: 'custom', label: 'Timer', urgency: 'attention', confidence: 'likely',
    score: 0.9, details: '3/4 frames', muted: false, demo: false, detected_at: new Date().toISOString(),
  }, 'phone-id', async () => ({ probability: null, same_occurrence: false }));
  await saveEvents(events);
  expect(JSON.parse((await AsyncStorage.getItem(EVENTS_KEY))!)).toEqual(events);
  expect(await loadEvents()).toEqual(events);
  await saveEvents([]);
  expect(await loadEvents()).toEqual([]);
  expect(await AsyncStorage.getItem(EVENTS_KEY)).toBe('[]');
});

test('custom name, date and fingerprint survive reload; removal only clears custom sound', async () => {
  const sound = { name: 'Timer', created_at: new Date().toISOString(), fingerprint: [1, 0, -1] };
  expect(await loadCustomSound()).toBeNull();
  await saveEvents([]);
  await saveCustomSound(sound);
  expect(await loadCustomSound()).toEqual(sound);
  await saveCustomSound(null);
  expect(await loadCustomSound()).toBeNull();
  expect(await AsyncStorage.getItem(CUSTOM_SOUND_KEY)).toBeNull();
  expect(await AsyncStorage.getItem(EVENTS_KEY)).toBe('[]');
});
