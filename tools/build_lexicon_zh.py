#!/usr/bin/env python3
"""Build the Mandarin lexicon: hanzi -> IPA phonemes the model can actually emit.

    python tools/build_lexicon_zh.py

Uses espeak-ng as the phonemiser, which is not an arbitrary choice: the bundled Chinese model
is `facebook/wav2vec2-lv-60-espeak-cv-ft`, trained on espeak phonemisations. Using the same
tool for the expected pronunciation means both sides speak an identical alphabet, with no
hand-written mapping table to get subtly wrong. The English side needed such a table
(ARPABET -> IPA) and it took two rounds to get right.

Every phoneme produced is checked against the model's vocabulary. A symbol the model cannot
emit would score as a permanent error no learner could ever fix.

espeak-ng is expected at C:\\Android\\espeak (extracted from the MSI with
`msiexec /a espeak-ng.msi /qn TARGETDIR=...`, which needs no admin rights). Override with
ESPEAK_NG_EXE.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
VOCAB = ASSETS / "vocab_zh.json"
OUT = ASSETS / "lexicon_zh.txt"

ESPEAK = Path(os.environ.get("ESPEAK_NG_EXE", r"C:\Android\espeak\eSpeak NG\espeak-ng.exe"))
ESPEAK_DATA = ESPEAK.parent / "espeak-ng-data"

# Stress and phrase marks espeak adds; the model does not emit them.
STRIP = "ˈˌ|-_ \t"


def log(m: str) -> None:
    print(f"[lexicon-zh] {m}", flush=True)


def phonemise(lines: list[str]) -> list[str]:
    """One espeak call for the whole batch — per-character calls take minutes."""
    if not ESPEAK.exists():
        sys.exit(f"espeak-ng not found at {ESPEAK}. See the module docstring.")

    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "in.txt"
        # Chinese cannot survive the Windows ANSI codepage as a command-line argument, so the
        # text goes through a UTF-8 file. This silently produced empty output when passed
        # directly.
        src.write_text("\n".join(lines) + "\n", encoding="utf-8")

        env = dict(os.environ, ESPEAK_DATA_PATH=str(ESPEAK_DATA))
        result = subprocess.run(
            [str(ESPEAK), "-v", "cmn", "--ipa", "-q", "-f", str(src)],
            capture_output=True, env=env,
        )
        text = result.stdout.decode("utf-8", errors="replace")

    out = [ln.strip() for ln in text.splitlines()]
    # espeak emits one line per input line, but drops empties; re-align defensively.
    if len(out) != len(lines):
        log(f"WARNING: espeak returned {len(out)} lines for {len(lines)} inputs")
    return out


def tokenise(ipa: str, vocab: set[str]) -> list[str] | None:
    """Greedy longest-match against the model's own vocabulary.

    Returns None if any part of the string cannot be represented, rather than silently
    dropping it — a dropped phoneme becomes a phantom deletion in every score.
    """
    s = "".join(c for c in ipa if c not in STRIP)
    out: list[str] = []
    i = 0
    longest = max((len(t) for t in vocab), default=1)

    while i < len(s):
        for size in range(min(longest, len(s) - i), 0, -1):
            chunk = s[i:i + size]
            if chunk in vocab:
                out.append(chunk)
                i += size
                break
        else:
            return None
    return out


def main() -> None:
    if not VOCAB.exists():
        sys.exit(f"{VOCAB.name} missing — run tools/prepare_assets.py --lang zh first")
    vocab = set(json.loads(VOCAB.read_text(encoding="utf-8")))
    vocab.discard("")

    # Characters the app will ever need: everything used by the Chinese content, plus the
    # syllable table behind the pinyin section.
    sources = [ASSETS / "prompts_zh.tsv", ASSETS / "game_problems_zh.tsv"]
    chars: set[str] = set()
    for path in sources:
        if path.exists():
            for ch in path.read_text(encoding="utf-8"):
                if "\u4e00" <= ch <= "\u9fff":
                    chars.add(ch)

    extra = ROOT / "tools" / "zh_charset.txt"
    if extra.exists():
        for ch in extra.read_text(encoding="utf-8"):
            if "\u4e00" <= ch <= "\u9fff":
                chars.add(ch)

    if not chars:
        sys.exit("No Chinese characters found. Generate content first, or add tools/zh_charset.txt")

    ordered = sorted(chars)
    log(f"phonemising {len(ordered)} characters …")
    ipa_lines = phonemise(ordered)

    rows, skipped = [], []
    for ch, ipa in zip(ordered, ipa_lines):
        tokens = tokenise(ipa, vocab)
        if not tokens:
            skipped.append((ch, ipa))
            continue
        rows.append(f"{ch}\t{' '.join(tokens)}")

    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    log(f"wrote {OUT.name}: {len(rows)} characters, {OUT.stat().st_size / 1024:.0f} KB")

    if skipped:
        log(f"WARNING: {len(skipped)} character(s) produced symbols outside the model vocab:")
        for ch, ipa in skipped[:15]:
            log(f"   {ch}  ->  {ipa!r}")
        log("   These are excluded; prompts containing them cannot be scored.")


if __name__ == "__main__":
    main()
