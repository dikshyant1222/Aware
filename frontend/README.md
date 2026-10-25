# Aware frontend

Android-focused Expo / React Native client for Aware sound awareness.

## Run

1. Copy `.env.example` to `.env` and set `EXPO_PUBLIC_BACKEND_URL`. Android Emulator reaches a backend on the Windows host at `http://10.0.2.2:8000`; a physical device needs the computer's LAN address.
2. Install dependencies with `npm install`.
3. Create and run the Android development build with `npx expo run:android`.

Background microphone recording and notification channels require a development build; Expo Go is not sufficient.

## Phone storage and service requests

`AwareContext` hydrates settings, events and the custom sound from AsyncStorage. Events are a JSON array under `aware.events`, maximum 500, ordered newest `last_detected_at` first. IDs are generated on the device. `aware.customSound` stores `{name, created_at, fingerprint:number[]}`. There is no database server and no history or custom-sound fetch on startup.

Alert delivery starts before history work. The app finds the most recent same-class row within 120 seconds and asks the stateless `/api/events/group` endpoint with text fields only. A 3-second timeout/failure inserts a new row. A confirmed reply merges locally, preserving first time/ID, incrementing repeats, retaining highest score/strongest confidence, and recording Jev metadata. History writes are serialized; clear-all in Events or Settings uses confirmation and invalidates pending older grouping results.

Teach step 1 keeps its returned fingerprint in screen memory. Step 2 sends `first_fingerprint`, receives the normalized average, and saves the name/date/fingerprint on the phone. Listening sends `custom_fingerprint` and `custom_name` with each analysis request. Replacing/removing the sound affects this phone only. Temporary recordings are deleted after processing; no audio is saved in AsyncStorage or retained by the service.

History and custom sound management work locally without a backend connection. Analysis and grouping still need the service. Data is local to each app installation; clearing app data removes it. Old server history/custom sounds are not imported automatically; teach the sound again on this phone.

## Checks

```text
npx tsc --noEmit
npx expo lint
npm test
```

The Jest suite covers alert policy, local grouping/merge boundaries and failures, sorting/cap, and AsyncStorage reload/clear behavior.

On Windows with PowerShell script restrictions, use `npx.cmd` and `npm.cmd`.

Aware requires network access for sound analysis. Android device policies may stop background work, and the app is not a certified safety device.
# Trusted-contact alerts

Settings includes one saved trusted contact, recipient agreement, your name, an enable switch, and 30/60/120-second waits. Real unacknowledged critical alerts schedule backend SMS; Acknowledge cancels before sending. Demo alerts never send SMS. Listen and Settings show delivery/cancellation state, including after app reopening. Configure `EXPO_PUBLIC_ESCALATION_API_KEY` to match the backend private-installation key; Twilio credentials belong only on the backend. See the root [README](../README.md#trusted-contact-alerts) for setup and limitations.
