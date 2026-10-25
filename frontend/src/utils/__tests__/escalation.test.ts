import AsyncStorage from '@react-native-async-storage/async-storage';
import { DEFAULT_CONTACT, EscalationManager, escalationMessage, loadContact, storeContact, validateContact } from '@/escalation';

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'));
jest.mock('@/api', () => ({ BACKEND_URL: 'https://aware.example' }));

const contact = { ...DEFAULT_CONTACT, name: 'Alex', userName: 'Sam', phone: '+18145551234', consent: true, enabled: true };
const fetchMock = jest.fn();
const originalFetch = global.fetch;
beforeEach(async () => {
  await AsyncStorage.clear();
  fetchMock.mockReset();
  global.fetch = fetchMock;
  process.env.EXPO_PUBLIC_ESCALATION_API_KEY = 'test-access';
});
afterAll(() => { global.fetch = originalFetch; delete process.env.EXPO_PUBLIC_ESCALATION_API_KEY; });

function response(status: string, id = 'test-id') {
  return { ok: true, json: async () => ({ id, status, due_at: Date.now() / 1000 + 30 }) };
}

test('contact is persisted and requires a name, international number and agreement', async () => {
  expect(validateContact(contact)).toBeNull();
  expect(validateContact({ ...contact, phone: '5551234' })).toBeTruthy();
  expect(validateContact({ ...contact, consent: false })).toBeTruthy();
  expect(validateContact({ ...contact, userName: '' })).toBeTruthy();
  await storeContact(contact);
  expect(await loadContact()).toEqual(contact);
});

test('demo and disabled-contact alerts never schedule an SMS', async () => {
  const manager = new EscalationManager(jest.fn());
  await manager.arm('fire_alarm', contact, true);
  await manager.arm('siren', { ...contact, enabled: false }, false);
  expect(fetchMock).not.toHaveBeenCalled();
});

test('repeating critical sounds share one pending incident', async () => {
  fetchMock.mockImplementation(async (url: string) => response('pending', url.split('/').pop()));
  const manager = new EscalationManager(jest.fn());
  await manager.arm('fire_alarm', contact, false);
  await manager.arm('fire_alarm', contact, false);
  await manager.arm('siren', contact, false);
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ class_id: 'fire_alarm', wait_seconds: 30, phone: contact.phone, name: 'Sam' });
});

test('acknowledgement cancels even while registration is in flight', async () => {
  let finish!: (value: unknown) => void;
  let registered!: () => void;
  const started = new Promise<void>((resolve) => { registered = resolve; });
  fetchMock.mockImplementation((url: string, init: RequestInit) => {
    const id = url.split('/')[5];
    if (init.method === 'PUT') {
      registered();
      return new Promise((resolve) => { finish = resolve; });
    }
    return Promise.resolve(response('cancelled', id));
  });
  const manager = new EscalationManager(jest.fn());
  const arming = manager.arm('fire_alarm', contact, false);
  await started;
  const id = manager.state!.id;
  await manager.cancel();
  finish(response('pending', id));
  await arming;
  expect(manager.state?.status).toBe('cancelled');
  expect(JSON.parse((await AsyncStorage.getItem('@aware/escalation'))!).status).toBe('cancelled');
});

test('failed cancellation persists intent and retries after app restart', async () => {
  fetchMock.mockImplementation(async (url: string) => response('pending', url.split('/').pop()));
  const first = new EscalationManager(jest.fn());
  await first.arm('siren', contact, false);
  const id = first.state!.id;
  fetchMock.mockRejectedValue(new Error('Network request failed'));
  const publish = jest.fn();
  await first.cancel();
  const second = new EscalationManager(publish);
  fetchMock.mockResolvedValue(response('cancelled', id));
  await second.restore();
  expect(fetchMock.mock.calls.at(-1)?.[1].method).toBe('POST');
  expect(second.state?.status).toBe('cancelled');
});

test('provider acceptance is not presented as delivery', () => {
  expect(escalationMessage({ id: 'id', status: 'queued', due_at: null })).not.toMatch(/delivered/);
  expect(escalationMessage({ id: 'id', status: 'delivered', due_at: null })).toMatch(/delivered/);
});
