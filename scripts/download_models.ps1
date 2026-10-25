$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$assetsDirectory = Join-Path $projectRoot 'app/src/main/assets'
$modelPath = Join-Path $assetsDirectory 'yamnet.tflite'
$downloadPath = "$modelPath.download"
$modelUrl = 'https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite'

New-Item -ItemType Directory -Path $assetsDirectory -Force | Out-Null
if (Test-Path -LiteralPath $modelPath -PathType Leaf) {
    Write-Output "Already present: $modelPath"
    exit 0
}

try {
    Invoke-WebRequest -UseBasicParsing -Uri $modelUrl -OutFile $downloadPath
    Move-Item -LiteralPath $downloadPath -Destination $modelPath
    Write-Output "Downloaded: $modelPath"
} finally {
    if (Test-Path -LiteralPath $downloadPath) {
        Remove-Item -LiteralPath $downloadPath
    }
}
