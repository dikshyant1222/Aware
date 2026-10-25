import type { ComponentProps } from 'react';
import { MaterialCommunityIcons } from '@expo/vector-icons';

export type SoundId =
  | 'fire_alarm'
  | 'siren'
  | 'doorbell'
  | 'baby_cry'
  | 'vehicle_horn'
  | 'custom';

export type Urgency = 'critical' | 'high' | 'attention';
export type Sensitivity = 'low' | 'medium' | 'high';
export type Confidence = 'likely' | 'possible';
export type Environment = 'quiet' | 'traffic' | 'crowd' | 'tv_music' | 'workplace' | 'mixed';

type IconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

export type SoundDefinition = {
  id: SoundId;
  label: string;
  urgency: Urgency;
  critical: boolean;
  icon: IconName;
  vibration: number[];
};

export const SOUNDS: readonly SoundDefinition[] = [
  { id: 'fire_alarm', label: 'Fire / smoke alarm', urgency: 'critical', critical: true, icon: 'fire-alert', vibration: [0, 500, 150, 500, 150, 500, 600] },
  { id: 'siren', label: 'Emergency siren', urgency: 'critical', critical: true, icon: 'alarm-light', vibration: [0, 150, 80, 250, 80, 400, 80, 600, 500] },
  { id: 'doorbell', label: 'Doorbell / knock', urgency: 'attention', critical: false, icon: 'doorbell', vibration: [0, 120, 120, 450] },
  { id: 'baby_cry', label: 'Baby crying', urgency: 'high', critical: false, icon: 'baby-face-outline', vibration: [0, 300, 200, 500, 200, 300] },
  { id: 'vehicle_horn', label: 'Vehicle horn', urgency: 'high', critical: false, icon: 'car-brake-alert', vibration: [0, 400, 150, 400] },
  { id: 'custom', label: 'Custom sound', urgency: 'attention', critical: false, icon: 'ear-hearing', vibration: [0, 90, 90, 90, 90, 500] },
] as const;

export const SOUND_BY_ID = Object.fromEntries(SOUNDS.map((sound) => [sound.id, sound])) as Record<SoundId, SoundDefinition>;
export const SOUND_IDS = SOUNDS.map((sound) => sound.id);

export const ENVIRONMENT_LABELS: Record<Environment, string> = {
  quiet: 'Quiet',
  traffic: 'Traffic',
  crowd: 'Crowd / public',
  tv_music: 'TV / music',
  workplace: 'Workplace / machinery',
  mixed: 'Mixed',
};

export const SENSITIVITY_LABELS: Record<Sensitivity, string> = {
  low: 'Low',
  medium: 'Medium',
  high: 'High',
};
