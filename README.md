# Aware

Native Android sound awareness for Deaf and hard-of-hearing users: background
sound alerts, haptics, captions, and optional on-device situation summaries.
See [PLAN.md](PLAN.md) for the architecture and later steps.

## Prerequisites

- Android Studio with support for Android Gradle Plugin 8.13.2.
- JDK 17 (select it as the Gradle JDK in Android Studio).
- Android SDK Platform 35, Build Tools 35.0.0, and Platform Tools, installed through
  Android Studio's SDK Manager.
- A physical Android device running Android 8.0 / API 26 or later is recommended,
  with USB debugging enabled. The optional LLM will need a capable physical device.
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
   also work. Model files are ignored by Git. The optional LLM is installed separately below.

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

## Avatar and live captions

The single green sign-language bubble opens the avatar above live caption text.
Home and Settings both open this same screen. Captions start automatically;
the viewing surface has no headings, badges, language metadata, or control row.
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
This is experimental English sign animation and fingerspelling,
not validated ASL translation; translated captions are displayed as text. Long
utterances and fingerspelling can accumulate a playback backlog.

## Optional on-device LLM

Everything works without an LLM model. **What's happening?** immediately shows a
deterministic quick summary of the last 30 seconds. Related sound alerts and final
speech can also trigger a summary, at most once every 20 seconds. With **Smart
summaries (on-device AI)** enabled and a compatible model installed, an on-device
sentence can replace the template. Alerts and haptics never wait for the LLM.

1. Download a **Gemma 3 1B int4 `.task`** model, for example from the
   [litert-community/Gemma3-1B-IT repository](https://huggingface.co/litert-community/Gemma3-1B-IT).
   Accept the model's license/access terms if required. Choose a `.task` variant
   intended for Android MediaPipe LLM Inference; model formats such as `.litertlm`
   are not interchangeable. Consult the
   [MediaPipe Android guide](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
   for compatible variants. Rename the downloaded file to `model.task`.
2. In **Settings → Import model file (.task)**, choose the download. The file must
   have a `.task` extension and be larger than 50 MB. Import shows progress when
   the document provider reports its size (otherwise 0% until completion), writes
   a temporary file, and atomically installs it as `filesDir/llm/model.task`.
   **Remove model** deletes the preferred installed file. An adb-owned file may
   require removing it through adb instead.
3. Alternatively, with the device connected, run from the model's directory:

   ```text
   adb shell mkdir -p /data/local/tmp/llm
   adb push model.task /data/local/tmp/llm/model.task
   adb shell chmod 644 /data/local/tmp/llm/model.task
   ```

   The imported private file takes priority; the adb path is used only when
   readable by the app. Android permissions/SELinux can prevent reading that path;
   use Settings import in that case. Remove an adb-installed model with
   `adb shell rm /data/local/tmp/llm/model.task`.

The LLM requires at least 4 GB total RAM and a device that Android does not mark
as low-RAM. Settings shows installation/loading/readiness/failure status. Loading
is lazy, each call has an eight-second result deadline, and an idle model unloads
after two minutes. Blocking native work may finish after that deadline; its result
is discarded and cleanup waits for native work to finish safely. Disabling smart
summaries keeps quick summaries available. Model compatibility, speed, and memory
use still need verification on the target phone; extension and size validation
cannot prove that a particular download is compatible. No LLM model is bundled.

## Verification status

Verified on October 25, 2026: debug APK builds, all 67 JVM unit tests pass, and
Android lint passes with warnings. Seven JavaScript tests cover caption queueing,
deduplication, cancellation, pause/resume, and compilation of real caption clips:

```powershell
node --experimental-default-type=module --test scripts/test_avatar_sync.mjs
```

`node scripts/smoke_avatar.mjs` also passes against the bundled avatar in headless
Chrome: local modules/model/textures load, ordered clips play, start/completion
bridge callbacks match captions, and Clear cancels pending playback. It uses a
temporary Chrome profile under `app/build/avatar-smoke/`; `CHROME_PATH` overrides
the executable location. Android WebView was also checked on the connected
Samsung phone. This found and fixed a zero-height canvas caused by loading before
Compose layout. The app now loads the WebView after measurement and sizes the
canvas from its visual viewport. The single captions button, plain caption
surface, and visible signer were checked on the phone. Continuous recognition
and device performance have not been benchmarked; signing remains experimental.

Three connected Android tests exercise the production caption-to-avatar path on
the phone: a partial word causes visible mesh motion (verified by changed avatar
pixels and joint movement), finalized text does not replay signed words, and
continued speech maps to the correct native caption highlight. Generated speech
audio is also passed through Android's actual recognizer into the caption stream
and signer, including a finalized segmented result. No user recording is needed
for this test.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Connected tests retain the app after finishing. Avatar comparison screenshots
are collected under `app/build/outputs/connected_android_test_additional_output/`.
