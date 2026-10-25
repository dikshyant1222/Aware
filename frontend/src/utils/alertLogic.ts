import type { SoundId, Urgency } from '@/sounds';

export const CONFIRMATION_WINDOW_MS = 6_000;
export const COOLDOWN_MS = 8_000;

export type DetectionDecisionInput = {
  classId: SoundId;
  shouldAlertNow: boolean;
  needsConfirmation: boolean;
  now: number;
};

export type DetectionMemory = {
  confirmations: Partial<Record<SoundId, number>>;
  cooldowns: Partial<Record<SoundId, number>>;
};

export type DetectionDecision = {
  alert: boolean;
  memory: DetectionMemory;
  reason: 'immediate' | 'confirmed' | 'awaiting_confirmation' | 'cooldown' | 'no_alert';
};

export function evaluateDetection(input: DetectionDecisionInput, memory: DetectionMemory): DetectionDecision {
  const confirmations = { ...memory.confirmations };
  const cooldowns = { ...memory.cooldowns };
  const lastAlert = cooldowns[input.classId];

  if (lastAlert !== undefined && input.now - lastAlert < COOLDOWN_MS) {
    return { alert: false, memory: { confirmations, cooldowns }, reason: 'cooldown' };
  }

  if (input.shouldAlertNow) {
    cooldowns[input.classId] = input.now;
    delete confirmations[input.classId];
    return { alert: true, memory: { confirmations, cooldowns }, reason: 'immediate' };
  }

  if (!input.needsConfirmation) {
    delete confirmations[input.classId];
    return { alert: false, memory: { confirmations, cooldowns }, reason: 'no_alert' };
  }

  const firstSeen = confirmations[input.classId];
  if (firstSeen !== undefined && input.now - firstSeen <= CONFIRMATION_WINDOW_MS) {
    cooldowns[input.classId] = input.now;
    delete confirmations[input.classId];
    return { alert: true, memory: { confirmations, cooldowns }, reason: 'confirmed' };
  }

  confirmations[input.classId] = input.now;
  return { alert: false, memory: { confirmations, cooldowns }, reason: 'awaiting_confirmation' };
}

export type PriorityAlert = { urgency: Urgency; receivedAt: number };
export type AlertQueueState<T extends PriorityAlert> = { current: T | null; queued: T[] };

const PRIORITY: Record<Urgency, number> = { attention: 1, high: 2, critical: 3 };

export function enqueueAlert<T extends PriorityAlert>(state: AlertQueueState<T>, incoming: T): AlertQueueState<T> {
  if (!state.current) return { current: incoming, queued: state.queued };

  if (PRIORITY[incoming.urgency] > PRIORITY[state.current.urgency]) {
    return { current: incoming, queued: [...state.queued, state.current] };
  }

  return { current: state.current, queued: [...state.queued, incoming] };
}

export function acknowledgeAlert<T extends PriorityAlert>(state: AlertQueueState<T>): AlertQueueState<T> {
  if (state.queued.length === 0) return { current: null, queued: [] };
  let nextIndex = 0;
  for (let index = 1; index < state.queued.length; index += 1) {
    if (PRIORITY[state.queued[index].urgency] > PRIORITY[state.queued[nextIndex].urgency]) nextIndex = index;
  }
  const next = state.queued[nextIndex];
  return { current: next, queued: state.queued.filter((_, index) => index !== nextIndex) };
}
