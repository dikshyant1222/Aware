# Aware — Product Requirements (source of truth)

Aware is an Android-focused accessibility app for Deaf and hard-of-hearing users. It continuously listens for important environmental sounds and converts detections into high-contrast visual alerts, distinct vibration signatures, Android notifications, and a concise event history. Scope is sound awareness only. **There is no Travel mode** (no tab, onboarding option, route, or announcement API).

## 1. Navigation
Three tabs: **Listen**, **Events**, **Settings**. Extra screens: **Onboarding**, **Teach a Sound**, **Privacy**.

## 2. Sound classes
Fire/smoke alarm, emergency siren, doorbell/knock, baby crying, vehicle horn, one user-taught custom sound. Each class has its own urgency, thresholds, icon, color treatment, and vibration signature.

## 3. Detection pipeline
App records 2-second segments and uploads them to FastAPI. Backend:
1. Decodes the recording in memory.
2. Deletes the uploaded raw bytes immediately.
3. Measures loudness, clipping, noise floor, signal level, estimated SNR.
4. Runs bundled YAMNet TFLite.
5. Evaluates evidence across multiple model frames.
6. Returns detection, confidence, ambient environment, supporting metrics.
Audio is never retained. The backend has no database; all user data is stored on the phone.

## 4. Noisy-environment intelligence
- **Raw + denoised ensemble:** every segment analysed as original and spectrally noise-suppressed (road, machinery hum, crowd, AC, room, TV background). Strongest reliable evidence from both is combined; denoised evidence gets a small confidence discount.
- **Broader evidence:** related AudioSet labels per class. Baby: crying, sobbing, whimper, infant cry. Door: doorbell, ding-dong, chime, door, knock. Siren: police, ambulance, fire engine, civil-defence, siren. Horn: car/truck/train/vehicle horn.
- **Frame-level confirmation:** a single spike cannot produce a noncritical alert. Strong detections alert immediately; weaker ones need evidence in multiple frames.
- **Environment estimate:** quiet, traffic, crowd/public, tv/music, workplace/machinery, mixed. Listen screen shows it with the ambient noise floor.

## 5. Alert logic
- Immediate alert when confidence is "likely", or the class is safety-critical (fire alarm, siren). A possible critical event is never silently discarded.
- Possible noncritical events (doorbell, baby, horn, custom) must appear in two nearby segments before a full alert.
- 8-second per-class cooldown after a full alert.

## 6. Visual alerts
Full-screen high-contrast overlay: sound name, urgency color, likely/possible, model signal strength, supporting frame evidence, environment, acknowledge button. Critical = strongest red; high = amber; attention = high-contrast neutral. A critical alert can't be replaced on screen by a lower-priority one.

## 7. Haptics
| Event | Vibration |
|---|---|
| Fire alarm | Three urgent pulses, repeating |
| Siren | Rising wave, repeating |
| Doorbell | Short-long chime |
| Baby crying | Gentle cry-like wave |
| Vehicle horn | Two strong pulses |
| Custom | Tap-tap-long |

Foreground: direct Android vibration/haptics; critical patterns repeat until acknowledged. Background: per-sound notification channel with its own vibration pattern. Foreground and notification vibration paths are separated so two patterns never overlap. Patterns can be previewed in Settings.

## 8. Automatic & background listening
After onboarding + mic permission, listening starts automatically. On later launches, listening restores if auto-listen is enabled or Aware was listening when the process closed. Android: background audio recording, microphone foreground service, FOREGROUND_SERVICE_MICROPHONE permission, persistent notification, background audio mode, unprocessed mono mic input. Privacy screen explains force-stop / permission removal stops listening.

## 9. Jev duplicate-alert grouping (Events log only - never touches audio)
1. Alert delivery starts immediately (visual + vibration + notification).
2. Independently, the app finds the most recent event of the same class in phone storage with last detection within 120 seconds.
3. If found, POST `/api/events/group` with `new`, `previous`, and `seconds_apart`; a short 3-second timeout/failure means no grouping.
4. Backend sends Jev text-only class, label, urgency, confidence, detection details, muted state, and seconds apart.
5. Jev returns a typed probability that both describe the same ongoing occurrence. Backend applies the 0.82 threshold and returns `{probability, same_occurrence}`; unconfigured/erroring Jev returns null/false.
6. The app merges locally only when confirmed: preserve ID/first time; update last time, repeat count, highest score, strongest confidence and Jev metadata. Otherwise insert a row with an ID generated on the device.
Events screen shows `xN`, first-last time range, "JEV GROUPED", probability. Jev never suppresses or delays alerts; the backend stores no history.

## 10. Events history
Text summaries only, stored on the phone as a JSON array in AsyncStorage (`aware.events`), maximum 500 rows, newest last detection first. Row: time/range, icon, urgency indicator, label, confidence, muted state, detection context, repeat count, Jev status/probability. Clear-all with confirmation clears local storage, including any pending older grouping results. History is available without contacting the backend. No database server, cross-phone history, or automatic import of old server data. Clearing app data removes the local data.

## 11. Teach a Sound
Name -> record >=2 s -> quality check -> record again -> compare fingerprints (cosine >=0.78) -> save normalized average on the phone. Step 1 returns a fingerprint to the app; step 2 sends it back as `first_fingerprint` and returns the normalized average. The backend retains no session, name or fingerprint. AsyncStorage stores `{name, created_at, fingerprint:number[]}` for one custom sound (`aware.customSound`). Rejections: too short, too quiet, clipped, mostly silence, speech-dominated, too noisy/unclear, differs from first sample. Analysis requests include the phone's optional `custom_fingerprint` and `custom_name`; matching runs only for that request. Match -> attention alert unless a more urgent sound is present. Replacing/removing a custom sound updates only phone storage.

## 12. Groq (optional)
Second opinion only for ambiguous noncritical labels. Receives text metadata (predicted class, score, top YAMNet labels, loudness) — never audio. Strong and critical detections bypass Groq.

## 13. Settings
Auto-listen toggle; Android background-protection status; per-sound enable/disable; per-sound sensitivity; vibration previews; optional AI label verification (Groq); teach/replace custom sound; notification permission; clear history; privacy info.

## 14. Onboarding
1. Product intro. 2. One-time mic + notification permission. 3. Start Aware → immediately begins listening.

## 15. Privacy rules
Audio decoded in memory; raw bytes discarded after decoding; temporary phone recordings deleted after processing. No audio retained, no database server. Event summaries, settings, custom name/date and normalized fingerprint are stored on the device only. The fingerprint is sent with analysis requests and never retained by the backend. Jev gets event text only; Groq gets label metadata only; neither receives audio. Mic can be stopped by system or user.

## 16. Demo
Tap the Aware title 5× to reveal a demo panel that triggers each class. Backend first runs synthesized samples through real YAMNet; if not reliably classified, a curated fallback keeps the demo usable. Demo events are flagged in history.

## 17. API
```
GET    /api/
GET    /api/status
POST   /api/listen/analyze
POST   /api/demo/sound/{class}
POST   /api/teach/sample
POST   /api/events/group
```
`/api/status` returns `{model_loaded, jev_configured, groq_configured}`. Analysis multipart accepts optional `custom_fingerprint` (JSON array) and `custom_name`. Teach step 1 returns `{ok:true, fingerprint, message}`; step 2 requires `first_fingerprint` and returns `{ok:true, similarity, fingerprint}` or a quality/mismatch rejection. Grouping accepts `{new, previous, seconds_apart}` and returns `{probability:number|null, same_occurrence:bool}`. Custom demo accepts optional query/form `custom_name` (default "Custom sound") and works without a taught sound.

All user data lives on the phone. `/api/events` GET/POST/DELETE and `/api/teach` GET/DELETE return 404. Any `/api/travel*` path returns 404. Grouping rejects unknown classes such as `announcement`.

## 18. Known limitations
Physical Android validation required; detection depends on network to the FastAPI service; not a certified safety device; one custom sound; real-world benchmark pack still to be built.
