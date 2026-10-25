import { acknowledgeAlert, CONFIRMATION_WINDOW_MS, COOLDOWN_MS, enqueueAlert, evaluateDetection } from '../alertLogic';

describe('confirmation and cooldown', () => {
  it('requires two nearby possible noncritical segments', () => {
    const first = evaluateDetection(
      { classId: 'doorbell', shouldAlertNow: false, needsConfirmation: true, now: 1_000 },
      { confirmations: {}, cooldowns: {} },
    );
    expect(first.alert).toBe(false);
    expect(first.reason).toBe('awaiting_confirmation');

    const second = evaluateDetection(
      { classId: 'doorbell', shouldAlertNow: false, needsConfirmation: true, now: 1_000 + CONFIRMATION_WINDOW_MS },
      first.memory,
    );
    expect(second.alert).toBe(true);
    expect(second.reason).toBe('confirmed');
  });

  it('expires stale confirmation evidence', () => {
    const first = evaluateDetection(
      { classId: 'baby_cry', shouldAlertNow: false, needsConfirmation: true, now: 0 },
      { confirmations: {}, cooldowns: {} },
    );
    const late = evaluateDetection(
      { classId: 'baby_cry', shouldAlertNow: false, needsConfirmation: true, now: CONFIRMATION_WINDOW_MS + 1 },
      first.memory,
    );
    expect(late.alert).toBe(false);
    expect(late.memory.confirmations.baby_cry).toBe(CONFIRMATION_WINDOW_MS + 1);
  });

  it('allows critical/immediate alerts but applies an eight-second per-class cooldown', () => {
    const immediate = evaluateDetection(
      { classId: 'siren', shouldAlertNow: true, needsConfirmation: false, now: 10_000 },
      { confirmations: {}, cooldowns: {} },
    );
    expect(immediate.alert).toBe(true);
    const blocked = evaluateDetection(
      { classId: 'siren', shouldAlertNow: true, needsConfirmation: false, now: 10_000 + COOLDOWN_MS - 1 },
      immediate.memory,
    );
    expect(blocked.reason).toBe('cooldown');
    const allowed = evaluateDetection(
      { classId: 'siren', shouldAlertNow: true, needsConfirmation: false, now: 10_000 + COOLDOWN_MS },
      immediate.memory,
    );
    expect(allowed.alert).toBe(true);
  });
});

describe('alert priority queue', () => {
  const alert = (urgency: 'attention' | 'high' | 'critical', receivedAt: number) => ({ urgency, receivedAt });

  it('never replaces a critical alert with lower priority', () => {
    const state = enqueueAlert({ current: alert('critical', 1), queued: [] }, alert('attention', 2));
    expect(state.current?.urgency).toBe('critical');
    expect(state.queued).toHaveLength(1);
  });

  it('promotes a critical alert and returns the highest priority queued alert next', () => {
    const promoted = enqueueAlert({ current: alert('attention', 1), queued: [alert('high', 0)] }, alert('critical', 2));
    expect(promoted.current?.urgency).toBe('critical');
    const next = acknowledgeAlert(promoted);
    expect(next.current?.urgency).toBe('high');
  });
});
