#!/usr/bin/env python3
"""Build the practice corpus into app/src/main/assets/prompts.tsv.

    python tools/generate_content.py

Targets: 1000+ words, 500+ sentences, 300+ paragraphs. That is far too much to keep in Java
source, so it ships as a generated asset and Lessons.java keeps only a small curated seed.

Everything is deterministic (fixed seed) and every word is checked against the bundled lexicon
before being written, because a prompt containing an unknown word cannot be scored - the app
greys it out and drops it from the total, which looks like a model failure rather than a
content bug.

Needs internet the first time, to fetch a word-frequency list. Cached in tools/.cache after.
"""

from __future__ import annotations

import random
import sys
import urllib.request
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
LEXICON = ASSETS / "lexicon.txt"
CACHE = ROOT / "tools/.cache"
OUT = ASSETS / "prompts.tsv"

FREQ_URL = ("https://raw.githubusercontent.com/first20hours/google-10000-english/"
            "master/google-10000-english-usa-no-swears.txt")

SEED = 20260820
TARGET_WORDS = 1200
TARGET_SENTENCES = 600
TARGET_PARAGRAPHS = 350

# Game problems: passages read aloud one sentence at a time, gated on getting each right.
GAME_PROBLEMS = 320
GAME_LEVELS = 10
GAME_PARAGRAPHS = 3          # the floor; harder levels get more
GAME_SENTENCES_PER_PARA = 3
SENTENCE_POOL = 2600         # internal pool, larger than the practice corpus, for variety

# Sounds non-native speakers lose most often, hardest first. A generated word's "focus" line
# names the hardest sound it contains, so the prompt still teaches something.
DIFFICULTY = ["θ", "ð", "ʒ", "ɹ", "ŋ", "ɚ", "ɝ", "v", "w", "ʃ", "tʃ", "dʒ",
              "æ", "ʌ", "ɔɪ", "aʊ", "ʊ", "l", "z", "j", "ɔ", "ɪ", "i", "u", "eɪ", "aɪ", "oʊ"]

EXAMPLES = {
    "θ": "think", "ð": "this", "ʃ": "ship", "ʒ": "measure", "tʃ": "cheap", "dʒ": "jump",
    "ŋ": "sing", "ɹ": "red", "l": "look", "v": "van", "w": "water", "j": "yellow",
    "æ": "cat", "ʌ": "cup", "ɚ": "teacher", "ɝ": "bird", "ɔɪ": "boy", "aʊ": "now",
    "ʊ": "book", "ɔ": "thought", "ɪ": "sit", "i": "see", "u": "food", "eɪ": "day",
    "aɪ": "my", "oʊ": "go", "z": "zebra",
}


def log(m: str) -> None:
    print(f"[content] {m}", flush=True)


def strip_marks(p: str) -> str:
    return p.replace("ˈ", "").replace("ˌ", "").replace("ː", "").strip()


def load_lexicon() -> dict[str, list[str]]:
    if not LEXICON.exists():
        sys.exit(f"{LEXICON} missing - run tools/prepare_assets.py first")
    out = {}
    for line in LEXICON.read_text(encoding="utf-8").splitlines():
        if "\t" in line:
            w, p = line.split("\t", 1)
            out[w] = [strip_marks(x) for x in p.split()]
    return out


def load_frequency() -> list[str]:
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / "google-10000-english.txt"
    if not cached.exists():
        log("downloading word frequency list …")
        with urllib.request.urlopen(FREQ_URL) as r:
            cached.write_bytes(r.read())
    return [w.strip().lower() for w in cached.read_text(encoding="utf-8").splitlines() if w.strip()]


# ---------------------------------------------------------------------------------------
# Words
# ---------------------------------------------------------------------------------------

def build_words(lex: dict[str, list[str]], freq: list[str]) -> list[tuple[str, str]]:
    """Common words that are pronounceable practice, ordered so every sound gets covered."""
    candidates = [
        w for w in freq
        if w in lex and 3 <= len(w) <= 12 and w.isalpha()
    ]

    # Round-robin by hardest sound, so the list is not 900 easy words then a few hard ones.
    buckets: dict[str, list[str]] = {p: [] for p in DIFFICULTY}
    leftovers: list[str] = []
    for w in candidates:
        sounds = set(lex[w])
        hardest = next((p for p in DIFFICULTY if p in sounds), None)
        (buckets[hardest] if hardest else leftovers).append(w)

    picked: list[str] = []
    seen: set[str] = set()
    order = [p for p in DIFFICULTY if buckets[p]]
    i = 0
    while len(picked) < TARGET_WORDS and order:
        p = order[i % len(order)]
        if buckets[p]:
            w = buckets[p].pop(0)
            if w not in seen:
                seen.add(w)
                picked.append(w)
        else:
            order.remove(p)
            continue
        i += 1

    for w in leftovers:
        if len(picked) >= TARGET_WORDS:
            break
        if w not in seen:
            seen.add(w)
            picked.append(w)

    out = []
    for w in picked:
        sounds = set(lex[w])
        hardest = next((p for p in DIFFICULTY if p in sounds), None)
        if hardest and hardest in EXAMPLES:
            focus = f"{hardest} as in {EXAMPLES[hardest]}"
        else:
            focus = f"{len(lex[w])} sounds"
        out.append((w, focus))
    return out


# ---------------------------------------------------------------------------------------
# Sentences - themed frames with compatible banks, so output stays grammatical and sane
# ---------------------------------------------------------------------------------------

PLURAL_SUBJ = ["I", "we", "they", "my friends", "the children", "my parents", "the students"]
SINGULAR_SUBJ = ["she", "he", "my brother", "my sister", "the teacher", "my mother", "my father"]

# Verbs live with their theme. Sharing one verb pool across themes produced "read a healthy
# salad" - grammatical, but nonsense a learner would notice and distrust.
THEMES: dict[str, dict] = {
    "daily": {
        "verbs": [("enjoy", "enjoys"), ("need", "needs"), ("want", "wants"),
                  ("remember", "remembers"), ("finish", "finishes")],
        "objects": ["another cup of coffee", "a healthy breakfast", "these warm clothes",
                    "the usual routine", "a short walk", "the weather report",
                    "three thin slices of bread", "the shopping list", "a quiet evening",
                    "the morning news"],
    },
    "study": {
        "verbs": [("read", "reads"), ("learn", "learns"), ("practice", "practices"),
                  ("review", "reviews"), ("remember", "remembers"), ("explain", "explains"),
                  ("finish", "finishes")],
        "objects": ["the third chapter", "this difficult language", "the whole lesson",
                    "another example", "the right answer", "these long words",
                    "a clear explanation", "our weekly homework", "the correct pronunciation",
                    "the first question"],
    },
    "travel": {
        "verbs": [("book", "books"), ("miss", "misses"), ("plan", "plans"),
                  ("enjoy", "enjoys"), ("remember", "remembers"), ("cancel", "cancels"),
                  ("choose", "chooses")],
        "objects": ["the earlier train", "a window seat", "the whole journey",
                    "another route", "a cheaper flight", "the return ticket",
                    "another hotel", "the northern railway", "the long road trip",
                    "our summer holiday"],
    },
    "food": {
        "verbs": [("eat", "eats"), ("prepare", "prepares"), ("cook", "cooks"),
                  ("order", "orders"), ("share", "shares"), ("enjoy", "enjoys"),
                  ("buy", "buys")],
        "objects": ["fresh vegetables", "another chocolate", "these ripe cherries",
                    "the whole chicken", "warm soup", "the usual sandwich",
                    "three yellow lemons", "a healthy salad", "the sweet dessert",
                    "a large breakfast"],
    },
    "work": {
        "verbs": [("finish", "finishes"), ("review", "reviews"), ("discuss", "discusses"),
                  ("prepare", "prepares"), ("share", "shares"), ("schedule", "schedules"),
                  ("cancel", "cancels")],
        "objects": ["the weekly meeting", "another difficult problem",
                    "these important messages", "the whole project", "a clear decision",
                    "the office move", "our regular schedule", "three separate reports",
                    "the shared folder", "a short break"],
    },
}

# Stative verbs do not take an imperative or a manner adverb: "Please need it slowly" is not
# English, however grammatical the slots look.
STATIVE = {"need", "want", "remember", "enjoy"}

# (template, needs_base, plural_only, action_only)
#   needs_base  - English takes the bare infinitive here; elsewhere the verb must agree with
#                 the subject, or singular subjects produce "my father always remember"
#   plural_only - "together" needs more than one person
#   action_only - excludes STATIVE verbs
FRAMES: list[tuple[str, bool, bool, bool]] = [
    ("{s} {v} {o}.", False, False, False),
    ("{s} {v} {o} every week.", False, False, False),
    ("{s} never {v} {o}.", False, False, False),
    ("{s} always {v} {o} together.", False, True, False),
    ("Do {sp} {vb} {o}?", True, False, False),
    ("{s} will {vb} {o} tomorrow.", True, False, False),
    ("{s} {v} {o} without thinking.", False, False, False),
    ("Please {vb} {o} slowly.", True, False, True),
    ("{s} {v} {o} because it is easier.", False, False, False),
    ("{s} {v} {o} on Thursday.", False, False, False),
    ("{s} {v} {o} in the morning.", False, False, False),
    ("{s} rarely {v} {o}.", False, False, False),
]


def build_sentences(rng: random.Random, limit: int = TARGET_SENTENCES
                    ) -> list[tuple[str, str, str, int, str]]:
    """@return (text, focus, theme, frame_index, object_phrase)"""
    out: list[tuple[str, str, str, int, str]] = []
    seen: set[str] = set()

    combos = []
    for theme, spec in THEMES.items():
        for obj in spec["objects"]:
            for base, third in spec["verbs"]:
                for fi in range(len(FRAMES)):
                    combos.append((theme, obj, base, third, fi))
    rng.shuffle(combos)

    for theme, obj, base, third, fi in combos:
        if len(out) >= limit:
            break
        frame, needs_base, plural_only, action_only = FRAMES[fi]

        if action_only and base in STATIVE:
            continue

        if "{sp}" in frame:
            text = frame.format(sp=rng.choice(PLURAL_SUBJ), vb=base, o=obj)
        elif "{s}" in frame:
            plural = True if plural_only else rng.random() < 0.5
            subj = rng.choice(PLURAL_SUBJ if plural else SINGULAR_SUBJ)
            verb = base if (needs_base or plural) else third
            text = frame.format(s=subj, v=verb, vb=base, o=obj)
        else:
            text = frame.format(vb=base, o=obj)

        text = text[0].upper() + text[1:]
        if text in seen:
            continue
        seen.add(text)
        out.append((text, f"connected speech — {theme}", theme, fi, obj))

    return out


def build_paragraphs(sentences: list[tuple[str, str, str, int, str]],
                     rng: random.Random) -> list[tuple[str, str]]:
    by_theme: dict[str, list[tuple[str, int]]] = {}
    for text, _, theme, fi, _ in sentences:
        by_theme.setdefault(theme, []).append((text, fi))

    out: list[tuple[str, str]] = []
    seen: set[str] = set()
    themes = [t for t in by_theme if len(by_theme[t]) >= 3]

    attempts = 0
    while len(out) < TARGET_PARAGRAPHS and attempts < TARGET_PARAGRAPHS * 60:
        attempts += 1
        theme = rng.choice(themes)
        picked = rng.sample(by_theme[theme], 3)

        # Three distinct frames, or the paragraph reads as the same sentence three times
        # ("... in the morning. ... in the morning. ...").
        if len({fi for _, fi in picked}) < 3:
            continue

        text = " ".join(t for t, _ in picked)
        if text in seen:
            continue
        seen.add(text)
        out.append((text, f"three linked sentences — {theme}"))
    return out


# ---------------------------------------------------------------------------------------
# Game problems: multi-paragraph passages, banded into difficulty levels
# ---------------------------------------------------------------------------------------

# Sounds that actually cost a learner effort, and roughly what they cost.
HARD_WEIGHTS = {"θ": 3, "ð": 3, "ʒ": 3, "ɹ": 2, "ŋ": 2, "ɚ": 2, "ɝ": 2,
                "v": 2, "w": 1, "ʃ": 2, "tʃ": 2, "dʒ": 2, "æ": 1, "ʌ": 1,
                "ʊ": 1, "ɔɪ": 2, "aʊ": 2}
VOWELS = set("iɪɛæɑɔʊuʌəɚɝ") | {"eɪ", "aɪ", "ɔɪ", "aʊ", "oʊ", "iː", "uː"}


def sentence_difficulty(text: str, lex: dict[str, list[str]]) -> float:
    """Effort per word: hard sounds, consonant clusters and syllable count.

    Per word rather than per sentence, so difficulty means "how hard are these words to say"
    and not simply "how long is this".
    """
    total = 0.0
    words = 0
    for raw in text.split():
        phones = lex.get(normalize(raw))
        if not phones:
            continue
        words += 1

        score = sum(HARD_WEIGHTS.get(p, 0) for p in phones)

        run = 0
        for p in phones:
            if p in VOWELS:
                run = 0
            else:
                run += 1
                if run >= 3:
                    score += 3      # three consonants in a row is a real obstacle
        syllables = sum(1 for p in phones if p in VOWELS)
        score += max(0, syllables - 1)
        total += score

    return total / words if words else 0.0


def build_game_problems(sentences: list[tuple[str, str, str, int, str]],
                        lex: dict[str, list[str]],
                        rng: random.Random) -> list[tuple[int, str, list[str]]]:
    """@return (level, title, paragraphs) with at least GAME_PARAGRAPHS paragraphs each."""
    scored = [(sentence_difficulty(t, lex), t, theme, fi, obj)
              for t, _, theme, fi, obj in sentences]
    scored.sort(key=lambda r: r[0])

    # Equal-count bands, so every level has material regardless of how the scores cluster.
    band_size = max(1, len(scored) // GAME_LEVELS)
    bands: list[list[tuple[float, str, str, int, str]]] = [
        scored[i * band_size: (i + 1) * band_size] for i in range(GAME_LEVELS)
    ]
    bands[-1].extend(scored[GAME_LEVELS * band_size:])

    per_level = GAME_PROBLEMS // GAME_LEVELS
    out: list[tuple[int, str, list[str]]] = []
    seen: set[str] = set()

    for level in range(1, GAME_LEVELS + 1):
        pool = bands[level - 1]
        by_theme: dict[str, list[tuple[str, int, str]]] = {}
        for _, text, theme, fi, obj in pool:
            by_theme.setdefault(theme, []).append((text, fi, obj))
        themes = [t for t in by_theme if len(by_theme[t]) >= GAME_SENTENCES_PER_PARA * 2]
        if not themes:
            themes = list(by_theme)

        # Longer passages at higher levels, on top of harder sentences.
        paragraphs_needed = GAME_PARAGRAPHS + (1 if level >= 7 else 0)
        needed = paragraphs_needed * GAME_SENTENCES_PER_PARA

        made, attempts = 0, 0
        while made < per_level and attempts < per_level * 400:
            attempts += 1
            theme = rng.choice(themes)
            available = by_theme[theme]
            if len(available) < needed:
                continue

            picked = rng.sample(available, min(len(available), needed * 3))

            # A passage that says "this difficult language" four times reads as filler. Each
            # theme only has ~10 object phrases, so cap repeats rather than forbid them.
            chosen: list[tuple[str, int, str]] = []
            obj_counts: Counter = Counter()
            frame_counts: Counter = Counter()
            for cand in picked:
                if len(chosen) >= needed:
                    break
                if obj_counts[cand[2]] >= 2 or frame_counts[cand[1]] >= 2:
                    continue
                chosen.append(cand)
                obj_counts[cand[2]] += 1
                frame_counts[cand[1]] += 1
            if len(chosen) < needed:
                continue

            paragraphs = []
            ok = True
            for p in range(paragraphs_needed):
                chunk = chosen[p * GAME_SENTENCES_PER_PARA:(p + 1) * GAME_SENTENCES_PER_PARA]
                if len({fi for _, fi, _ in chunk}) < GAME_SENTENCES_PER_PARA:
                    ok = False      # same frame twice reads as one sentence repeated
                    break
                paragraphs.append(" ".join(t for t, _, _ in chunk))
            if not ok:
                continue

            key = "||".join(paragraphs)
            if key in seen:
                continue
            seen.add(key)
            out.append((level, f"{theme.title()} · level {level}", paragraphs))
            made += 1

    return out


# ---------------------------------------------------------------------------------------

def normalize(word: str) -> str:
    import re
    return re.sub(r"[^a-z']+$", "", re.sub(r"^[^a-z']+", "", word.lower()))


def main() -> None:
    rng = random.Random(SEED)
    lex = load_lexicon()
    freq = load_frequency()

    words = build_words(lex, freq)
    log(f"words: {len(words)}")

    # One big pool feeds both the practice corpus and the game passages; the game needs far
    # more raw material because each problem consumes 9-12 sentences.
    pool = build_sentences(rng, limit=SENTENCE_POOL)
    sentences = pool[:TARGET_SENTENCES]
    log(f"sentences: {len(sentences)} (pool {len(pool)})")

    paragraphs = build_paragraphs(sentences, rng)
    log(f"paragraphs: {len(paragraphs)}")

    problems = build_game_problems(pool, lex, rng)
    log(f"game problems: {len(problems)}")

    rows = ([("WORD", t, f) for t, f in words]
            + [("SENTENCE", t, f) for t, f, _, _, _ in sentences]
            + [("PARAGRAPH", t, f) for t, f in paragraphs])

    # Refuse to ship a prompt containing a word the scorer cannot look up.
    bad = []
    checkable = [(u, t) for u, t, _ in rows] + [
        ("GAME", " ".join(paras)) for _, _, paras in problems
    ]
    for unit, text in checkable:
        for raw in text.split():
            w = normalize(raw)
            if w and w not in lex:
                bad.append((w, text))
    if bad:
        log(f"ERROR: {len(bad)} unknown word(s); fix the banks in this script:")
        for w, t in bad[:20]:
            log(f"   {w!r} in {t[:70]!r}")
        sys.exit(1)

    ASSETS.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="\n") as fh:
        for unit, text, focus in rows:
            fh.write(f"{unit}\t{text}\t{focus}\n")

    # level \t title \t paragraphs joined by ||
    games = ASSETS / "game_problems.tsv"
    with games.open("w", encoding="utf-8", newline="\n") as fh:
        for level, title, paras in problems:
            fh.write(f"{level}\t{title}\t{'||'.join(paras)}\n")

    lengths = [len(" ".join(p).split()) for _, _, p in problems]
    per_level = Counter(l for l, _, _ in problems)
    log(f"wrote {games.name}: {len(problems)} problems, "
        f"{min(lengths)}-{max(lengths)} words each, {games.stat().st_size / 1024:.0f} KB")
    log("  per level: " + "  ".join(f"L{k}={per_level[k]}" for k in sorted(per_level)))

    coverage = Counter()
    for _, text, _ in rows:
        seen = set()
        for raw in text.split():
            seen.update(lex.get(normalize(raw), []))
        coverage.update(seen)
    inventory = {p for ph in lex.values() for p in ph}
    thin = sorted(p for p in inventory if coverage[p] < 3)

    log(f"wrote {OUT.name}: {len(rows)} prompts, {OUT.stat().st_size / 1024:.0f} KB")
    log(f"phoneme coverage: {len(inventory) - len(thin)}/{len(inventory)} symbols have 3+ prompts")
    if thin:
        log(f"WARNING: thin coverage for {thin}")


if __name__ == "__main__":
    main()
