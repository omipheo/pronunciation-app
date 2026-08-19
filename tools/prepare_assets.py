#!/usr/bin/env python3
"""Build the three assets the app needs for pronunciation scoring.

Run this once on the desktop; the outputs are bundled into the APK and the app never
touches the network again.

    python tools/prepare_assets.py

Outputs into app/src/main/assets/:
    phoneme_model.onnx  quantised wav2vec2 CTC model (~90-120 MB)
    vocab.json          token -> id, straight from the tokenizer
    model_config.json   blank id, input name, normalisation flag
    lexicon.txt         word -> expected IPA, derived from CMUdict

Requires (desktop only, not on the device):
    pip install torch transformers onnx onnxruntime
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request
from pathlib import Path

# A wav2vec2-base CTC model that emits IPA phonemes rather than words. Small enough to
# quantise onto a phone, and trained to transcribe what was *said*, not what was meant.
DEFAULT_MODEL = "bookbot/wav2vec2-ljspeech-gruut"

CMUDICT_URL = "https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict"

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS = REPO_ROOT / "app" / "src" / "main" / "assets"

# CMUdict is ARPAbet; the model speaks IPA. These must line up or every score is zero.
ARPABET_TO_IPA = {
    "AA": "ɑ", "AE": "æ", "AH": "ʌ", "AO": "ɔ", "AW": "aʊ", "AY": "aɪ",
    "B": "b", "CH": "tʃ", "D": "d", "DH": "ð",
    "EH": "ɛ", "ER": "ɚ", "EY": "eɪ",
    "F": "f", "G": "ɡ", "HH": "h",
    "IH": "ɪ", "IY": "i", "JH": "dʒ",
    "K": "k", "L": "l", "M": "m", "N": "n", "NG": "ŋ",
    "OW": "oʊ", "OY": "ɔɪ",
    "P": "p", "R": "ɹ", "S": "s", "SH": "ʃ",
    "T": "t", "TH": "θ",
    "UH": "ʊ", "UW": "u", "V": "v", "W": "w",
    "Y": "j", "Z": "z", "ZH": "ʒ",
}

# Unstressed AH is a schwa, which the model emits as a distinct symbol.
SCHWA = "ə"


def log(msg: str) -> None:
    print(f"[prepare-assets] {msg}", flush=True)


# --------------------------------------------------------------------------------------
# Model export
# --------------------------------------------------------------------------------------

def export_model(model_id: str, quantize: bool) -> dict:
    try:
        import torch
        from transformers import Wav2Vec2CTCTokenizer, Wav2Vec2FeatureExtractor, Wav2Vec2ForCTC
    except ImportError:
        sys.exit("Missing deps. Run: pip install torch transformers onnx onnxruntime")

    log(f"Downloading {model_id} …")
    model = Wav2Vec2ForCTC.from_pretrained(model_id)
    model.eval()

    tokenizer = Wav2Vec2CTCTokenizer.from_pretrained(model_id)
    extractor = Wav2Vec2FeatureExtractor.from_pretrained(model_id)

    fp32_path = ASSETS / "phoneme_model_fp32.onnx"
    final_path = ASSETS / "phoneme_model.onnx"

    log("Exporting to ONNX …")
    dummy = torch.randn(1, 16000)  # one second of audio; the axis is dynamic
    torch.onnx.export(
        model,
        dummy,
        str(fp32_path),
        input_names=["input_values"],
        output_names=["logits"],
        dynamic_axes={
            "input_values": {0: "batch", 1: "samples"},
            "logits": {0: "batch", 1: "frames"},
        },
        opset_version=14,
        do_constant_folding=True,
        # torch 2.13 defaults to the dynamo exporter, which renames tensors and reshapes the
        # graph. The legacy path gives the stable input_values -> logits naming the Android
        # side reads back out of model_config.json.
        dynamo=False,
    )

    if quantize:
        from onnxruntime.quantization import QuantType, quantize_dynamic

        # MatMul only, on purpose. Quantising everything also hits the convolutional feature
        # encoder, and that destroys this model: measured on synthesised speech, full int8
        # scored 0% on single words and 75% on a sentence, while MatMul-only scores 100% on
        # both — identical to fp32. The transformer holds most of the weights anyway, so
        # sparing the conv stack costs ~30 MB and buys back all the accuracy.
        log("Quantising MatMul ops to int8 (conv encoder stays fp32) …")
        quantize_dynamic(
            str(fp32_path),
            str(final_path),
            weight_type=QuantType.QInt8,
            op_types_to_quantize=["MatMul"],
        )
        fp32_path.unlink()
    else:
        fp32_path.rename(final_path)

    size_mb = final_path.stat().st_size / 1e6
    log(f"Wrote {final_path.name} ({size_mb:.0f} MB)")

    vocab = tokenizer.get_vocab()
    (ASSETS / "vocab.json").write_text(
        json.dumps(vocab, ensure_ascii=False, indent=0), encoding="utf-8"
    )

    blank_id = tokenizer.pad_token_id
    if blank_id is None:
        blank_id = vocab.get("<pad>", 0)

    config = {
        "model_id": model_id,
        "blank_id": int(blank_id),
        "input_name": "input_values",
        "normalize_input": bool(getattr(extractor, "do_normalize", True)),
        "sample_rate": int(getattr(extractor, "sampling_rate", 16000)),
    }
    (ASSETS / "model_config.json").write_text(
        json.dumps(config, indent=2), encoding="utf-8"
    )
    log(f"Vocab: {len(vocab)} tokens, blank id {blank_id}")
    return vocab


# --------------------------------------------------------------------------------------
# Lexicon
# --------------------------------------------------------------------------------------

def fetch_cmudict(cache: Path) -> str:
    if cache.exists():
        log(f"Using cached {cache.name}")
        return cache.read_text(encoding="utf-8", errors="ignore")

    log("Downloading CMUdict …")
    with urllib.request.urlopen(CMUDICT_URL) as response:
        text = response.read().decode("utf-8", errors="ignore")
    cache.write_text(text, encoding="utf-8")
    return text


def arpabet_to_tokens(phones: list[str], vocab: set[str]) -> list[str] | None:
    """Map one ARPAbet pronunciation onto tokens the model can actually emit.

    Returns None if any phone has no representation in the model's vocabulary — better to
    drop the word than to score it against symbols that can never match.
    """
    out: list[str] = []
    for phone in phones:
        stress = phone[-1] if phone[-1].isdigit() else None
        base = phone[:-1] if stress else phone

        if base == "AH" and stress == "0":
            ipa = SCHWA
        else:
            ipa = ARPABET_TO_IPA.get(base)

        if ipa is None:
            return None

        if ipa in vocab:
            out.append(ipa)
        else:
            # Diphthongs and affricates may be stored as separate symbols.
            parts = list(ipa)
            if not all(part in vocab for part in parts):
                return None
            out.extend(parts)
    return out


def build_lexicon(vocab: set[str], cache_dir: Path) -> None:
    raw = fetch_cmudict(cache_dir / "cmudict.dict")

    written = 0
    skipped = 0
    seen: set[str] = set()
    lines: list[str] = []

    for line in raw.splitlines():
        line = line.split("#")[0].strip()
        if not line:
            continue

        parts = line.split()
        word, phones = parts[0], parts[1:]

        # cmudict lists alternates as "word(2)"; the first entry is the common one.
        if "(" in word:
            continue
        word = word.lower()
        if word in seen:
            continue

        tokens = arpabet_to_tokens(phones, vocab)
        if tokens is None:
            skipped += 1
            continue

        seen.add(word)
        lines.append(f"{word}\t{' '.join(tokens)}")
        written += 1

    out = ASSETS / "lexicon.txt"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    size_mb = out.stat().st_size / 1e6

    log(f"Wrote lexicon.txt: {written} words ({size_mb:.1f} MB), {skipped} skipped")
    if skipped > written * 0.05:
        log(
            f"WARNING: skipped {skipped} words. The ARPABET_TO_IPA table in this script "
            f"probably does not match this model's symbol inventory — check vocab.json."
        )


def report_unmapped(vocab: set[str]) -> None:
    """Name the IPA symbols we produce that the model cannot emit. Silent mismatches here
    are the single most likely cause of everything scoring badly."""
    produced = set()
    for ipa in list(ARPABET_TO_IPA.values()) + [SCHWA]:
        produced.add(ipa)

    missing = sorted(s for s in produced if s not in vocab and not all(c in vocab for c in s))
    if missing:
        log(f"WARNING: these IPA symbols are absent from the model vocab: {missing}")
    else:
        log("All mapped IPA symbols exist in the model vocabulary.")


# --------------------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default=DEFAULT_MODEL, help="HuggingFace model id")
    parser.add_argument("--no-quantize", action="store_true",
                        help="keep fp32 (3-4x larger, marginally more accurate)")
    parser.add_argument("--lexicon-only", action="store_true",
                        help="rebuild lexicon.txt from an existing vocab.json")
    args = parser.parse_args()

    ASSETS.mkdir(parents=True, exist_ok=True)
    cache_dir = REPO_ROOT / "tools" / ".cache"
    cache_dir.mkdir(exist_ok=True)

    if args.lexicon_only:
        vocab_path = ASSETS / "vocab.json"
        if not vocab_path.exists():
            sys.exit("vocab.json not found — run without --lexicon-only first")
        vocab = json.loads(vocab_path.read_text(encoding="utf-8"))
    else:
        vocab = export_model(args.model, quantize=not args.no_quantize)

    vocab_tokens = set(vocab.keys())
    report_unmapped(vocab_tokens)
    build_lexicon(vocab_tokens, cache_dir)

    log("Done. Rebuild the app and the banner in Sections 2 and 3 will disappear.")


if __name__ == "__main__":
    main()
