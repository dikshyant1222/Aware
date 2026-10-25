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

   Only `gradle/wrapper/gradle-wrapper.properties` is supplied. The wrapper JAR and
   `gradlew` / `gradlew.bat` could not be generated offline in this environment.
   Have Android Studio create the missing wrapper files using Gradle 8.13. If it
   does not offer to do so, use a local Gradle 8.13 installation in the project root:

   ```text
   gradle wrapper --gradle-version 8.13 --distribution-type bin
   ```

3. Sync Gradle, connect your device, select the `app` configuration, and run.
4. Tap **Grant permissions**. Allow microphone access and, on Android 13+,
   notifications. Tap **Start listening** to recognize nearby sounds.
   After denial, **Open app settings**
   lets you enable the permissions; returning to the app refreshes the screen.

Listening continues in a foreground service until you stop it. Captions use the
phone's speech recognizer; offline availability depends on the installed speech models.

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

Configuration, manifest declarations, resources, catalog references, and Kotlin
imports were reviewed statically. No Gradle build, dependency sync, emulator, or
device run was attempted: Android SDK / Android Studio are not installed in the
provided environment. Build and runtime verification remain pending in Android Studio.
