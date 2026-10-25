# Aware — Build Plan

Fully on-device sound awareness for Deaf and hard-of-hearing users.
Native Android · Kotlin · Jetpack Compose · minSdk 26.

Roles: Claude plans and reviews and keeps this file current. Codex writes the code (via codex-plugin-cc, model `gpt-6.1-sol`).
Rule: finish one step and confirm it builds and runs before starting the next.

---

## Architecture (simplest thing that works)

Single Gradle module `:app`, package `com.aware.app`. One audio pipeline, owned by the
foreground service, that exposes `StateFlow`s the UI observes.

```
AudioSource (mic | demo fixture) ──16 kHz mono PCM, ~1 s windows──► Pipeline
   │                                                                 │
   │   ┌─ SoundClassifier (YAMNet model | deterministic demo label)  │
   │   ├─ CategoryMapper   (AudioSet label → Category)               │
   │   ├─ AlertEngine      (2–3 window smoothing, cooldown, RMS trend)
   │   │        │                                                    │
   │   │        ├─ instant path ► HapticPlayer + template AlertCard (<1 s)
   │   │        └─ event path   ► SituationSummarizer (LLM | Template fallback)
   │   └─ Speech: Transcriber ► NameDetector, Translator (ML Kit)    │
   ▼                                                                 ▼
ListeningService (foreground, mic type) ── StateFlow ──► Compose UI
```

| Package | Contents |
|---|---|
| `audio` | `AudioSource` interface, `MicAudioSource` (AudioRecord), RMS util; guided demo source lives in `demo` |
| `classify` | `YamnetClassifier` wrapper around MediaPipe |
| `alert` | `Category` enum, `CategoryMapper`, `AlertEngine`, `Alert`/`Trend` models |
| `haptics` | `HapticPatterns.kt` (the **single editable config**), `HapticPlayer` |
| `speech` | `Transcriber` interface + SpeechRecognizer impl, `NameDetector`, `Translator` |
| `llm` | `SituationSummarizer` interface, `MediaPipeLlmSummarizer`, `TemplateSummarizer` |
| `service` | `ListeningService` (foreground), `AwarePipeline` |
| `data` | `SettingsRepository` (DataStore) |
| `ui` | theme (dark, high-contrast), Home, Settings, HapticTest, EmergencyActivity, Demo panel, floating bubble |

### Key decisions and risks
- **Models are not committed.** `yamnet.tflite` (~4 MB) is downloaded by a script into `app/src/main/assets/`. The LLM `.task` file (~0.5–1.5 GB) is pushed with `adb` to app storage at runtime and is optional.
- **Mic contention (risk):** Android `SpeechRecognizer` opens its own mic, which conflicts with AudioRecord. Plan: on API 33+ feed our shared PCM to it through `EXTRA_AUDIO_SOURCE`; on older devices pause classification while the transcription bubble is active. whisper.cpp stays the fallback if offline recognition isn't available on the demo phone. Decide in step 6.
- **LLM API (risk):** MediaPipe LLM Inference is being superseded by LiteRT-LM. Keep it behind `SituationSummarizer` so it can be swapped; the template fallback must always work.
- **Floating bubble:** an in-app floating button on every screen that opens live transcription. A system-wide overlay (`SYSTEM_ALERT_WINDOW`) is a stretch goal.
- **Demo mode** feeds paced fixture windows and deterministic labels through the same smoothing, alert-card, and haptic pipeline. It teaches the UI reliably; it is not a YAMNet accuracy test.

---

## Steps

### 1. Project setup — ✅ done
- [x] Gradle (Kotlin DSL + version catalog), Compose, minSdk 26. AGP 8.13.2 / Gradle 8.13 / Kotlin 2.2.21, compile+target SDK 35
- [x] Dependencies: MediaPipe tasks-audio 0.10.26.1, tasks-genai 0.10.27, ML Kit translate, DataStore, lifecycle/service
- [x] Manifest: RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS, VIBRATE, WAKE_LOCK, USE_FULL_SCREEN_INTENT
- [x] Dark high-contrast theme + runtime permission screen (rechecks on resume, links to app settings when denied)
- [x] `scripts/download_models` (YAMNet) + README. `yamnet.tflite` downloaded (4.1 MB)
- [x] Gradle wrapper created by Android Studio
- [x] ✅ Verified: runs on a physical phone (emulator blocked by WHPX not being enabled; not needed)

### 2. Live capture + YAMNet on screen — ✅ done
- [x] `AudioSource` interface + `MicAudioSource` (16 kHz mono, 15600-sample windows, 50% hop, so a result about every 0.5 s; MIC→DEFAULT fallback; conflated so it never queues stale audio)
- [x] `YamnetClassifier` (MediaPipe AUDIO_CLIPS, top 5, threshold 0.1) + RMS/dBFS util. API calls checked against the 0.10.26.1 binaries
- [x] `AwarePipeline` exposing `StateFlow<PipelineState>` (owned by `HomeViewModel` for now; moves to the service in step 5)
- [x] Home screen: start/stop button, level meter, top-5 labels with score bars, error text; capture stops when the app leaves the foreground
- [x] ✅ Verified: user reports it running on the phone
### 3. Category mapping, smoothing, cooldowns, loudness trend — ✅ done (user accepted)
- [x] `Category` + granular `SoundType` (haptics key off it in step 4); SPEECH goes in the feed but never alerts
- [x] `CategoryMapper`: 36 exact YAMNet labels (checked against the model's 521 labels). Broad Alarm/Glass/Tap/Telephone/Microwave labels left out on purpose
- [x] All tuning (score thresholds + `requiredWindows`) lives in `CategoryMapper`: glass 1 window; siren/alarm/scream/knock/doorbell/horn/bell 2; sustained sounds 3. Thresholds: emergency 0.22, glass 0.35, vehicle/water/beep 0.40, others 0.30. Typical time to alert: about 1–1.5 s (emergency about 1 s)
- [x] `AlertEngine`: sensitivity ±40%, per-type cooldown (5 s emergency / 8 s other), category toggles, 4 s least-squares dB slope (±1.5 dB/s)
- [x] YAMNet now passes its top 20 labels (score ≥ 0.05) to the engine, not the top 5, so speech labels can't crowd out other sounds
- [x] Smoothing is now 'k of the last k+1 windows' (one weak window is tolerated), not strictly consecutive
- [x] Settle delay: supported but **off by default** (0 ms); timing is back to before the delay was added. Confidence = mean score while held; only the most confident sound per category is shown, and weaker rivals go into cooldown
- [x] Home keeps the stronger alert: a weaker new alert won't replace one under 3 s old (emergencies always do). The card shows a confidence %
- [x] JUnit: 23/23 pass (`scripts/test_alerts.ps1` runs them without the Android SDK)
- [x] Pipeline exposes `latestAlert` + `feed` (30 items, speech at most every 3 s); Home has an alert card, live feed and a debug toggle for raw labels
- [x] User accepted and moved on. Logcat tag `Aware` is available if tuning is needed later
### 4. Haptic patterns, alert cards, full-screen emergency — ✅ done
- [x] `HapticPatterns.kt`: the single editable config, a pattern per sound type + NAME_CALLED (validated: equal lengths, amplitude 0–255, ≤ 2.5 s)
- [x] `HapticPlayer` (VibratorManager on 31+, amplitude fallback, ALARM usage for emergencies)
- [x] `AlertMessages` templates (string resources, ≤ 12 words, trend-aware); also the LLM fallback in step 7
- [x] Pipeline `alerts` SharedFlow → `AlertReactor` (haptics + emergency launch); moves into the service in step 5
- [x] `AlertCard` (category band + icon, message, trend chip, confidence, tap replays the haptic)
- [x] `EmergencyActivity`: show-when-locked, 0.67 Hz smooth red↔black fade (solid red if animations are off), repeating haptic, 88 dp dismiss button, 30 s auto-stop
- [x] Haptic test screen + navigation (home ↔ haptics)
- [x] Home resumes listening when you come back from the emergency screen (stopgap until step 5)
- [x] Tests: 26/26 pass (including the haptic pattern tests)
- [x] ✅ Verified on device by the user (after fixing an unescaped apostrophe in strings.xml)
### 5. Foreground service for background listening — ✅ code done (user moved on; device check pending)
- [x] `ListeningRepository` object: state/running flows + start/stop (stop uses stopService, so it never creates an idle service)
- [x] `ListeningService` (LifecycleService, mic FGS type set before the model loads, 4 h partial wakelock, START_NOT_STICKY, full cleanup on stop/error/destroy) owns the pipeline + AlertReactor
- [x] Persistent silent notification: "Aware is listening" + latest alert (sampled every 3 s) + a Stop action
- [x] Heads-up alert notifications (one per category, channel vibration off, our haptics play instead); emergency uses a full-screen intent (gated on canUseFullScreenIntent on 34+) plus a direct launch when the app is in the foreground; dismiss cancels it
- [x] HomeViewModel/HomeScreen observe only; the ON_STOP stop and resume hack are removed
- [x] Checks: 26/26 tests pass; `scripts/check_strings.ps1` passes (XML valid, all 95 referenced strings exist, apostrophes escaped); all Kotlin compiles against the cached jars
- [ ] ✅ Verified on device: locked screen + knock → vibration and notification; siren → full-screen alert
### 6. Settings, offline STT, name detection, ML Kit translation, floating captions bubble — 🟡 code done, awaiting device verification
**Mic decision:** API 33+ feeds our own PCM to SpeechRecognizer via `EXTRA_AUDIO_SOURCE` (alerts and captions run together). Below 33, classification pauses while captions are on.
- [x] `SettingsRepository` (DataStore): categories, name, sensitivity, translation target, name toggle; applied live by the service
- [x] Settings screen (+ full-screen-intent permission shortcut on 34+, offline speech status/download)
- [x] `MicAudioSource.pcm` raw tap; `Transcriber` (SharedAudio on 33+, Mic fallback) + `SpeechController` (burst mode on speech for name detection; continuous for captions)
- [x] `NameDetector` (fuzzy, JVM tests) → three taps + "Someone said your name" alert
- [x] `LiveTranslator` (ML Kit, offline after download)
- [x] Floating captions button → Captions screen (huge text, partials, translation)
- [x] Checks: 32/32 tests (26 + 6 NameDetector); strings 127/127 referenced names exist; 35 Kotlin files compile against the cached jars
- ⚠️ Risk: some recognizers ignore `EXTRA_AUDIO_SOURCE` and open their own mic; `triggerModelDownload` may need user action. Verify on the phone
- [ ] ✅ Verified on device
### 7. On-device LLM with template fallback — 🟡 code done, awaiting device verification
- [x] `SituationContext` JSON, `TemplateSummarizer` (≤ 15 words, combo rules), `OutputSanitizer`, all JVM-tested
- [x] `MediaPipeLlmSummarizer`: model at filesDir/llm/model.task or /data/local/tmp/llm/model.task; 4 GB RAM gate; lazy load; 8 s timeout; unload after 2 min idle; any failure → template
- [x] `SituationController`: 30 s buffer; auto trigger (HOME/PEOPLE/EMERGENCY alert within 10 s of speech, 20 s cooldown) + "What's happening?" button; template first, then LLM replaces it
- [x] UI: Situation card + button on Home, summary on AlertCard, Settings switch/status/import model
- [x] Checks: 61/61 tests (32 + 29 new), strings 145/145, 43 Kotlin files compile; LlmInference + LlmInferenceSession APIs checked with javap against tasks-genai 0.10.27
- ⚠️ /data/local/tmp is often unreadable by apps on newer Android, so prefer **Import model file** in Settings
- [ ] ✅ Verified on device (with and without a model)
### 8. Guided demo mode — 🟡 code done, awaiting device verification
- [x] Six deterministic scenarios: doorbell, knock, siren, glass breaking, baby crying, and running water
- [x] Demo input uses the real `AwarePipeline`, `AlertEngine`, alert cards, and haptics without opening the microphone
- [x] Demo emergency events suppress notifications and the full-screen emergency activity
- [x] Demo mode is in-app only, stops when its screen/ViewModel is cleared, and cannot start during live listening
- [x] Mapping tests verify every demo label resolves to its intended `SoundType`
- [ ] ✅ Verified on device

---

## Environment / blockers
- 2026-10-25: JDK 17 present. **Android SDK / Android Studio not installed**, so no local build is possible yet. Install Android Studio and open the project to sync and run.
- Codex CLI 0.164.0 ready and logged in.

## Log
- 2026-10-25 — Plan created. Step 1 delegated to Codex.
- 2026-10-25 — Codex finished the step 1 skeleton (20 files). Claude reviewed it and downloaded YAMNet. Waiting on Android Studio sync and a device run before step 2.
- 2026-10-25 — User said to go ahead with step 2 before verifying on a device. Codex finished step 2 (6 new files + HomeScreen/strings), with JVM unit checks and a compile against the real MediaPipe jars. Claude reviewed it. Steps 1 and 2 now need a device check together.
- 2026-10-25 — User ran the app on a physical phone (the emulator hit a WHPX error; skipped). Steps 1–2 marked done. Step 3 delegated to Codex.
- 2026-10-25 — Codex finished step 3 (19 tests). Claude's review found transient sounds needed too many windows; Claude fixed it directly (the Codex follow-up never started) by adding per-type `requiredWindows` and lowering the knock threshold. 20/20 tests pass.
- 2026-10-25 — User asked for highest-confidence alerts plus a short delay for robustness, done by Claude directly. Added the settle period, best-per-category selection, the 3 s hold for stronger alerts and a confidence % on the card. 23/23 tests pass.
- 2026-10-25 — User reported that only speech was showing and the earlier version was more sensitive. Root cause: top-5 labels were crowded out by speech labels. Fix: top 20 labels, settle cut to 0.5 s (emergency 0), thresholds lowered about 15%. 23/23 tests pass.
- 2026-10-25 — Still only speech on device. Engine logic is verified by tests, but the exact on-device cause is unconfirmed. Reverted the settle delay to 0, made smoothing tolerate one missed window, and added `Log.d("Aware")` of each window's top 8 labels + fired alerts for diagnosis. 23/23 tests pass.
- 2026-10-25 — User accepted step 3. Step 4 delegated to Codex.
- 2026-10-25 — Codex was about 60% through step 4 when the user asked Claude to finish it. Claude stopped Codex, reviewed its files, and added the 38 missing strings, the manifest entry, navigation, the Test vibrations button, the test-script fix and the haptic tests, plus auto-resume after an emergency. 26/26 pass.
- 2026-10-25 — User verified step 4 on the phone. Step 5 delegated to Codex.
- 2026-10-25 — User switched Codex to gpt-6-astra. Cancelled the step 5 run on gpt-6.1-sol (no files had changed) and restarted step 5 on astra.
- 2026-10-25 — Cancelling the sol run didn't stop its Codex thread; it and the astra run edited step 5 at the same time. Killed the plugin app-server tree. The sol run had drafted most of step 5. A fresh astra run is now reviewing and finishing it.
- 2026-10-25 — Astra finished step 5 from the sol draft (fixed failure cleanup, the idle-service stop, notification permission races and redundant updates). Claude re-ran the tests and string check (pass) and reviewed the service, repository, reactor and UI wiring.
- 2026-10-25 — User moved on from step 5 (device check pending). Step 6 delegated to Codex astra in one task.
- 2026-10-25 — The first step 6 launch never ran: an apostrophe in the prompt broke the shell quoting. Relaunched directly via `codex-companion task --write --model gpt-6-astra --prompt-file` (07:52 UTC).
- 2026-10-25 — Astra finished step 6 (~12 min). Claude re-ran the tests (32 pass) and string check (pass), and reviewed SharedAudioTranscriber (pipe + extras, main thread, backoff), NameDetector, and the MicTranscriber fallback/pause path.
- 2026-10-25 — User switched the Codex model back to gpt-6.1-sol for the remaining steps.
- 2026-10-25 — Pushed to GitHub (dikshyant1222/Aware). `main` and `android-native` now point to fed794d, the Android app committed on top of the old main history (fast-forward, no force push). The old Expo/Python main (a199221) is kept as `expo-prototype`. Teammate branches `pragoon_features` and `prashanta` are untouched (they're based on a199221). The local folder is now a git repo on `android-native`, tracking origin. gradlew isn't committed (Android Studio provides Gradle).
- 2026-10-25 — Step 7 delegated to Codex gpt-6.1-sol via prompt file (08:23 UTC).
- 2026-10-25 — Codex sol finished step 7 (~13 min). Claude re-ran the tests (61 pass) and string check (pass), and reviewed the LLM wrapper (timeout, mutex, idle unload, RAM gate, Throwable catch) and the template-first controller.
