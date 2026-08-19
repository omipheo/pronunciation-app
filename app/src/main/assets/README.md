# Generated assets

This folder is intentionally almost empty. Four files belong here, and all four are produced
by `tools/prepare_assets.py`:

| File | Size | Purpose |
|---|---|---|
| `phoneme_model.onnx` | 116 MB | wav2vec2 CTC model, MatMul ops int8, conv encoder fp32 |
| `vocab.json` | 1 KB | token → id map from the tokenizer |
| `model_config.json` | <1 KB | blank id, input tensor name, normalisation flag |
| `lexicon.txt` | 3.1 MB | word → expected IPA, 126,052 words from CMUdict |

Note `blank_id` is **42** for this model, not 0. `OnnxPhonemeRecognizer` reads it from
`model_config.json` rather than assuming — hardcoding 0 produces silent garbage.

They are not committed because of their size. Until you generate them, the app still builds
and runs — Section 1 works fully, and Sections 2 and 3 show a banner explaining that the model
is missing rather than pretending to score.

From the project root:

    pip install torch transformers onnx onnxruntime
    python tools/prepare_assets.py
