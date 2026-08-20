# Installs the whole development environment from the downloaded archives. No internet.
#
#   .\offline-install.ps1
#   .\offline-install.ps1 -ProjectDir D:\work\pronunciation-app -SkipEmulator
#
# Everything lands under C:\Android and your user profile. No administrator rights are
# needed, and no Android Studio: the toolchain is a JDK zip, the SDK command-line tools and
# Gradle, which is all a build actually requires.
#
# Run this from inside the downloaded folder (the one holding 01-jdk, 02-android-sdk, ...).

param(
    [string]$Root = $PSScriptRoot,
    [string]$AndroidHome = "C:\Android",
    [string]$ProjectDir = "",
    [switch]$SkipEmulator
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host "    $m" -ForegroundColor Yellow }
function Need($p) {
    $f = Get-ChildItem $p -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $f) { Warn "not found: $p" }
    return $f
}

$sdk = Join-Path $AndroidHome "Sdk"
New-Item -ItemType Directory -Force $AndroidHome, $sdk | Out-Null

# --- helper: extract a zip whose single top folder must end up somewhere specific --------
function Expand-Renamed($zip, $targetDir, $expectedInner) {
    $tmp = Join-Path $env:TEMP ("x_" + [guid]::NewGuid().ToString("N"))
    Expand-Archive -Path $zip -DestinationPath $tmp -Force

    $inner = if ($expectedInner -and (Test-Path (Join-Path $tmp $expectedInner))) {
        Join-Path $tmp $expectedInner
    } else {
        (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName
    }

    if (Test-Path $targetDir) {
        try {
            Remove-Item -Recurse -Force $targetDir -ErrorAction Stop
        } catch {
            # Usually a Gradle daemon holding jvm.dll from a previous install.
            Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
            throw ("Cannot replace $targetDir - a file is in use. " +
                   "Run '.\gradlew.bat --stop' and close any terminal using it, then re-run.")
        }
    }
    New-Item -ItemType Directory -Force (Split-Path $targetDir) | Out-Null
    Move-Item $inner $targetDir
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

# --- 1. JDK ------------------------------------------------------------------------------
Step "JDK 17"
$jdkZip = Need "$Root\01-jdk\*.zip"
if ($jdkZip) { Expand-Renamed $jdkZip.FullName (Join-Path $AndroidHome "jdk17") $null }

# --- 2. Android SDK ----------------------------------------------------------------------
Step "Android SDK command-line tools"
$cli = Need "$Root\02-android-sdk\commandlinetools-win*.zip"
if ($cli) { Expand-Renamed $cli.FullName (Join-Path $sdk "cmdline-tools\latest") "cmdline-tools" }

# These zips each contain one folder whose name does not match where the SDK expects it.
# platform-35 and build-tools both unpack as "android-15", which is why they are renamed
# explicitly rather than by guessing.
$layout = @(
    @{ match = "platform-tools_*";        target = "$sdk\platform-tools";              inner = "platform-tools" }
    @{ match = "platforms_android-35__*"; target = "$sdk\platforms\android-35";        inner = $null }
    @{ match = "build-tools_35*";         target = "$sdk\build-tools\35.0.0";          inner = $null }
    # AGP requires 34.0.0 as well as 35.0.0; without it the build fails outright.
    @{ match = "build-tools_34*";         target = "$sdk\build-tools\34.0.0";          inner = $null }
)
foreach ($l in $layout) {
    $z = Get-ChildItem "$Root\02-android-sdk\$($l.match)" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $z) { Warn "missing $($l.match)"; continue }
    Step "  $(Split-Path $l.target -Leaf)"
    Expand-Renamed $z.FullName $l.target $l.inner
}

if (-not $SkipEmulator) {
    $emu = Get-ChildItem "$Root\02-android-sdk\emulator__*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($emu) { Step "  emulator"; Expand-Renamed $emu.FullName "$sdk\emulator" "emulator" }

    foreach ($img in Get-ChildItem "$Root\02-android-sdk\system-images_*" -ErrorAction SilentlyContinue) {
        # system-images_android-26_google_apis_x86_64__x86_64-26_r08.zip
        if ($img.Name -match "system-images_(android-\d+)_([a-z_]+)_([a-z0-9_]+)__") {
            $target = "$sdk\system-images\$($Matches[1])\$($Matches[2])\$($Matches[3])"
            Step "  system image $($Matches[1]) $($Matches[3])"
            Expand-Renamed $img.FullName $target $null
        }
    }
}

# --- 3. SDK licences ---------------------------------------------------------------------
# Written directly because `sdkmanager --licenses` is interactive and would block here.
Step "SDK licences"
$lic = Join-Path $sdk "licenses"
New-Item -ItemType Directory -Force $lic | Out-Null
@{
    "android-sdk-license"         = "24333f8a63b6825ea9c5514f83c2829b004d1fee`n8933bad161af4178b1185d1a37fbf41ea5269c55`nd56f5187479451eabf01fb78af6dfcb131a6481e"
    "android-sdk-preview-license" = "84831b9409646a918e30573bab4c9c91346d8abd"
    "android-sdk-arm-dbt-license" = "859f317696f67ef3d7f30a50a5560e7834b43903"
    "google-gdk-license"          = "33b6a2b64607f11b759f320ef9dff4ae5c47d97a"
    "intel-android-extra-license" = "d975f751698a77b662f1254ddbeed3901e976f5a"
    "android-googletv-license"    = "601085b94cd77f0b54ff86406957099ebe79c4d6"
}.GetEnumerator() | ForEach-Object { [IO.File]::WriteAllText("$lic\$($_.Key)", $_.Value) }

# --- 4. Gradle ---------------------------------------------------------------------------
Step "Gradle"
$gz = Need "$Root\03-gradle\gradle-*-bin.zip"
if ($gz) {
    Expand-Renamed $gz.FullName (Join-Path $AndroidHome "gradle") $null
    # The wrapper looks for the distribution here; seeding it avoids a download on first build.
    $dists = "$env:USERPROFILE\.gradle\wrapper\dists"
    New-Item -ItemType Directory -Force $dists | Out-Null
}

Step "Gradle dependency cache"
if (Test-Path "$Root\08-gradle-cache\modules-2") {
    $caches = "$env:USERPROFILE\.gradle\caches"
    New-Item -ItemType Directory -Force $caches | Out-Null
    robocopy "$Root\08-gradle-cache\modules-2" "$caches\modules-2" /E /NFL /NDL /NJH /NJS /NP | Out-Null
} else {
    # ASCII only inside string literals: PowerShell 5.1 reads .ps1 as ANSI, and a UTF-8
    # em-dash decodes its third byte as a smart quote, which terminates the string.
    Warn "no dependency cache - the first build will need internet"
}

# --- 5. Python ---------------------------------------------------------------------------
Step "Python (embeddable) and packages"
$pz = Need "$Root\04-python\python-*-embed-amd64.zip"
if ($pz) {
    $py = Join-Path $AndroidHome "python312"
    if (Test-Path $py) { Remove-Item -Recurse -Force $py }
    Expand-Archive -Path $pz.FullName -DestinationPath $py -Force

    # The embeddable build disables site-packages; pip needs it back on.
    $pth = Get-ChildItem "$py\python*._pth" | Select-Object -First 1
    (Get-Content $pth.FullName) -replace '^#\s*import site', 'import site' |
        Set-Content $pth.FullName -Encoding ascii

    # No 2>&1 on these. PowerShell 5.1 wraps a native command's stderr in an ErrorRecord and
    # fails the script even on exit code 0 - pip's "not on PATH" notice is enough to do it.
    if (Test-Path "$Root\04-python\get-pip.py") {
        & "$py\python.exe" "$Root\04-python\get-pip.py" --no-warn-script-location | Out-Null
    }
    if (Test-Path "$Root\04-python\wheels") {
        Step "  installing wheels from disk (no index)"
        & "$py\python.exe" -m pip install --no-index --no-warn-script-location `
            --find-links "$Root\04-python\wheels" `
            torch transformers onnx onnxruntime onnxscript msvc-runtime pypinyin | Out-Null
        Step "  packages installed"
    }
}

# --- 6. espeak-ng and git ----------------------------------------------------------------
Step "espeak-ng"
$msi = Need "$Root\06-espeak\espeak-ng.msi"
if ($msi) {
    # /a is an administrative install: it extracts without needing admin rights.
    $p = Start-Process msiexec.exe -ArgumentList "/a", "`"$($msi.FullName)`"", "/qn",
        "TARGETDIR=$AndroidHome\espeak" -Wait -PassThru
    if ($p.ExitCode -ne 0) { Warn "msiexec returned $($p.ExitCode)" }
}

Step "portable git"
$gitZip = Need "$Root\05-git\MinGit*.zip"
if ($gitZip) {
    $g = Join-Path $AndroidHome "git"
    if (Test-Path $g) { Remove-Item -Recurse -Force $g }
    Expand-Archive -Path $gitZip.FullName -DestinationPath $g -Force
}

# --- 7. project --------------------------------------------------------------------------
if ($ProjectDir) {
    Step "Project assets -> $ProjectDir"
    $assets = Join-Path $ProjectDir "app\src\main\assets"
    New-Item -ItemType Directory -Force $assets | Out-Null
    Get-ChildItem "$Root\09-app-assets\*" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne "WHY-THIS-IS-COPIED.txt" } |
        ForEach-Object { Copy-Item $_.FullName $assets -Force }

    Set-Content -Path (Join-Path $ProjectDir "local.properties") `
        -Value ("sdk.dir=" + $sdk.Replace("\", "\\").Replace(":", "\:")) -Encoding ascii
}

# --- 8. environment ----------------------------------------------------------------------
Step "Environment variables (user scope)"
[Environment]::SetEnvironmentVariable("JAVA_HOME", "$AndroidHome\jdk17", "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
[Environment]::SetEnvironmentVariable("ESPEAK_DATA_PATH", "$AndroidHome\espeak\eSpeak NG\espeak-ng-data", "User")
$add = @("$AndroidHome\jdk17\bin", "$sdk\platform-tools", "$sdk\emulator")
$cur = [Environment]::GetEnvironmentVariable("Path", "User"); if ($null -eq $cur) { $cur = "" }
$parts = @($cur -split ';' | Where-Object { $_ -ne '' })
foreach ($p in $add) { if ($parts -notcontains $p) { $parts += $p } }
[Environment]::SetEnvironmentVariable("Path", ($parts -join ';'), "User")

Write-Host ""
Write-Host "Installed. Open a NEW terminal (the PATH change only applies to new ones), then:" -ForegroundColor Green
Write-Host "  cd <project>"
Write-Host "  .\gradlew.bat assembleDebug --offline"
Write-Host ""
if (-not $SkipEmulator) {
    Write-Host "The emulator needs one admin step and a reboot before it will run:" -ForegroundColor Yellow
    Write-Host "  dism /online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart"
    Write-Host "Then create an AVD and launch:"
    Write-Host '  avdmanager create avd -n android8 -k "system-images;android-26;google_apis;x86_64" -d pixel_2'
    Write-Host "  .\tools\run-emulator.ps1 -Avd android8"
}

$global:LASTEXITCODE = 0
exit 0
