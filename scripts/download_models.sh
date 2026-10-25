#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
assets_directory="$project_root/app/src/main/assets"
model_path="$assets_directory/yamnet.tflite"
download_path="$model_path.download"
model_url="https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite"

mkdir -p -- "$assets_directory"
if [[ -f "$model_path" ]]; then
    printf 'Already present: %s\n' "$model_path"
    exit 0
fi

trap 'rm -f -- "$download_path"' EXIT
curl --fail --location --retry 3 --output "$download_path" "$model_url"
mv -- "$download_path" "$model_path"
printf 'Downloaded: %s\n' "$model_path"
