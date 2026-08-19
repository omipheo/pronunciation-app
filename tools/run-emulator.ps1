# Boots an AVD, installs the app, and launches it.
#
#   .\tools\run-emulator.ps1                  # Android 15 (API 35)
#   .\tools\run-emulator.ps1 -Avd android8    # Android 8.0 (API 26) - the minSdk floor
#
# Test on android8 before shipping: API 26 is the oldest version the app supports and where
# compatibility problems actually surface. Lint cannot catch runtime issues there.
#
# Requires Windows Hypervisor Platform to be enabled AND the machine rebooted since enabling
# it. Everything here is local; nothing contacts the network.

param(
    [string]$Avd = "pronunciation"
)

$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Android\jdk17"
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Android\Sdk"

$adb = "C:\Android\Sdk\platform-tools\adb.exe"
$emulator = "C:\Android\Sdk\emulator\emulator.exe"
$avd = $Avd
$pkg = "com.example.pronunciation"

# The emulator is x86_64, so the arm64 APK will not install on it.
$apk = "app\build\outputs\apk\debug\app-x86_64-debug.apk"

function Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

# --- 1. Acceleration -------------------------------------------------------------------
Step "Checking hardware acceleration"
& $emulator -accel-check
if ($LASTEXITCODE -ne 0) {
    Write-Host @"

Acceleration is unavailable. Two causes, in order of likelihood:

  1. You enabled Windows Hypervisor Platform but have not rebooted yet. Reboot.
  2. It was never enabled. In an ADMIN terminal:
       dism /online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart
     then reboot.

"@ -ForegroundColor Yellow
    exit 1
}

# --- 2. Build if needed ----------------------------------------------------------------
if (-not (Test-Path $apk)) {
    Step "APK missing, building"
    & .\gradlew.bat assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }
}

# --- 3. Boot the emulator --------------------------------------------------------------
$running = (& $adb devices) -match "^emulator-\d+\s+device"
if ($running) {
    Step "Emulator already running"
} else {
    Step "Starting emulator '$avd' (first boot takes a few minutes)"
    Start-Process -FilePath $emulator -ArgumentList "-avd", $avd, "-no-snapshot-load"

    Step "Waiting for the device to appear"
    & $adb wait-for-device

    Step "Waiting for Android to finish booting"
    for ($i = 0; $i -lt 180; $i++) {
        $booted = (& $adb shell getprop sys.boot_completed 2>$null)
        if ("$booted".Trim() -eq "1") { break }
        Start-Sleep -Seconds 5
    }
    if ("$(& $adb shell getprop sys.boot_completed)".Trim() -ne "1") {
        throw "Emulator did not finish booting within 15 minutes"
    }
}

# --- 4. Install ------------------------------------------------------------------------
Step "Installing $apk"
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "Install failed" }

# --- 5. Launch -------------------------------------------------------------------------
Step "Launching the app"
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null

Write-Host ""
Write-Host "Running. Useful follow-ups:" -ForegroundColor Green
Write-Host "  Logs : $adb logcat -s SpeechEngine:* OnnxPhonemeRecognizer:* AudioRecorder:* Lexicon:* AndroidRuntime:E"
Write-Host "  Mic  : emulator toolbar -> ... -> Microphone -> enable 'Virtual microphone uses host audio input'"
Write-Host ""
Write-Host "If Section 1 is silent, the image has no TTS voice data:" -ForegroundColor Yellow
Write-Host "  Settings > System > Languages & input > Text-to-speech output"
