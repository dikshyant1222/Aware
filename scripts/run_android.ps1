param(
    [string]$DeviceSerial = '',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, "$env:LOCALAPPDATA/Android/Sdk", 'C:/android-sdk')
$sdk = $sdkCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'platform-tools/adb.exe')) } | Select-Object -First 1
if (!$sdk) { throw 'Android SDK not found. Set ANDROID_SDK_ROOT to your SDK directory.' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$deviceRows = @(& $adb devices)
if ($LASTEXITCODE -ne 0) { throw 'Could not query Android devices.' }
$connected = @($deviceRows | ForEach-Object {
    if ($_ -match '^(\S+)\s+device$') { $Matches[1] }
})
if (!$DeviceSerial) {
    if ($connected.Count -eq 0) {
        throw 'No Android device is ready. Connect a phone with USB debugging and accept its authorization prompt, or start an Android Studio emulator with hardware acceleration configured.'
    }
    if ($connected.Count -gt 1) { throw "Multiple devices found. Run again with -DeviceSerial <serial>: $($connected -join ', ')" }
    $DeviceSerial = $connected[0]
}
if ($DeviceSerial -notin $connected) { throw "Device '$DeviceSerial' is unavailable or not authorized. Check 'adb devices'." }

Push-Location $projectRoot
try {
    if (!$SkipBuild) {
        & (Join-Path $projectRoot 'gradlew.bat') :app:assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    }
    $apk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
    if (!(Test-Path $apk)) { throw 'Debug APK not found. Run again without -SkipBuild.' }
    & $adb -s $DeviceSerial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
    & $adb -s $DeviceSerial shell am start -W -n com.aware.app/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
    Write-Host 'Aware launched. Allow microphone/notification permissions, then tap the green ASL bubble for avatar + live captions.'
} finally {
    Pop-Location
}
