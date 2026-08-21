# Pronunciation

An offline Android app for practising pronunciation, in **English or Mandarin**. Java + XML
Views, minSdk 26 (Android 8.0), targetSdk 35 (Android 15).

Nothing leaves the device. The merged manifest declares no `INTERNET` permission, so the app
cannot open a network connection even if it tried — the speech models run on the phone.

---

## 1. Install the development environment

Three routes. Pick one.

### A — from the offline bundle (no internet needed)

Every archive is at
[pronunciation-app-offline-bundle](https://github.com/omipheo/pronunciation-app-offline-bundle).
Download all volumes into one folder, then:

```powershell
7z x pronunciation-offline.part001.rar     # or open the first volume in WinRAR
cd pronunciation-offline
.\offline-install.ps1 -ProjectDir C:\pronunciation-app
```

That unpacks the JDK, Android SDK, Gradle and its dependency cache, Python with every wheel,
and espeak-ng into `C:\Android`, then sets `JAVA_HOME`, `ANDROID_HOME` and `PATH` at user
scope. **No administrator rights, no Android Studio.**

If a folder is missing from the bundle, re-fetch it on any machine with internet:

```powershell
python tools/fetch_offline_sources.py C:\pronunciation-offline
```

### B — portable toolchain, from the internet (~15 min, no admin)

Same result as A, downloading as it goes:

```powershell
python tools/fetch_offline_sources.py C:\pronunciation-offline
C:\pronunciation-offline\offline-install.ps1 -ProjectDir C:\pronunciation-app
```

### C — Android Studio

Install it from <https://developer.android.com/studio>, open the project folder, and let it
sync. It brings its own JDK and SDK. This is the heaviest option (~8 GB) and the build needs
none of the IDE, but it is the familiar one.

**Open a new terminal afterwards.** `PATH` changes only apply to terminals started after them.

---

## 2. Generate the speech assets

The models and lexicons are **not in git** — they are 459 MB, past GitHub's file limit. The app
builds and runs without them, but Training and Game show a banner instead of scoring.

If you installed from the offline bundle they are already in place. Otherwise:

```powershell
# English: model, CMUdict lexicon              (~15 min, ~2 GB download)
python tools/prepare_assets.py

# Mandarin: multilingual model, hanzi lexicon  (~25 min, ~3 GB download)
python tools/prepare_assets.py --lang zh
python tools/build_lexicon_zh.py

# Practice content for both
python tools/generate_content.py
python tools/generate_content_zh.py
```

Then check nothing is silently broken:

```powershell
python tools/check_content.py
```

---

## 3. Build

```powershell
.\gradlew.bat assembleDebug          # gradlew on macOS/Linux
```

Add `--offline` to make Gradle fail loudly rather than sit waiting on a network that is not
there:

```powershell
.\gradlew.bat assembleDebug --offline
```

Run the tests:

```powershell
.\gradlew.bat test
```

First build takes a few minutes; incremental builds are ~30 seconds.

---

## 4. Run

### On an emulator

```powershell
.\tools\run-emulator.ps1 -Avd android8     # Android 8.0 - the minSdk floor
.\tools\run-emulator.ps1                   # Android 15
```

The script checks acceleration, boots the AVD, waits for Android, installs the **x86_64** APK
and launches the app.

Acceleration needs one administrator command and a **reboot**, once per machine:

```powershell
dism /online /Enable-Feature /FeatureName:HypervisorPlatform /All /NoRestart
```

If no AVD exists yet:

```powershell
avdmanager create avd -n android8 -k "system-images;android-26;google_apis;x86_64" -d pixel_2
```

**Turn the microphone on** before trying Training or Game: emulator toolbar `...` →
**Microphone** → *"Virtual microphone uses host audio input"*. It is off by default, so
recordings capture silence and everything scores 0% — which looks like a broken model.

### On a phone

```powershell
adb devices                # want a line ending in "device"
.\gradlew.bat installDebug
```

Needs **Developer options → USB debugging** on, and a **data** cable — many USB cables carry
power only, and Windows then shows no device at all.

Either way, install the text-to-speech voice data on the device or the Listen buttons stay
silent: **Settings → System → Languages & input → Text-to-speech output**. English for the
English workflow, Chinese for Mandarin.

---

## 5. Get the APK

After `assembleDebug`, three APKs are in `app/build/outputs/apk/debug/`:

| File | Size | Use |
|---|---|---|
| `app-arm64-v8a-debug.apk` | ~484 MB | **every real phone** |
| `app-x86_64-debug.apk` | ~486 MB | emulators |
| `app-universal-debug.apk` | ~536 MB | when the target is unknown |

They are large because both speech models ship inside: 116 MB English plus 339 MB Mandarin.
ONNX Runtime also carries a ~17 MB native library per architecture, which is why the build
splits by ABI rather than shipping one universal APK.

**To sideload**, copy the arm64 file to the phone by any means — cloud drive, USB storage,
email — tap it, and allow *"install from unknown sources"*. It is signed with the debug
keystore: fine for testing, not publishable.

To build a smaller APK for one language, delete the other model from
`app/src/main/assets/` before building. English alone gives roughly 145 MB.

---

## The four sections

**0. Main** — where practice stands: attempts, average score, day streak, and the sounds you get
wrong most often, ranked by error rate and labelled with an example word ("θ as in think"). Also
where you switch language, and the way into everything else.

Tapping a weak sound opens Training filtered to just the prompts whose expected pronunciation
contains it, easiest unit first — so the dashboard is actionable rather than only informative.

**1. Alphabet / Pinyin** — A–Z in English, with each letter's name in IPA, the sound(s) it makes
and an example word. In Chinese it becomes the pinyin table instead, since Chinese has no
alphabet: initials, finals and the four tones, each with a character that carries the sound.

**2. Training** — pick a unit (word, sentence, paragraph), read the prompt aloud, and get a
per-phoneme breakdown: which sounds were right, which came out as something else, and which were
dropped. Chinese prompts show hanzi with pinyin underneath.

**3. Game** — ten passages, each harder than the last, drawn at random from 320 problems banded
into ten difficulty levels. Read one sentence at a time; a fish swims above the passage and moves
only when you pronounce a sentence well enough. Press Record and it sets off along the sentence —
finish before it reaches the end or the run is over.

---

## How the scoring works

The interesting design decision is *not* using a speech-to-text model.

Whisper and friends transcribe what you **meant**. Say "sink" when the prompt reads "think" and
they will happily return "think" — the language model corrects you, and the app reports success
on a mispronunciation. That is the exact failure this app exists to catch.

So the pipeline recognises phonemes instead of words:

```
mic ─► 16 kHz mono PCM ─► wav2vec2 CTC (ONNX, int8) ─► IPA the user actually produced
                                                              │
prompt text ─► lexicon ─────► IPA the prompt requires ────────┤
                                                              ▼
                                         Levenshtein alignment ─► per-phoneme verdict
```

Alignment runs over the whole utterance rather than word by word, so a sound dropped at a word
boundary is charged to one word instead of wrecking both. Stress and length marks are stripped
before comparison — this scores sounds, not prosody. Words missing from the dictionary are shown
greyed out and excluded from the total rather than counted as errors.

Typical cost on a mid-range phone: 200–600 ms for a short utterance. Scoring is deliberately per
sentence: inference is O(n²) in audio length, and a 60-second recording takes 27 seconds to
process where a single sentence takes under one.

### Verified behaviour

The English model was checked end-to-end against speech synthesised by Windows TTS at 16 kHz —
the same path the app uses, minus the microphone:

| Prompt | Expected | Recognised | Score |
|---|---|---|---|
| think | `θ ɪ ŋ k` | `θ ɪ ŋ k` | 100% |
| three | `θ ɹ i` | `θ ɹ i` | 100% |
| world | `w ɚ l d` | `w ɚ l d` | 100% |
| the three thin thieves left | `ð ə θ ɹ i θ ɪ n θ i v z l ɛ f t` | identical | 100% |

Clean synthesised speech is the easy case, so this proves the pipeline is wired correctly — not
that scoring is calibrated for real learners. Only a person at a microphone can tell you that.

One trap this caught: **quantising the whole model silently breaks it.** Full int8 scored 0% on
single words — the conv feature encoder cannot survive weight quantisation, and the failure mode
is an empty phoneme list rather than an error. `tools/prepare_assets.py` therefore quantises
MatMul ops only. See [SETUP.md](SETUP.md) for the numbers.

### Mandarin: what is and is not scored

Initials, finals and vowels are scored. **Tone is shown but never scored** — the bundled
multilingual model's vocabulary has no third tone at all and covers the others unevenly, so
comparing tone would mark every third-tone syllable wrong however well it was said. The Main tab
says so where the language is chosen.

Phonemisation goes through espeak-ng, which is not arbitrary: the Chinese model is
`facebook/wav2vec2-lv-60-espeak-cv-ft`, trained on espeak output, so both sides use one alphabet
with no hand-written mapping table to get wrong.

---

## Layout

```
app/src/main/java/com/example/pronunciation/
  speech/
    OnnxPhonemeRecognizer   ONNX Runtime session + CTC greedy decode
    Lexicon                 word (or hanzi) -> expected IPA
    PronunciationScorer     alignment and per-phoneme verdicts
    SpeechEngine            one model resident at a time; swaps on language change
  audio/
    AudioRecorder           16 kHz capture via VOICE_RECOGNITION source
    TtsSpeaker              system TTS wrapper, per-language voice
  ui/
    RecordingFragment       shared mic permission + recorder plumbing
    home/                   Main tab - dashboard and language switch
    alphabet/               A-Z grid, or the pinyin table in Chinese
    training/               per-phoneme practice
    game/                   passages, the chase fish, PassageTextView
  data/
    Language                per-language assets, tokenisation and tone rules
    Lessons, LessonRepository, PracticeSession
    GameProblem, GameProblems, GameScoring
    PracticeStats           attempt counters, streak, per-phoneme error rates
    Phonemes, Syllables     IPA -> example word; the pinyin table
tools/
  prepare_assets.py         model export (--lang en|zh)
  build_lexicon_zh.py       hanzi -> IPA via espeak
  generate_content*.py      practice corpora
  check_content.py          content invariants
  fetch_offline_sources.py  download every installer for an offline setup
  offline-install.ps1       install them, no admin
  run-emulator.ps1          boot, install, launch
```

---

## Tests

```powershell
.\gradlew.bat test
```

46 tests. `PronunciationScorerTest` covers the alignment logic — substitutions, deletions,
insertions, cross-word boundaries, unknown words and silence. `GameScoringTest` guards the rule
that the game only advances on an accurate reading, because nothing in the suite can speak into
a microphone.

---

## Content

**English: 2280 prompts** — 1285 words, 637 sentences, 358 paragraphs, plus 320 game passages
across ten difficulty levels.
**Mandarin: 502 prompts** and 320 game passages, plus a 58-row initials/finals/tones table.

The 130 English prompts in `data/Lessons.java` are hand-written and lean on the contrasts
non-native speakers actually lose — θ/ð, l/ɹ, v/w, ʃ/tʃ/ʒ, ɪ/iː, æ/ʌ and the unstressed schwa.
The rest are generated into `assets/*.tsv`, which is far too much for Java source.

A learner never sees the flat list. Opening Training draws a fresh **10 words, 8 sentences and 3
paragraphs** at random; "1 / 1285" would be discouraging and nobody works through a corpus in
order.

Two invariants are enforced rather than assumed, across every source:

- **Every prompt word must be in the lexicon.** An unknown word is greyed out and excluded from
  scoring, so a typo silently produces a prompt that teaches nothing. This caught "practising" —
  CMUdict is US English and has only "practicing".
- **Every phoneme must appear in at least 3 prompts.** Otherwise the Main tab can name your
  weakest sound and then have nothing to practise it with. This caught ɔɪ sitting at one prompt.

`tools/check_content.py` exits non-zero on failure, so it can gate a commit.
