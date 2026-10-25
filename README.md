# Aware

Native Android sound awareness for Deaf and hard-of-hearing users. Step 1 contains
only project setup, an always-dark accessible theme, runtime permissions, and a
home placeholder. No audio is captured, no model is loaded, and no service is
started. See [PLAN.md](PLAN.md) for the architecture and later steps.

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
   also work. Model files are ignored by Git. The skeleton itself runs without a model.

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
   notifications. The home screen shows **Aware** and
   **Ready — listening starts in step 2**. After denial, **Open app settings**
   lets you enable the permissions; returning to the app refreshes the screen.

The declared `ListeningService` is a stub that stops itself if started. Foreground
listening belongs to step 5. Audio, classification, haptics, speech, and LLM logic
are deliberately left for later steps. Package directories follow PLAN.md;
unused packages contain no placeholder source files.

## Optional LLM model (for step 7)

The app must work without an LLM model. Step 1 does not copy or read one.

1. Download a **MediaPipe-compatible Gemma approximately 1B int4 `.task`** model
   from Kaggle or Hugging Face, for example the
   [litert-community/Gemma3-1B-IT repository](https://huggingface.co/litert-community/Gemma3-1B-IT).
   Accept the model's license/access terms if required. Choose a `.task` variant
   intended for Android MediaPipe LLM Inference; model formats such as `.litertlm`
   are not interchangeable. Consult the
   [MediaPipe Android guide](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
   for compatible variants. Rename the downloaded file to `model.task`.
2. With the device connected, run from the model's directory:

   ```text
   adb shell mkdir -p /data/local/tmp/llm
   adb push model.task /data/local/tmp/llm/model.task
   ```

3. Step 7 will implement copying/reading from that staging path, accounting for
   device file permissions, and retain a template fallback when the model is
   absent or unavailable. No LLM model is bundled or committed.

## Verification status

Configuration, manifest declarations, resources, catalog references, and Kotlin
imports were reviewed statically. No Gradle build, dependency sync, emulator, or
device run was attempted: Android SDK / Android Studio are not installed in the
provided environment. Build and runtime verification remain pending in Android Studio.
