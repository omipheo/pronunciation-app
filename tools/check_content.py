#!/usr/bin/env python3
"""Validate the practice content in Lessons.java against the bundled lexicon.

    python tools/check_content.py

Two failure modes this catches, both silent at runtime:

1. A prompt word missing from the lexicon cannot be scored. The app greys it out and
   excludes it from the total, so a typo turns into a prompt that quietly teaches nothing.
2. A phoneme with no prompts makes "practise this sound" on the Main tab a dead end - the
   app can tell you θ is your weakest sound and then have nothing to offer.

Exits non-zero if either check fails, so it can gate a commit.
"""

from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LESSONS = ROOT / "app/src/main/java/com/example/pronunciation/data/Lessons.java"
LEXICON = ROOT / "app/src/main/assets/lexicon.txt"
PHONEMES = ROOT / "app/src/main/java/com/example/pronunciation/data/Phonemes.java"

# Below this, "practise this sound" has almost nothing to offer.
MIN_PROMPTS_PER_PHONEME = 3


def load_lexicon() -> dict[str, list[str]]:
    if not LEXICON.exists():
        sys.exit(f"{LEXICON} not found - run tools/prepare_assets.py first")
    out = {}
    for line in LEXICON.read_text(encoding="utf-8").splitlines():
        if "\t" in line:
            w, p = line.split("\t", 1)
            out[w] = p.split()
    return out


def strip_comments(src: str) -> str:
    """Remove // comments, respecting string literals.

    Without this, a comment that happens to quote a word - which is natural when explaining a
    spelling choice - gets picked up as a string literal and silently corrupts the prompt text.
    """
    out = []
    in_str = False
    i = 0
    while i < len(src):
        ch = src[i]
        if in_str:
            out.append(ch)
            if ch == "\\" and i + 1 < len(src):
                out.append(src[i + 1])
                i += 2
                continue
            if ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
            out.append(ch)
        elif ch == "/" and i + 1 < len(src) and src[i + 1] == "/":
            while i < len(src) and src[i] != "\n":
                i += 1
            continue
        else:
            out.append(ch)
        i += 1
    return "".join(out)


def parse_prompts() -> list[tuple[str, str]]:
    """Extract (unit, text) from every `new Lesson(...)` in the source."""
    src = strip_comments(LESSONS.read_text(encoding="utf-8"))
    prompts = []

    for chunk in src.split("new Lesson(")[1:]:
        # Take up to the closing paren of this constructor.
        depth, end = 1, 0
        in_str = False
        for i, ch in enumerate(chunk):
            if ch == '"':
                in_str = not in_str
            elif not in_str:
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        end = i
                        break
        body = chunk[:end]

        unit_match = re.match(r"\s*Lesson\.Unit\.(\w+)", body)
        literals = re.findall(r'"([^"]*)"', body)
        if not unit_match or len(literals) < 2:
            continue
        # Everything before the final literal is the (possibly concatenated) prompt text.
        prompts.append((unit_match.group(1), "".join(literals[:-1])))

    return prompts


def normalize(word: str) -> str:
    return re.sub(r"[^a-z']+$", "", re.sub(r"^[^a-z']+", "", word.lower()))


def strip_marks(p: str) -> str:
    return p.replace("ˈ", "").replace("ˌ", "").replace("ː", "").strip()


def main() -> None:
    lexicon = load_lexicon()
    prompts = parse_prompts()
    failed = False

    by_unit = Counter(u for u, _ in prompts)
    print(f"prompts: {len(prompts)}  " + "  ".join(f"{u.lower()}={n}" for u, n in sorted(by_unit.items())))

    # --- 1. every word must be scorable -------------------------------------------------
    unknown = []
    for unit, text in prompts:
        for raw in text.split():
            w = normalize(raw)
            if w and w not in lexicon:
                unknown.append((w, text))

    if unknown:
        failed = True
        print(f"\nFAIL: {len(unknown)} prompt word(s) missing from the lexicon:")
        for w, text in unknown:
            print(f"   {w!r} in {text[:60]!r}")
    else:
        print("\nOK: every prompt word is in the lexicon")

    # --- 2. phoneme coverage --------------------------------------------------------------
    inventory = {strip_marks(p) for ph in lexicon.values() for p in ph}
    inventory.discard("")

    coverage = Counter()
    for _, text in prompts:
        seen = set()
        for raw in text.split():
            for p in lexicon.get(normalize(raw), []):
                seen.add(strip_marks(p))
        coverage.update(seen)

    thin = sorted((p for p in inventory if coverage[p] < MIN_PROMPTS_PER_PHONEME),
                  key=lambda p: coverage[p])

    print(f"\nphoneme coverage ({len(inventory)} symbols in the lexicon):")
    for p in sorted(inventory, key=lambda x: -coverage[x]):
        bar = "#" * min(40, coverage[p])
        print(f"   {p:4s} {coverage[p]:4d}  {bar}")

    if thin:
        failed = True
        print(f"\nFAIL: {len(thin)} phoneme(s) below {MIN_PROMPTS_PER_PHONEME} prompts:")
        for p in thin:
            print(f"   {p!r}: {coverage[p]} prompt(s)")
    else:
        print(f"\nOK: every phoneme appears in at least {MIN_PROMPTS_PER_PHONEME} prompts")

    # --- 3. example words for the Main tab -------------------------------------------------
    mapped = set(re.findall(r'm\.put\("([^"]+)"', PHONEMES.read_text(encoding="utf-8")))
    unmapped = sorted(p for p in inventory if p not in mapped)
    if unmapped:
        failed = True
        print(f"\nFAIL: no example word in Phonemes.java for: {unmapped}")
    else:
        print("OK: every phoneme has an example word")

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
