# Aware

Android accessibility app for Deaf and hard-of-hearing users. Aware listens for fire/smoke alarms, sirens, doorbells/knocks, baby crying, vehicle horns and one taught sound, and turns them into full-screen visual alerts, distinct vibration patterns, notifications and a grouped event history.

Settings, up to 500 event summaries, one custom sound fingerprint, and your trusted contact stay on the phone. Audio analysis and Jev/Groq requests are stateless; audio is not retained. Optional trusted-contact SMS temporarily stores an escalation on the backend in SQLite so its countdown survives app closure and server restarts.

## Trusted-contact alerts

In **Settings → Trusted contact**, enter your name, the contact's name and international mobile number, confirm their agreement to receive SMS, choose **30, 60, or 120 seconds**, enable alerts, and save. Real fire/smoke alarm and siren alerts schedule one SMS per unacknowledged incident. Repeated detections do not restart its deadline or schedule extra messages. Demo alerts never schedule SMS.

Tap **ACKNOWLEDGE** on a critical alert to cancel the scheduled SMS. After reopening the app, a pending contact alert also appears on Listen and in Settings with a cancellation button. Connection failures show unconfirmed status and cancellation is retried while the app runs and after reopening. Opening a notification or pressing Android Back does not acknowledge an alert. Once sending starts, the SMS cannot be recalled. Stopping listening does not cancel an already scheduled SMS.

Configure these values before enabling the feature:

```dotenv
# backend/.env — Twilio credentials stay on the server
TWILIO_ACCOUNT_SID=your_account_sid
TWILIO_AUTH_TOKEN=your_auth_token
TWILIO_FROM_NUMBER=your_sms_capable_twilio_number
ESCALATION_API_KEY=your_random_private_installation_access_key

# frontend/.env — matches the server access key
EXPO_PUBLIC_ESCALATION_API_KEY=your_random_private_installation_access_key
```

Restart the backend and Expo after configuration. A Twilio account and SMS-capable sender are required; trial accounts may require verified recipient numbers. No live SMS is sent by tests. Integration follows the [Twilio Messages API](https://www.twilio.com/docs/messaging/api/message-resource).

The backend must remain running, with **one uvicorn worker**, and persistent writable storage for `backend/escalations.sqlite3` (override with `ESCALATION_DB_PATH`). Do not use reload mode for a live demonstration of SMS. Pending countdowns resume after a server restart; incidents overdue by more than five minutes fail instead of sending a stale alert. Status distinguishes queued/sent from delivered. Ambiguous provider timeouts are shown as unknown and are not automatically resent, to avoid duplicate texts.

Privacy exception for this optional feature: the backend holds the recipient number, your name, sound class, and deadline until cancellation or a send attempt; it then removes the number/name. Status records expire after 24 hours while the worker runs. Twilio processes the recipient and SMS content under its own retention policies. No audio or location is included. The access key is for a **private hackathon installation** and is embedded in the app; before public deployment, replace it with per-user authentication, ownership checks, and abuse controls. Use HTTPS outside local development.

- Product spec: [`memory/PRD.md`](memory/PRD.md)
- Architecture, class table and API contract: [`PLAN.md`](PLAN.md)
- Backend details: [`backend/README.md`](backend/README.md)
- App details: [`frontend/README.md`](frontend/README.md)

## Quick start (Windows)

```powershell
# 1. Backend (Python 3.11)
py -3.11 -m venv backend\.venv
backend\.venv\Scripts\python -m pip install -r backend\requirements.txt
Copy-Item backend\.env.example backend\.env
backend\.venv\Scripts\python -m uvicorn backend.server:app --host 0.0.0.0 --port 8000

# 2. App (Android dev build; Expo Go cannot record in the background)
cd frontend
npm install
# set EXPO_PUBLIC_BACKEND_URL in frontend\.env:
#   emulator -> http://10.0.2.2:8000, phone -> http://<PC LAN IP>:8000
npx expo run:android
```

## Checks

```powershell
backend\.venv\Scripts\python -m pytest backend\tests
cd frontend
npx tsc --noEmit
npx expo lint
npm test
```

## Status

- Backend: stateless contract, audio policy, teach quality/rejections, and real YAMNet smoke test covered. Jev and Groq are optional and need `JEV_API_URL`/`JEV_API_KEY` and `GROQ_API_KEY`; without Jev, events are never grouped.
- App: typecheck, lint, alert-logic tests, local merge/grouping tests, and storage reload/clear tests.
- Not yet validated: background survival, notification channels and vibration feel on a physical Android phone.

History and custom sounds are available locally without the service. Detection and Jev grouping still need a network connection. The app sends its custom fingerprint with each analysis request; the service never retains it. Grouping runs after alert delivery with a 3-second timeout; errors keep separate rows. Clearing app data removes history and custom sounds. Existing server data is not imported automatically; teach the custom sound again on the phone.

In Windows PowerShell environments that block npm scripts, use `npx.cmd` and `npm.cmd` for the same commands.

Aware is not a certified safety device and must not replace smoke alarms or emergency equipment.
