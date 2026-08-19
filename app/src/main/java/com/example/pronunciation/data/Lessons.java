package com.example.pronunciation.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Practice content for Sections 2 and 3.
 *
 * <p>Bundled in code for now. The prompts lean on sounds that are hard for non-native speakers —
 * θ/ð, l/r, v/w, ʃ/tʃ and the short/long vowel pairs — because a generic sentence scores well
 * without teaching anything.
 */
public final class Lessons {

    private Lessons() {
    }

    private static final List<Lesson> ALL = Collections.unmodifiableList(Arrays.asList(
            // --- Words: minimal pairs and single tricky sounds ---
            new Lesson(Lesson.Unit.WORD, "think", "θ as in think, not s or t"),
            new Lesson(Lesson.Unit.WORD, "this", "ð — voiced, buzz the tongue tip"),
            new Lesson(Lesson.Unit.WORD, "world", "r followed by l"),
            new Lesson(Lesson.Unit.WORD, "really", "r and l in one word"),
            new Lesson(Lesson.Unit.WORD, "very", "v — teeth on lip, not w"),
            new Lesson(Lesson.Unit.WORD, "ship", "ʃ versus tʃ"),
            new Lesson(Lesson.Unit.WORD, "cheap", "tʃ — a hard start to the ʃ"),
            new Lesson(Lesson.Unit.WORD, "beach", "long iː"),
            new Lesson(Lesson.Unit.WORD, "rhythm", "two unstressed syllables"),
            new Lesson(Lesson.Unit.WORD, "comfortable", "swallowed middle syllable"),

            // --- Sentences ---
            new Lesson(Lesson.Unit.SENTENCE, "The three thin thieves left.", "repeated θ"),
            new Lesson(Lesson.Unit.SENTENCE, "She sells sea shells by the shore.", "s versus ʃ"),
            new Lesson(Lesson.Unit.SENTENCE, "I would like a glass of water.", "l, w and the schwa"),
            new Lesson(Lesson.Unit.SENTENCE, "Red lorry, yellow lorry.", "r and l alternating"),
            new Lesson(Lesson.Unit.SENTENCE, "The weather is very cold today.", "w versus v"),
            new Lesson(Lesson.Unit.SENTENCE, "Please call me back this evening.", "long iː, final ŋ"),
            new Lesson(Lesson.Unit.SENTENCE, "That is the third time this month.", "θ and ð together"),
            new Lesson(Lesson.Unit.SENTENCE, "He caught a big fish in the river.", "short ɪ versus long iː"),

            // --- Paragraphs ---
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "Every morning I walk to the park near my house. "
                            + "The air is fresh and the birds are singing. "
                            + "I think it is the best part of my day.",
                    "connected speech at natural pace"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "Learning a language takes time and patience. "
                            + "You will make mistakes, and that is completely normal. "
                            + "What matters is that you keep practising every single day.",
                    "longer phrases, weak forms"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "The weather changed quickly this afternoon. "
                            + "Thick grey clouds covered the whole sky, "
                            + "and within thirty minutes the rain was falling hard.",
                    "θ, ð and consonant clusters")
    ));

    public static List<Lesson> byUnit(Lesson.Unit unit) {
        List<Lesson> out = new ArrayList<>();
        for (Lesson lesson : ALL) {
            if (lesson.unit == unit) out.add(lesson);
        }
        return out;
    }

    public static List<Lesson> all() {
        return ALL;
    }

    /** A shuffled run of sentence prompts for one game session. */
    public static List<Lesson> gameRound(int count, long seed) {
        List<Lesson> pool = new ArrayList<>(byUnit(Lesson.Unit.SENTENCE));
        Collections.shuffle(pool, new Random(seed));
        return pool.subList(0, Math.min(count, pool.size()));
    }
}
