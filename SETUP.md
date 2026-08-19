# Setup

Your machine currently has none of the Android toolchain installed. This is the full
zero-to-running path. Steps 1–3 need internet; after that everything is local.

## 1. Android Studio (~8 GB, one time)

Download from <https://developer.android.com/studio> and install with the defaults. The
installer bundles:

- **JDK 17** (as JetBrains Runtime) — you do not need a separate Java install
- **Android SDK** + platform 35 + build tools
- **Gradle**
- **adb** and the emulator

Nothing else on this list needs installing separately.

## 2. Open the project

`File → Open` → select `c:\Users\Work\Documents\pronunciation-app`.

Two prompts to expect on first open:

- **"Gradle wrapper not found"** — this project ships `gradle-wrapper.properties` but not the
  `.jar` (it is a binary). Let Android Studio create the wrapper, or run
  `gradle wrapper` if you have Gradle on the PATH. Either is fine.
- **AGP upgrade suggestion** — safe to accept. The project targets AGP 8.7.3 / Gradle 8.9.

Then `Build → Make Project`. The first Gradle sync downloads dependencies (a few minutes).

At this point **the app builds and runs**, and Section 1 is fully functional. Sections 2 and 3
show a banner saying the model is missing — that is expected until step 4.

## 3. Getting it onto a device

### Sideloading (no adb, no admin)

The build produces three APKs in `app/build/outputs/apk/debug/`:

| APK | Size | Use |
|---|---|---|
| `app-arm64-v8a-debug.apk` | 143 MB | **every current phone** — use this one |
| `app-x86_64-debug.apk` | 146 MB | emulators only |
| `app-universal-debug.apk` | 196 MB | when you don't know the target |

Transfer the arm64 one to the phone (cloud drive, USB storage, email to yourself), tap it, and
allow "install from unknown sources" when prompted. It is signed with the debug keystore, which
is fine for testing but cannot be published.

### adb over USB

Needs USB debugging on: **Settings → About phone → tap "Build number" 7 times**, then
**Developer options → USB debugging → ON**. Plug in, set the USB mode to **File transfer**
(not "Charging only"), and accept the "Allow USB debugging?" prompt.

```powershell
C:\Android\Sdk\platform-tools\adb.exe devices   # want a line ending in "device"
.\gradlew.bat installDebug
```

If Windows shows no device at all, it is almost always a **charge-only USB cable** — many
cables carry power but no data lines.

### adb over Wi-Fi (Android 11+)

Avoids cable problems entirely. **Developer options → Wireless debugging → Pair device with
pairing code**, then:

```powershell
C:\Android\Sdk\platform-tools\adb.exe pair <ip>:<pair-port>   # enter the 6-digit code
C:\Android\Sdk\platform-tools\adb.exe connect <ip>:<port>     # port from the main screen
```

### Emulator

Two AVDs are set up, both with host-microphone passthrough:

```powershell
.\tools\run-emulator.ps1 -Avd android8    # Android 8.0 (API 26) - the minSdk floor
.\tools\run-emulator.ps1                  # Android 15 (API 35)
```

The script checks acceleration, boots the AVD, waits for Android, installs the **x86_64** APK
(the arm64 one will not install on an emulator) and launches the app.

**Test on `android8` before shipping.** It is the oldest release the app supports, and where
compatibility problems actually surface — lint checks your code but cannot tell you how ONNX
Runtime's native library behaves on an eight-year-old Android.

Acceleration required a one-time admin step, already done on this machine:

```powershell
# ADMIN PowerShell, then REBOOT:
dism /online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart
```

Verify with `emulator -accel-check` — exit code 0 and "WHPX is installed and usable". Without
it the emulator interprets x86 instructions and is far too slow to use.

Two caveats:

- **Enable the mic manually**: emulator toolbar → `...` → Microphone → *"Virtual microphone
  uses host audio input"*. Off by default, so recordings capture silence.
- **`SystemServer` crashes in logcat on the API 26 image are not your app.** The stock image
  has a `NetworkPolicyManagerService` NPE during boot. Filter by process to avoid chasing it.

A physical phone is still the better final check: emulator microphone passthrough is not a
faithful test of pronunciation scoring.

## 3b. Emulator setup, from scratch

Either works:

- **Physical phone** (recommended) — enable Developer Options → USB debugging, plug in via USB.
  The emulator's simulated microphone makes pronunciation scoring useless for real testing.
- **Emulator** — `Tools → Device Manager → Create Device`. In the AVD's advanced settings set
  **Microphone → Host audio input** or recording will return silence.

## 4. Generate the pronunciation model

This is what makes Sections 2 and 3 work. It runs on the desktop, once.

```powershell
# Real Python, not the Microsoft Store stub currently on your PATH.
# Install from https://www.python.org/downloads/ (3.9+), then:
pip install torch transformers onnx onnxruntime
python tools/prepare_assets.py
```

It downloads `bookbot/wav2vec2-ljspeech-gruut`, exports it to ONNX, quantises it, and writes
four files into `app/src/main/assets/`. Expect 10–20 minutes and about 2 GB of temporary
download.

Watch the output for this line:

```
All mapped IPA symbols exist in the model vocabulary.
```

If it instead warns about missing symbols, the model you chose uses a different phoneme
inventory than CMUdict maps onto, and every score will come out near zero. Fix the
`ARPABET_TO_IPA` table in `tools/prepare_assets.py` against the printed `vocab.json` before
going further.

Rebuild the app afterwards. The banner disappears.

### Do not quantise the conv encoder

The script quantises **MatMul ops only**, leaving the convolutional feature encoder in fp32.
This is not a stylistic choice. Measured on synthesised speech, quantising everything gives:

| Build | Size | Single words | Sentence |
|---|---|---|---|
| fp32 | 378 MB | 100% | 100% |
| int8, everything | 91 MB | **0%** | 75% |
| int8, MatMul only | 122 MB | 100% | 100% |

Full int8 does not merely degrade — short utterances decode to *nothing at all*, because the
conv feature encoder cannot survive weight quantisation. The failure is silent: the model
loads, runs, and returns an empty phoneme list, which the app reports as a 0% score. If you
change the quantisation settings, re-run the verification before trusting any score.

## 5. Text-to-speech voice data

Section 1 and the "Listen" buttons use Android's built-in TTS. On the device:

`Settings → System → Languages & input → Text-to-speech output → Google TTS → Install voice data → English (US)`

Once installed this works offline. The app shows a warning where TTS is unavailable rather
than silently doing nothing.

## Offline from here

After steps 1–4, nothing in the build or the app needs a network connection. If you want to
guarantee that, enable Gradle offline mode: `File → Settings → Build → Gradle → Offline work`.

## Swapping the model later

`OnnxPhonemeRecognizer` reads the blank id, input tensor name and normalisation flag out of
`model_config.json`, so a different CTC phoneme model only needs:

```powershell
python tools/prepare_assets.py --model <other/model-id>
```

Any model whose output is `[1, frames, vocab]` CTC logits over IPA symbols will work without a
code change. If the new vocabulary differs, update `ARPABET_TO_IPA` to match.

## Troubleshooting

**`OutOfMemoryError` during Gradle sync** — raise the heap in `gradle.properties`:
`org.gradle.jvmargs=-Xmx4096m`.

**Everything scores 0%** — almost always a phoneme inventory mismatch (step 4). Check Logcat
for the `Loaded phoneme_model.onnx (N tokens…)` line, then compare `vocab.json` against the
IPA symbols in `lexicon.txt`.

**Recording returns silence on the emulator** — see step 3, host audio input.

**App size** — the arm64 debug APK is ~143 MB, the universal one ~196 MB. `--no-quantize`
triples the model (378 MB) for no measured accuracy gain; it exists only for comparison.

## Verified on this machine

The build was set up and run headlessly, without Android Studio, using portable toolchains
under `C:\Android\`. If you would rather not install the full IDE, this works:

| Component | Location | Notes |
|---|---|---|
| Temurin JDK 17 | `C:\Android\jdk17` | zip, no installer |
| Gradle 8.9 | `C:\Android\gradle` | only needed once, to create the wrapper |
| Android SDK | `C:\Android\Sdk` | cmdline-tools + platform 35 + build-tools 35 |
| Python 3.12 | `C:\Android\python312` | embeddable zip + `get-pip.py` |

Set `JAVA_HOME` and use the wrapper:

```powershell
$env:JAVA_HOME="C:\Android\jdk17"
.\gradlew.bat assembleDebug test
```

Two Windows-specific snags worth knowing:

- **MSI installers need admin.** A `winget` install that raises a UAC prompt nobody clicks
  holds a global installer lock and makes every later MSI fail with exit code 1618. The
  portable zips above avoid this entirely.
- **PyTorch needs the MSVC runtime**, which is normally an elevated install. `pip install
  msvc-runtime` drops the DLLs into the Python directory instead.

What this setup **cannot** verify: anything involving the microphone, TTS playback, or the
UI on a real device. Those still need a phone.
