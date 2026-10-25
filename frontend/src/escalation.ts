import AsyncStorage from '@react-native-async-storage/async-storage';
import { BACKEND_URL } from '@/api';

export type TrustedContact = {
  enabled: boolean;
  name: string;
  phone: string;
  userName: string;
  consent: boolean;
  waitSeconds: 30 | 60 | 120;
};
export const DEFAULT_CONTACT: TrustedContact = { enabled: false, name: '', phone: '', userName: '', consent: false, waitSeconds: 30 };
export type EscalationState = { id: string; status: string; due_at: number | null; cancelRequested?: boolean; contactName?: string; registration?: { class_id: 'fire_alarm' | 'siren'; contact: TrustedContact } };
const CONTACT_KEY = '@aware/trusted-contact';
const PENDING_KEY = '@aware/escalation';

export function validateContact(contact: TrustedContact): string | null {
  if (!contact.name.trim()) return 'Enter your contact’s name.';
  if (!contact.userName.trim()) return 'Enter your name so your contact knows who needs help.';
  if (!/^\+[1-9]\d{7,14}$/.test(contact.phone)) return 'Use an international phone number, such as +18145551234.';
  if (!contact.consent) return 'Confirm that this contact has agreed to receive SMS alerts.';
  if (![30, 60, 120].includes(contact.waitSeconds)) return 'Choose a valid wait time.';
  return null;
}

export async function escalationRequest<T>(path: string, method = 'GET', body?: object): Promise<T> {
  const key = process.env.EXPO_PUBLIC_ESCALATION_API_KEY?.trim();
  if (!BACKEND_URL || !key) throw new Error('Trusted-contact SMS needs a backend address and access key.');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {
    const response = await fetch(`${BACKEND_URL}/api/escalations${path}`, {
      method, headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined, signal: controller.signal,
    });
    if (!response.ok) {
      if (response.status === 503) throw new Error('SMS is not configured on the server.');
      if (response.status === 401) throw new Error('The trusted-contact access key is incorrect.');
      throw new Error('Could not update the trusted-contact alert.');
    }
    return await response.json() as T;
  } catch (error) {
    if (error instanceof Error && error.name !== 'AbortError' && error.message !== 'Network request failed') throw error;
    throw new Error('No connection: contact alert status could not be confirmed.');
  } finally { clearTimeout(timeout); }
}

export class EscalationManager {
  state: EscalationState | null = null;
  private registration: { class_id: 'fire_alarm' | 'siren'; contact: TrustedContact } | null = null;
  private busy = false;
  private writes: Promise<void> = Promise.resolve();
  constructor(private publish: (state: EscalationState | null) => void) {}

  async restore() {
    const stored = await AsyncStorage.getItem(PENDING_KEY);
    this.state = stored ? JSON.parse(stored) as EscalationState : null;
    this.registration = this.state?.registration || null;
    this.publish(this.state);
    await this.refresh();
  }

  private async persist(state: EscalationState) {
    this.state = state;
    this.publish(state);
    const value = JSON.stringify(state);
    this.writes = this.writes.catch(() => undefined).then(() => AsyncStorage.setItem(PENDING_KEY, value));
    await this.writes;
  }

  async arm(classId: 'fire_alarm' | 'siren', contact: TrustedContact, demo: boolean) {
    if (demo || !contact.enabled || validateContact(contact)) return;
    // One escalation per incident, even if the sound keeps repeating.
    if (this.state && this.state.status !== 'cancelled' && !(this.state.cancelRequested && ['failed', 'undelivered', 'delivered', 'sent', 'unknown'].includes(this.state.status))) return;
    const id = `${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
    this.registration = { class_id: classId, contact: { ...contact } };
    await this.persist({ id, status: 'registering', due_at: null, contactName: contact.name, registration: this.registration });
    await this.refresh();
  }

  async cancel() {
    if (!this.state) return;
    await this.persist({ ...this.state, cancelRequested: true });
    // May race with registration; the backend records a cancellation tombstone.
    await this.refresh(true);
  }

  async refresh(force = false) {
    if (!this.state || (this.busy && !force)) return;
    this.busy = true;
    const before = this.state;
    try {
      let result: EscalationState;
      if (before.cancelRequested) {
        result = await escalationRequest(`/${before.id}/cancel`, 'POST');
      } else if (before.status === 'registering' && this.registration) {
        const { class_id, contact } = this.registration;
        result = await escalationRequest(`/${before.id}`, 'PUT', { class_id, phone: contact.phone, name: contact.userName, consent: true, wait_seconds: contact.waitSeconds });
      } else {
        result = await escalationRequest(`/${before.id}`);
      }
      // An acknowledgement that raced this request must remain a cancellation.
      if (this.state?.id !== before.id) return;
      if (this.state.status === 'cancelled' && result.status !== 'cancelled') return;
      const cancelled = this.state.cancelRequested;
      await this.persist({ ...result, contactName: before.contactName, cancelRequested: cancelled });
      if (cancelled && result.status === 'cancelled') this.registration = null;
    } catch {
      if (this.state?.id === before.id && this.state.status !== 'cancelled') this.publish({ ...this.state, status: this.state.cancelRequested ? 'cancel_retry' : 'connection_error' });
    } finally { this.busy = false; }
  }
}

export async function loadContact(): Promise<TrustedContact> {
  const raw = await AsyncStorage.getItem(CONTACT_KEY);
  return raw ? { ...DEFAULT_CONTACT, ...JSON.parse(raw) } : { ...DEFAULT_CONTACT };
}
export async function storeContact(contact: TrustedContact) {
  await AsyncStorage.setItem(CONTACT_KEY, JSON.stringify(contact));
}

export function escalationMessage(state: EscalationState | null): string {
  if (!state) return 'No contact alert pending.';
  if (state.status === 'cancelled') return 'Contact alert cancelled.';
  if (state.status === 'cancel_retry' || (state.cancelRequested && ['pending', 'registering'].includes(state.status))) return 'Cancellation not confirmed. Reconnecting; the SMS may still be sent.';
  if (state.status === 'connection_error') return 'Connection lost. Contact alert status is unconfirmed.';
  if (state.status === 'registering') return 'Setting up the contact alert…';
  if (state.status === 'pending') return `Will text ${state.contactName || 'your contact'} at ${new Date((state.due_at || 0) * 1000).toLocaleTimeString()}. Tap Acknowledge to cancel.`;
  if (state.status === 'delivered') return 'Contact SMS delivered.';
  if (['failed', 'undelivered'].includes(state.status)) return 'Contact SMS failed. Please contact them another way.';
  if (state.status === 'unknown') return 'SMS delivery is unknown. Please contact them another way.';
  if (state.status === 'sent') return 'Contact SMS sent; delivery not yet confirmed.';
  return 'Contact SMS is being sent. It can no longer be cancelled.';
}
