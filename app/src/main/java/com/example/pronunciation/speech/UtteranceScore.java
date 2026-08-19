package com.example.pronunciation.speech;

import java.util.List;

/** Result of scoring one recorded attempt at a target sentence. */
public class UtteranceScore {

    /** The prompt the learner was asked to read. */
    public final String targetText;
    /** 0-100 across every scorable word. */
    public final int overallPercent;
    public final List<WordScore> words;
    /** Raw phoneme sequence the recogniser heard — useful for debugging, shown behind a toggle. */
    public final List<String> recognizedPhonemes;

    public UtteranceScore(String targetText, int overallPercent,
                          List<WordScore> words, List<String> recognizedPhonemes) {
        this.targetText = targetText;
        this.overallPercent = overallPercent;
        this.words = words;
        this.recognizedPhonemes = recognizedPhonemes;
    }

    /** The words most worth practising again, worst first. */
    public WordScore weakestWord() {
        WordScore worst = null;
        for (WordScore w : words) {
            if (!w.isScorable()) continue;
            if (worst == null || w.percent < worst.percent) worst = w;
        }
        return worst;
    }

    public boolean isEmpty() {
        return recognizedPhonemes.isEmpty();
    }
}
