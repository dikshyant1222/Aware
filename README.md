# Aware

Android accessibility app for Deaf and hard-of-hearing users. Aware listens for fire/smoke alarms, sirens, doorbells/knocks, baby crying, vehicle horns and one taught sound, and turns them into full-screen visual alerts, distinct vibration patterns, notifications and a grouped event history.

All user data stays on the phone: settings, up to 500 event summaries, and one custom sound fingerprint in AsyncStorage. The stateless backend processes audio and text-only Jev/Groq requests; no database server is needed. Audio is not retained.

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
