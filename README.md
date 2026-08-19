# Pronunciation

An offline Android app for practising English pronunciation. Java + XML Views, minSdk 26.
No network calls at runtime — the speech model runs on the device.

New here? Start with [SETUP.md](SETUP.md); nothing is installed on this machine yet.

## The three sections

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
    alphabet/               Section 1
    training/               Section 2
    game/                   Section 3
  data/                     alphabet table and practice content
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
