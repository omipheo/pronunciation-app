# Fully offline development

This project builds and runs with **no network access**. Verified: a clean rebuild with
`--offline` succeeds and produces all three APKs, and the app itself has no `INTERNET`
permission so it cannot reach the network even if it wanted to.

But getting there needs one online session. This document lists exactly what to fetch.

## The trap: you cannot do this by downloading files alone

Everyone plans for the JDK and the SDK. The one that breaks offline builds is **Gradle's
dependency cache**. At build time Gradle fetches AndroidX, Material and ONNX Runtime from Maven
Central and Google's repo — about 249 MB of jars and AARs. There is no zip of that anywhere.

The only way to populate it is to **run one successful build while online**, then copy
`~/.gradle/caches/`. Plan your online session around that, not just around downloads.

Same applies to Python: use `pip download` to a folder, not `pip install`, if you want to move
packages to an air-gapped machine.

## What is needed, by tier

Pick the lowest tier that covers what you do. Sizes are measured, not estimated.

### Tier 1 — build the app and install it on a phone (1.2 GB)

| Component | Size | Destination |
|---|---|---|
| Temurin JDK 17 (zip) | 303 MB | `C:\Android\jdk17` |
| Android SDK platform-tools | 17 MB | `C:\Android\Sdk\platform-tools` |
| Android SDK platform 35 | 98 MB | `C:\Android\Sdk\platforms` |
| Android SDK build-tools 35 | 275 MB | `C:\Android\Sdk\build-tools` |
| Android SDK licence files | <1 MB | `C:\Android\Sdk\licenses` |
| Gradle 8.9 distribution | 144 MB | `~\.gradle\wrapper\dists` |
| **Gradle dependency cache** | **249 MB** | `~\.gradle\caches\modules-2` |
| Generated model assets | 119 MB | `app\src\main\assets` |

Copy `modules-2` specifically, not all of `~\.gradle\caches`. The siblings
(`build-cache-1`, `transforms-*`, `jars-*`) add roughly 700 MB, are regenerated on first build,
and the Gradle daemon holds locks on some of them so a naive copy fails partway.

Python is **not** needed here. The assets are the *output* of the Python step; once generated
they are just files.

### Tier 2 — also regenerate the model offline (+1.3 GB)

| Component | Size | Destination |
|---|---|---|
| Python 3.12 embeddable + packages | 912 MB | `C:\Android\python312` |
| HuggingFace model cache | 360 MB | `~\.cache\huggingface` |
| CMUdict | 4 MB | `tools\.cache` |

Only needed if you want to swap models or change the quantisation. If you are just building the
app, skip it.

### Tier 3 — also run the emulator (+4.5 GB per API level)

| Component | Size | Destination |
|---|---|---|
| Emulator binaries | 1,033 MB | `C:\Android\Sdk\emulator` |
| System image, one API level | ~3,400 MB | `C:\Android\Sdk\system-images` |

Do **not** copy `~\.android\avd` (9.4 GB here). Those are disposable instances — recreate them
with `avdmanager` in seconds. Copy the system images only.

Emulator acceleration also needs Windows Hypervisor Platform, which is an **admin action and a
reboot**, not a download:

```powershell
dism /online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart
```

### Optional

| Component | Size | Why |
|---|---|---|
| MinGit (portable) | 90 MB | version control; nothing else needs it |
| SDK cmdline-tools | 146 MB | only to install more SDK packages later |

## Moving it: use the bundle script

On the machine that already works, run:

```powershell
.\tools\make-offline-bundle.ps1 -Destination D:\offline-bundle -Tier 2
```

That copies everything for the chosen tier into one folder with a restore script. Zip it, carry
it, unzip on the target, run `restore.ps1`.

## Fresh downloads, if you are starting from nothing

Direct links, no installers, no admin:

| What | URL |
|---|---|
| JDK 17 | `https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse` |
| SDK cmdline-tools | `https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip` |
| Gradle 8.9 | `https://services.gradle.org/distributions/gradle-8.9-bin.zip` |
| Python 3.12 embeddable | `https://www.python.org/ftp/python/3.12.7/python-3.12.7-embed-amd64.zip` |
| pip bootstrap | `https://bootstrap.pypa.io/get-pip.py` |
| CMUdict | `https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict` |
| MinGit | github.com/git-for-windows/git → releases → `MinGit-*-64-bit.zip` |

SDK packages come through `sdkmanager`, which needs the network:

```powershell
$env:JAVA_HOME="C:\Android\jdk17"
C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=C:\Android\Sdk `
  "platform-tools" "platforms;android-35" "build-tools;35.0.0" `
  "emulator" "system-images;android-26;google_apis;x86_64"
```

Python packages, downloaded rather than installed so they can be moved:

```powershell
C:\Android\python312\python.exe -m pip download -d wheels `
  torch --index-url https://download.pytorch.org/whl/cpu
C:\Android\python312\python.exe -m pip download -d wheels `
  transformers onnx onnxruntime onnxscript msvc-runtime
```

Install them on the offline machine with:

```powershell
C:\Android\python312\python.exe -m pip install --no-index --find-links wheels `
  torch transformers onnx onnxruntime onnxscript msvc-runtime
```

`msvc-runtime` is not optional on Windows — PyTorch needs the MSVC DLLs, which otherwise
require an elevated redistributable installer.

Then, still online, **run one build** to populate the Gradle cache:

```powershell
.\gradlew.bat assembleDebug
```

Only after that is the machine genuinely ready to go offline.

## Working offline day to day

Force Gradle to fail loudly rather than silently waiting on the network:

```powershell
.\gradlew.bat assembleDebug --offline
```

Or make it permanent in `gradle.properties`:

```properties
org.gradle.offline=true
```

Verify the whole thing with a clean rebuild:

```powershell
.\gradlew.bat clean --offline
.\gradlew.bat assembleDebug test --offline
```

If that passes, you are genuinely independent of the network.

## What the app itself needs at runtime

Nothing. The merged manifest declares only `RECORD_AUDIO`; there is no `INTERNET` permission,
so speech recognition, the lexicon and scoring all run on-device from bundled assets.

The one runtime dependency outside the APK is **text-to-speech voice data**, used by Section 1
and the Listen buttons. Install it on the device once:

**Settings → System → Languages & input → Text-to-speech output → Install voice data → English (US)**

After that it works offline. Where TTS is unavailable the app shows a warning rather than
failing silently.
