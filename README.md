# Aware

Native Android sound awareness for Deaf and hard-of-hearing users: background
sound alerts, haptics, captions, and on-device AI triage and situation summaries.
See [PLAN.md](PLAN.md) for the architecture and later steps.

## Prerequisites

- Android Studio with support for Android Gradle Plugin 8.13.2.
- JDK 17 (select it as the Gradle JDK in Android Studio).
- Android SDK Platform 35, Build Tools 35.0.0, and Platform Tools, installed through
  Android Studio's SDK Manager.
- A physical Android device running Android 8.0 / API 26 or later is recommended,
  with USB debugging enabled. The on-device LLM needs a physical device with 7 GB RAM or more.
- PowerShell on Windows, or Bash and `curl` on macOS/Linux, plus internet access
  for dependency and model downloads.

The project uses AGP 8.13.2, Gradle 8.13, Kotlin and its Compose compiler plugin
2.2.21, and Compose BOM 2025.04.01. Java and Kotlin target JVM 17; compileSdk and
targetSdk are 35, minSdk is 26. AndroidX versions are pinned for API 35.
The AGP/Gradle/JDK pairing follows the
[official compatibility table](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
All dependency versions are in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Setup and run

1. Download YAMNet from the project root using either script:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\download_models.ps1
   ```

   ```bash
   bash scripts/download_models.sh
   ```

   Both scripts create `app/src/main/assets/`, download `yamnet.tflite`, and skip
   an existing file. Paths are relative to the script, so other working directories
   also work. Model files are ignored by Git. The on-device LLM model is installed separately below.

2. Open this directory in Android Studio. Install the SDK packages above and
   select JDK 17 as the Gradle JDK.

   The Gradle 8.13 wrapper is included (`gradlew.bat` on Windows, `./gradlew` on
   macOS/Linux).

3. Sync Gradle, connect your device, select the `app` configuration, and run.
4. Tap **Grant permissions**. Allow microphone access and, on Android 13+,
   notifications. Tap **Start listening** to recognize nearby sounds.
   After denial, **Open app settings**
   lets you enable the permissions; returning to the app refreshes the screen.

Listening continues in a foreground service until you stop it. Captions use the
phone's speech recognizer; offline availability depends on the installed speech models.

On Windows, this command builds, installs, and opens the app on a connected,
authorized phone or a running emulator:

```powershell
powershell -File .\scripts\run_android.ps1
```

Use `-SkipBuild` to install the existing debug APK, or `-DeviceSerial <serial>`
when multiple devices are connected. Enable USB debugging on the phone and accept
its USB authorization prompt. The script reports an actionable error if no device
is ready.

The installed `Medium_Phone_API_37.0` emulator could not start on the verification
computer: hardware acceleration was unavailable and the software-only fallback
also exited. For an emulator, configure
[Windows Hypervisor Platform](https://developer.android.com/studio/run/emulator-acceleration#vm-windows),
restart Windows, and start the virtual device in Android Studio's Device Manager.

## App interface

The native Compose UI uses a pale sky/mint backdrop, rounded white cards, and a
shared floating Home / CC / History / Settings dock. The microphone control keeps
the existing listening toggle; its halo indicates state rather than loudness.
Home previews two distinct recent sounds and expands to five. Summary previews
open a full reading sheet, and alert details retain their replay actions.

First launch collects name and date of birth, followed by a three-sound demo check.
The profile is local and fixed until app data is cleared. Name detection is always on;
check responses do not change detection behavior or classify hearing ability.
Settings shows the read-only profile, categories, sensitivity, language/speech, and permissions.
Smart-summary model controls are hidden in this demo UI; existing model/backend support remains. Vibration testing and four-label sound diagnostics are
available only in the collapsed developer section of debug builds. Emergency
messages retain their explicit large, high-contrast styles.

UI projection tests run with `:app:testDebugUnitTest`. `AwareUiTest` uses fixture
data only in instrumentation to check expansion, sheets, navigation, and
320–412 dp layouts with font scales through 2.0. It writes screenshots to the
connected Android test additional output directory. Run connected UI tests on an
unlocked device; APK retention is enabled so tests preserve the development app.

## Avatar and live captions

The single CC control in the dock opens Live Talk, with a rounded signer panel
above large live caption text. A compact header provides Back; the dock is hidden
on this screen. Wide landscape windows arrange signer and text side by side.
Home and Settings both open this same route. Captions start automatically;
the signing area has no badges, language metadata, or overlaid control row.
Stable words stream into the signer while recognition is still partial. A short
pause flushes the remaining word even if the recognizer has not finalized the
sentence; a final result sends only the unsent tail. The injected audio source
requests segmented recognition and handles finalized segment callbacks. Words
are queued once in order; translation updates do not replay them. The exact word
or phrase being signed is highlighted using playback callbacks. The caption
being performed stays in view. Leaving the screen stops captions and
releases its WebView. Model or rendering failures show Retry
while text captions remain usable.

Signing follows recognition with a short stability delay (up to 800 ms for an
unchanging partial word). Live clips use shorter holds than the studio preview.
Playback targets 60 fps and blends the final solved arm, wrist, and finger poses
over 180 ms at each word boundary. Queued words keep the hands in signing space
instead of lowering and lifting them between clips. The next clip and handshapes
are prepared ahead of the handoff and cached; finger transitions use eased motion.
This is experimental English sign animation and fingerspelling,
not validated ASL translation; translated captions are displayed as text. Long
utterances and fingerspelling can accumulate a playback backlog.

## On-device LLM (Gemma 4 E2B)

The LLM makes the app smarter but never gates an alert: detection, haptics and the
first notification always fire immediately from the audio classifier.

- **Summaries:** every 40 seconds, and shortly after each new alert, the service
  summarizes the last 40 seconds of sounds and speech in one sentence. The Home card
  shows it; History keeps 10 minutes locally. A model summary replaces its
  template entry rather than duplicating it.
- **Triage:** the model also rates the situation LOW, NORMAL, HIGH or CRITICAL.
  `SituationRules` computes an instant floor from sound combinations (for example
  smoke alarm + scream, or an emergency sound + someone calling for help). The
  model can raise that floor by one level, and lower it only when no emergency
  sound is present. A situation worse than the alerts already delivered triggers
  an "Urgent" notification with a strong vibration, at most once per 30 seconds;
  the model's wording then replaces the template text silently.
- **Noise filtering:** when the model rates the situation LOW with no speech,
  repeat alerts for running water, appliance beeps, vehicles and dog barks are
  muted for 60 seconds. No other sound type can be muted, and any other alert
  lifts every mute.
- **Without a model** the app uses template summaries and the rules floor.

### Installing the model

Download `gemma-4-E2B-it.litertlm` (about 2.6 GB, LiteRT-LM format; `.task` files
do not work). Then either:

1. **In the app:** Settings -> On-device AI -> *Import model file (.litertlm)*. The
   file must end in `.litertlm` and be larger than 50 MB. It is copied to a temporary
   file and atomically installed as `filesDir/llm/model.litertlm`. *Remove model*
   deletes it.
2. **With adb (debug builds):** `node scripts/install_llm.mjs <model path> [device serial]`
   verifies the official file's size and SHA-256, then copies it into the app's
   private storage. The app also reads `/data/local/tmp/llm/model.litertlm` as a
   fallback; Android permissions can block that path.

The device needs at least 7 GB total RAM, a 64-bit ABI, and must not be marked
low-RAM; otherwise Settings shows "not supported". The engine tries the GPU backend,
then CPU. The first load can take up to two minutes; later calls have a 15-second
result deadline, and an idle model unloads after two minutes. Blocking native work
may finish after a deadline; its result is discarded. Speed, memory use and triage
quality still need verification on the target phone. No model is bundled.

## Verification status

Verified on October 27, 2026: debug APK builds, all 91 JVM unit tests pass, and
Android lint passes with warnings. Eighteen JavaScript tests cover caption queueing,
deduplication, cancellation, pause/resume, clip compilation, pose continuity, and
the authored signing vocabulary:

```powershell
node --test scripts/test_avatar_sync.mjs scripts/test_avatar_transitions.mjs scripts/test_avatar_vocabulary.mjs
```

`node scripts/smoke_avatar.mjs` also passes against the bundled avatar in headless
Chrome: local modules/model/textures load, ordered clips play, start/completion
bridge callbacks match captions, rendered word handoffs stay continuous, and
Clear cancels pending playback. It uses a
temporary Chrome profile under `app/build/avatar-smoke/`; `CHROME_PATH` overrides
the executable location. Android WebView was also checked on the connected
Samsung phone. This found and fixed a zero-height canvas caused by loading before
Compose layout. The app now loads the WebView after measurement and sizes the
canvas from its visual viewport. The single captions button, plain caption
surface, and visible signer were checked on the phone. The short mixed-motion
sequence measures about 60 fps (16.7 ms median and 95th-percentile rendered frame
intervals on the test phone). Long-session recognition and thermal behavior have
not been benchmarked; signing remains experimental.

Four connected Android tests exercise the production caption-to-avatar path on
the phone: a partial word causes visible mesh motion (verified by changed avatar
pixels and joint movement), finalized text does not replay signed words, and
continued speech maps to the correct native caption highlight. Generated speech
audio is also passed through Android's actual recognizer into the caption stream
and signer, including a finalized segmented result. No user recording is needed
for this test. A mixed sign/fingerspelling sequence also checks rendered joint
continuity at word boundaries, frame cadence, and preparation of the next clip.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Connected tests retain the app after finishing. Avatar comparison screenshots
are collected under `app/build/outputs/connected_android_test_additional_output/`.
