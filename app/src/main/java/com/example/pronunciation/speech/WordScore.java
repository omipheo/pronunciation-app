package com.example.pronunciation.speech;

import java.util.Collections;
import java.util.List;

/** Per-word result: the score plus the phoneme breakdown behind it. */
public class WordScore {

    /** The word as written, punctuation and all, for display. */
    public final String word;
    /** Empty when the word was not in the lexicon and therefore could not be scored. */
    public final List<PhonemeScore> phonemes;
    /** 0-100, or -1 when {@link #isScorable()} is false. */
    public final int percent;
    /** Extra phonemes inserted inside this word. */
    public final int insertions;

    public WordScore(String word, List<PhonemeScore> phonemes, int percent, int insertions) {
        this.word = word;
        this.phonemes = phonemes == null ? Collections.emptyList() : phonemes;
        this.percent = percent;
        this.insertions = insertions;
    }

    /** A word missing from the dictionary — shown greyed out rather than counted as wrong. */
    public static WordScore unscorable(String word) {
        return new WordScore(word, Collections.emptyList(), -1, 0);
    }

    public boolean isScorable() {
        return percent >= 0;
    }

    /** The expected pronunciation, e.g. {@code θɹiː}. */
    public String expectedIpa() {
        StringBuilder sb = new StringBuilder();
        for (PhonemeScore p : phonemes) sb.append(p.expected);
        return sb.toString();
    }
}
