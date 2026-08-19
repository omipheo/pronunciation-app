package com.example.pronunciation.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Example words for IPA symbols, so feedback can say "θ as in think" instead of showing a bare
 * symbol most learners cannot read.
 *
 * <p>Covers the inventory the bundled model emits. Anything unmapped falls back to the symbol.
 */
public final class Phonemes {

    private Phonemes() {
    }

    private static final Map<String, String> EXAMPLES;

    static {
        Map<String, String> m = new HashMap<>();
        // Consonants learners most often struggle with come first in spirit, but the map is flat.
        m.put("θ", "think");
        m.put("ð", "this");
        m.put("ʃ", "ship");
        m.put("ʒ", "measure");
        m.put("tʃ", "cheap");
        m.put("dʒ", "jump");
        m.put("ŋ", "sing");
        m.put("ɹ", "red");
        m.put("r", "red");
        m.put("l", "look");
        m.put("v", "van");
        m.put("w", "water");
        m.put("j", "yellow");
        m.put("h", "hat");
        m.put("b", "ball");
        m.put("p", "pen");
        m.put("d", "dog");
        m.put("t", "tree");
        m.put("ɡ", "goat");
        m.put("g", "goat");
        m.put("k", "kite");
        m.put("f", "fish");
        m.put("s", "sun");
        m.put("z", "zebra");
        m.put("m", "moon");
        m.put("n", "nose");

        // Vowels
        m.put("iː", "see");
        m.put("i", "see");
        m.put("ɪ", "sit");
        m.put("ɛ", "bed");
        m.put("e", "bed");
        m.put("æ", "cat");
        m.put("ɑ", "father");
        m.put("ɒ", "hot");
        m.put("ɔ", "thought");
        m.put("ʊ", "book");
        m.put("u", "food");
        m.put("uː", "food");
        m.put("ʌ", "cup");
        m.put("ə", "about");
        m.put("ɚ", "teacher");
        m.put("ɝ", "bird");

        // Diphthongs
        m.put("eɪ", "day");
        m.put("aɪ", "my");
        m.put("ɔɪ", "boy");
        m.put("aʊ", "now");
        m.put("oʊ", "go");

        EXAMPLES = Collections.unmodifiableMap(m);
    }

    /** @return an example word containing the sound, or null if we have none */
    public static String exampleFor(String phoneme) {
        if (phoneme == null) return null;
        return EXAMPLES.get(strip(phoneme));
    }

    /** "θ as in think", or just "θ" when there is no example. */
    public static String describe(String phoneme) {
        String example = exampleFor(phoneme);
        return example == null ? phoneme : phoneme + " as in " + example;
    }

    private static String strip(String p) {
        return p.replace("ˈ", "").replace("ˌ", "").trim();
    }
}
