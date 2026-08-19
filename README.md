# Pronunciation

An offline Android app for practising English pronunciation. Java + XML Views, minSdk 26.
No network calls at runtime — the speech model runs on the device.

## Quick start

Already have Android Studio (or a JDK 17 + Android SDK)? From the project root:

```bash
# 1. Generate the speech model and lexicon (once, ~15 min, needs internet)
pip install torch transformers onnx onnxruntime onnxscript
python tools/prepare_assets.py

# 2. Build
./gradlew assembleDebug        # gradlew.bat on Windows

# 3. Install on a connected device
./gradlew installDebug
```

APKs land in `app/build/outputs/apk/debug/` — use `app-arm64-v8a-debug.apk` for a phone,
`app-x86_64-debug.apk` for an emulator. To sideload, copy the arm64 one to the phone and tap it.

Run the tests with `./gradlew test`.

Step 1 is optional: the app builds and runs without it, with Sections 2 and 3 showing a banner
instead of faking scores. Section 1 works either way. The model is not committed because it is
116 MB, past GitHub's file limit.

On Windows there is a scripted emulator path:

```powershell
.\tools\run-emulator.ps1 -Avd android8    # Android 8.0, the minSdk floor
.\tools\run-emulator.ps1                  # Android 15
```

For a machine with no toolchain at all, [SETUP.md](SETUP.md) has the full zero-to-running path,
including a portable no-admin setup.

**Testing Sections 2 and 3 needs a working microphone.** On an emulator, enable it explicitly:
toolbar `...` → Microphone → "Virtual microphone uses host audio input". It is off by default,
so recordings capture silence and everything scores 0%.

## The four sections

**0. Main** — where practice stands: attempts, average score, day streak, and the sounds you get
wrong most often, ranked by error rate and labelled with an example word ("θ as in think"). Also
the way into everything else. Stats come from `PracticeStats`, a handful of counters in
`SharedPreferences` that Training and Game fold each scored attempt into.

**1. Alphabet** — an A–Z grid. Tapping a letter opens its name in IPA, the sound(s) it makes,
and an example word, each playable through the system TTS at normal or half speed.

**2. Training** — pick a unit (word, sentence, paragraph), read the prompt aloud, and get a
per-phoneme breakdown: which sounds were right, which came out as something else, and which
were dropped entirely.

**3. Game** — five sentences, twenty-five seconds each, running score and a best-score record.
Same scoring engine, no chance to rehearse.

## How the scoring works

The interesting design decision is *not* using a speech-to-text model.

Whisper and friends transcribe what you **meant**. Say "sink" when the prompt reads "think"
and they will happily return "think" — the language model corrects you, and the app reports
success on a mispronunciation. That is the exact failure this app exists to catch.

So the pipeline recognises phonemes instead of words:

```
mic ─► 16 kHz mono PCM ─► wav2vec2 CTC (ONNX, int8) ─► IPA the user actually produced
                                                              │
prompt text ─► CMUdict lexicon ─► IPA the prompt requires ─────┤
                                                              ▼
                                         Levenshtein alignment ─► per-phoneme verdict
```

Alignment runs over the whole utterance rather than word by word, so a sound dropped at a word
boundary is charged to one word instead of wrecking both. Stress and length marks are stripped
before comparison — this scores sounds, not prosody. Words missing from the dictionary are
shown greyed out and excluded from the total rather than counted as errors.

Typical cost on a mid-range phone: 200–600 ms for a short utterance.

### Verified behaviour

The exported model was checked end-to-end against speech synthesised by Windows TTS at
16 kHz — the same path the app uses, minus the microphone:

| Prompt | Expected | Recognised | Score |
|---|---|---|---|
| think | `θ ɪ ŋ k` | `θ ɪ ŋ k` | 100% |
| three | `θ ɹ i` | `θ ɹ i` | 100% |
| world | `w ɚ l d` | `w ɚ l d` | 100% |
| the three thin thieves left | `ð ə θ ɹ i θ ɪ n θ i v z l ɛ f t` | identical | 100% |

Clean synthesised speech is the easy case, so this proves the pipeline is wired correctly —
not that scoring is calibrated for real learners. Only a person at a microphone can tell you
that.

One trap this caught: **quantising the whole model silently breaks it.** Full int8 scored 0%
on single words — the conv feature encoder cannot survive weight quantisation, and the failure
mode is an empty phoneme list rather than an error. `tools/prepare_assets.py` therefore
quantises MatMul ops only. See SETUP.md for the numbers.

## Layout

```
app/src/main/java/com/example/pronunciation/
  speech/          the engine — recogniser, lexicon, scorer, and the facade over them
    OnnxPhonemeRecognizer   ONNX Runtime session + CTC greedy decode
    Lexicon                 word -> expected IPA
    PronunciationScorer     alignment and per-phoneme verdicts
    SpeechEngine            process-wide singleton, worker thread, LiveData state
  audio/
    AudioRecorder           16 kHz capture via VOICE_RECOGNITION source
    TtsSpeaker              system TTS wrapper
  ui/
    RecordingFragment       shared mic permission + recorder plumbing
    home/                   Main tab - progress dashboard
    alphabet/               Section 1
    training/               Section 2
    game/                   Section 3
  data/
    Alphabet, Lessons       A-Z table and practice content
    PracticeStats           attempt counters, streak, per-phoneme error rates
    Phonemes                IPA symbol -> example word, for readable feedback
tools/prepare_assets.py     model export + lexicon generation (desktop, run once)
```

`app/src/main/assets/` is empty until you run the asset script — see
[app/src/main/assets/README.md](app/src/main/assets/README.md). The app builds and runs without
it; Sections 2 and 3 show a banner rather than faking scores.

## Tests

```powershell
.\gradlew test
```

`PronunciationScorerTest` covers the alignment logic — substitutions, deletions, insertions,
cross-word boundaries, unknown words and silence. That is where scoring bugs hide.

## Content

Practice prompts live in `data/Lessons.java` and lean on sounds that are hard for non-native
speakers — θ/ð, l/r, v/w, ʃ/tʃ and the short/long vowel pairs. A generic sentence scores well
without teaching anything, so the prompts are deliberately awkward.
