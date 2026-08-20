# Offline install

Everything needed to build and run this project on a machine with **no internet**.

No Android Studio. The toolchain is a JDK zip, the Android SDK command-line tools and Gradle,
which is all a build actually requires — and it avoids an 8 GB interactive installer.

## Install

Copy this whole folder to the offline machine, open PowerShell in it, and run:

```powershell
.\offline-install.ps1 -ProjectDir C:\pronunciation-app
```

No administrator rights are needed. It unpacks everything to `C:\Android`, seeds the Gradle
cache, installs the Python packages from disk, and sets `JAVA_HOME`, `ANDROID_HOME` and `PATH`
at user scope.

Then **open a new terminal** — the PATH change only applies to new ones:

```powershell
cd C:\pronunciation-app
.\gradlew.bat assembleDebug --offline
```

`--offline` makes Gradle fail loudly rather than silently waiting on a network that isn't there.

## What's in here

| Folder | Contents |
|---|---|
| `01-jdk` | Temurin JDK 17, zip |
| `02-android-sdk` | command-line tools, platform 35, build-tools 35, platform-tools, emulator, system images |
| `03-gradle` | Gradle 8.9 |
| `04-python` | Python 3.12 embeddable, `get-pip.py`, and every wheel (`torch` CPU, `transformers`, `onnx`, `onnxruntime`, `onnxscript`, `msvc-runtime`, `pypinyin`) |
| `05-git` | MinGit, portable |
| `06-espeak` | espeak-ng MSI — extracted with `msiexec /a`, which needs no admin |
| `07-data` | CMUdict and the English word-frequency list |
| `08-gradle-cache` | **copied, not downloaded** — see below |
| `09-app-assets` | **copied, not downloaded** — see below |
| `10-hf-models` | HuggingFace weights, only needed to regenerate the models |

## Two things that cannot be downloaded

**`08-gradle-cache`** — Gradle resolves AndroidX, Material and ONNX Runtime from Maven *at build
time*. No published bundle of that dependency set exists. The only way to obtain it is to run
one successful build while online and copy `~/.gradle/caches/modules-2`. That is what this
folder is, and without it the first build would need the network.

**`09-app-assets`** — the speech models and lexicons are *generated*, not downloaded. You could
rebuild them offline from `10-hf-models` plus espeak and the wheels, but the finished assets are
459 MB against 2.8 GB of raw weights, and rebuilding takes about twenty minutes.

## Two things that are not downloads at all

**Emulator acceleration** needs one admin command and a reboot:

```powershell
dism /online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart
```

Then create an AVD — instances are not shipped, only the system images:

```powershell
avdmanager create avd -n android8 -k "system-images;android-26;google_apis;x86_64" -d pixel_2
.\tools\run-emulator.ps1 -Avd android8
```

**Text-to-speech voice data** is installed on the device, not here: Settings → System →
Languages & input → Text-to-speech output. English for the English workflow, Chinese for the
Mandarin one. The app shows a warning where a voice is missing rather than failing silently.

## Regenerating the models offline

Only needed if you want to change or swap a model. The weights and word lists are all present:

```powershell
C:\Android\python312\python.exe tools\prepare_assets.py --lang en
C:\Android\python312\python.exe tools\prepare_assets.py --lang zh
C:\Android\python312\python.exe tools\build_lexicon_zh.py
C:\Android\python312\python.exe tools\generate_content.py
C:\Android\python312\python.exe tools\generate_content_zh.py
```

Copy `07-data\cmudict.dict` and `07-data\google-10000-english.txt` into `tools\.cache\` first,
or the generators will try to fetch them.

## Verifying you are genuinely offline

```powershell
.\gradlew.bat clean --offline
.\gradlew.bat assembleDebug test --offline
```

If that passes with the network disconnected, nothing here depends on it. The app itself never
did: the merged manifest declares no `INTERNET` permission, so it cannot open a connection even
if it tried.
