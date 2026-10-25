import type { EventInput, EventRecord, GroupResult } from '@/api';
import { addToHistory, groupingCandidate, sortEvents } from '@/utils/eventHistory';

const at = (seconds: number) => new Date(Date.UTC(2026, 9, 24) + seconds * 1_000).toISOString();
const incoming = (seconds = 0, changes: Partial<EventInput> = {}): EventInput => ({
  class_id: 'doorbell', label: 'Doorbell', urgency: 'attention', confidence: 'possible',
  score: 0.4, details: '2/4 frames', muted: false, demo: true, detected_at: at(seconds), ...changes,
});
const noGroup = jest.fn<Promise<GroupResult>, [EventInput, EventRecord, number]>(async () => ({
  probability: null, same_occurrence: false,
}));
const row = async (seconds = 0, id = 'first', changes: Partial<EventInput> = {}) => (
  await addToHistory([], incoming(seconds, changes), id, noGroup)
)[0];

beforeEach(() => jest.clearAllMocks());

test('first event is stored locally with device id and full summary without calling Jev', async () => {
  const event = await row();
  expect(event).toMatchObject({
    id: 'first', first_detected_at: at(0), last_detected_at: at(0), detected_at: at(0),
    repeat_count: 1, max_score: 0.4, best_confidence: 'possible',
    grouped: false, jev_grouped: false, jev_probability: null,
  });
  expect(noGroup).not.toHaveBeenCalled();
});

test('uses the most recent same-class event within the inclusive 120 second window', async () => {
  const first = await row(0);
  const recent = await row(30, 'recent');
  const other = await row(40, 'other', { class_id: 'siren' });
  expect(groupingCandidate([first, other, recent], incoming(150))).toBe(recent);
  expect(groupingCandidate([first, recent], incoming(151))).toBeUndefined();
  expect(groupingCandidate([recent], incoming(29))).toBeUndefined();
});

test('a confirmed merge keeps id, first time and strongest evidence, and moves row to top', async () => {
  const first = await row(0, 'first', { confidence: 'likely', score: 0.9 });
  const other = await row(10, 'other', { class_id: 'siren' });
  const ask = jest.fn(async () => ({ probability: 0.82, same_occurrence: true }));
  const result = await addToHistory([other, first], incoming(20, { demo: false, muted: true, details: 'Latest context' }), 'unused', ask);
  expect(ask).toHaveBeenCalledWith(expect.objectContaining({ detected_at: at(20) }), first, 20);
  expect(result).toHaveLength(2);
  expect(result[0]).toMatchObject({
    id: 'first', first_detected_at: at(0), last_detected_at: at(20),
    repeat_count: 2, max_score: 0.9, score: 0.9, confidence: 'likely', best_confidence: 'likely',
    grouped: true, jev_grouped: true, jev_probability: 0.82, demo: false, muted: true, details: 'Latest context',
  });
  expect(first.repeat_count).toBe(1);
});

test('repeated merges increase count and upgrade confidence and max score', async () => {
  const ask = jest.fn(async () => ({ probability: 0.95, same_occurrence: true }));
  let events = [await row()];
  events = await addToHistory(events, incoming(20), 'unused', ask);
  events = await addToHistory(events, incoming(40, { confidence: 'likely', score: 0.8 }), 'unused', ask);
  expect(events[0]).toMatchObject({ repeat_count: 3, max_score: 0.8, best_confidence: 'likely', demo: true });
});

test.each([
  { probability: 0.8199, same_occurrence: false },
  { probability: null, same_occurrence: false },
  { probability: null, same_occurrence: true },
])('keeps separate rows without a confirmed probability: %j', async (decision) => {
  const first = await row();
  const result = await addToHistory([first], incoming(20), 'new', async () => decision);
  expect(result.map((event) => event.id)).toEqual(['new', 'first']);
  expect(result[0].jev_grouped).toBe(false);
});

test('offline or timed-out Jev still creates the event', async () => {
  const result = await addToHistory([await row()], incoming(20), 'offline', async () => { throw new Error('timeout'); });
  expect(result[0].id).toBe('offline');
  expect(result).toHaveLength(2);
});

test('never calls grouping for a different class or expired candidate', async () => {
  await addToHistory([await row()], incoming(121), 'late', noGroup);
  await addToHistory([await row()], incoming(10, { class_id: 'siren' }), 'different', noGroup);
  expect(noGroup).not.toHaveBeenCalled();
});

test('keeps newest 500 rows by last detection even when input is unsorted', async () => {
  const base = await row();
  const events = Array.from({ length: 500 }, (_, i) => ({ ...base, id: String(i), last_detected_at: at(i) }));
  const updated = await addToHistory(events.reverse(), incoming(600), 'latest', noGroup);
  expect(updated).toHaveLength(500);
  expect(updated[0].id).toBe('latest');
  expect(updated.at(-1)?.id).toBe('1');
  expect(sortEvents(events)[0].id).toBe('499');
});
