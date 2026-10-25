import type { EventInput, EventRecord, GroupResult } from '@/api';

export const EVENT_LIMIT = 500;
export const GROUP_WINDOW_SECONDS = 120;

export function sortEvents(events: EventRecord[]): EventRecord[] {
  return [...events].sort((a, b) => Date.parse(b.last_detected_at) - Date.parse(a.last_detected_at)).slice(0, EVENT_LIMIT);
}

export function groupingCandidate(events: EventRecord[], incoming: EventInput): EventRecord | undefined {
  return sortEvents(events).find((event) => {
    const seconds = (Date.parse(incoming.detected_at) - Date.parse(event.last_detected_at)) / 1_000;
    return event.class_id === incoming.class_id && seconds >= 0 && seconds <= GROUP_WINDOW_SECONDS;
  });
}

// Alert delivery happens before this asynchronous history-only operation.
export async function addToHistory(
  events: EventRecord[],
  incoming: EventInput,
  id: string,
  askJev: (incoming: EventInput, previous: EventRecord, secondsApart: number) => Promise<GroupResult>,
): Promise<EventRecord[]> {
  const previous = groupingCandidate(events, incoming);
  let decision: GroupResult = { probability: null, same_occurrence: false };
  if (previous) {
    const seconds = (Date.parse(incoming.detected_at) - Date.parse(previous.last_detected_at)) / 1_000;
    try { decision = await askJev(incoming, previous, seconds); } catch { /* Keep a separate row on failure. */ }
  }
  if (previous && decision.same_occurrence && decision.probability !== null && Number.isFinite(decision.probability)) {
    const confidence = previous.best_confidence === 'likely' || incoming.confidence === 'likely' ? 'likely' : 'possible';
    const score = Math.max(previous.max_score, incoming.score);
    const merged: EventRecord = {
      ...previous,
      label: incoming.label,
      urgency: incoming.urgency,
      details: incoming.details,
      muted: incoming.muted,
      demo: previous.demo && incoming.demo,
      last_detected_at: incoming.detected_at,
      repeat_count: previous.repeat_count + 1,
      max_score: score,
      score,
      confidence,
      best_confidence: confidence,
      grouped: true,
      jev_grouped: true,
      jev_probability: decision.probability,
    };
    return sortEvents(events.map((event) => event.id === previous.id ? merged : event));
  }
  return sortEvents([{
    ...incoming,
    id,
    first_detected_at: incoming.detected_at,
    last_detected_at: incoming.detected_at,
    repeat_count: 1,
    max_score: incoming.score,
    best_confidence: incoming.confidence,
    grouped: false,
    jev_grouped: false,
    jev_probability: null,
  }, ...events]);
}
