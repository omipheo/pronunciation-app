package com.example.pronunciation.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers the alignment logic, which is where the scoring can silently go wrong: a phoneme dropped
 * at a word boundary must be charged to one word, not smeared across two.
 */
public class PronunciationScorerTest {

    private PronunciationScorer scorer;

    @Before
    public void setUp() {
        Map<String, String[]> words = new HashMap<>();
        words.put("three", new String[]{"θ", "ɹ", "iː"});
        words.put("thin", new String[]{"θ", "ɪ", "n"});
        words.put("think", new String[]{"θ", "ɪ", "ŋ", "k"});
        words.put("sink", new String[]{"s", "ɪ", "ŋ", "k"});
        scorer = new PronunciationScorer(Lexicon.inMemory(words));
    }

    @Test
    public void perfectMatchScores100() {
        UtteranceScore score = scorer.score("think", Arrays.asList("θ", "ɪ", "ŋ", "k"));

        assertEquals(100, score.overallPercent);
        assertEquals(1, score.words.size());
        assertEquals(100, score.words.get(0).percent);
    }

    @Test
    public void substitutedPhonemeIsReportedWithWhatWasHeard() {
        // The classic θ -> s error: "think" said as "sink".
        UtteranceScore score = scorer.score("think", Arrays.asList("s", "ɪ", "ŋ", "k"));

        assertEquals(75, score.overallPercent);

        PhonemeScore first = score.words.get(0).phonemes.get(0);
        assertEquals(PhonemeScore.Status.SUBSTITUTED, first.status);
        assertEquals("θ", first.expected);
        assertEquals("s", first.actual);
    }

    @Test
    public void missingPhonemeIsMarkedMissingNotSubstituted() {
        UtteranceScore score = scorer.score("think", Arrays.asList("θ", "ɪ", "k"));

        List<PhonemeScore> phonemes = score.words.get(0).phonemes;
        assertEquals(4, phonemes.size());

        PhonemeScore missing = phonemes.get(2);
        assertEquals(PhonemeScore.Status.MISSING, missing.status);
        assertEquals("ŋ", missing.expected);
    }

    @Test
    public void errorInOneWordDoesNotDragDownTheOther() {
        // "three thin" with the second word's θ turned into s.
        UtteranceScore score = scorer.score("three thin",
                Arrays.asList("θ", "ɹ", "iː", "s", "ɪ", "n"));

        assertEquals(100, score.words.get(0).percent);
        assertEquals(67, score.words.get(1).percent);
    }

    @Test
    public void stressAndLengthMarksAreIgnored() {
        UtteranceScore score = scorer.score("three", Arrays.asList("ˈθ", "ɹ", "i"));

        assertEquals(100, score.overallPercent);
    }

    @Test
    public void unknownWordIsSkippedRatherThanCountedWrong() {
        UtteranceScore score = scorer.score("think zzzz", Arrays.asList("θ", "ɪ", "ŋ", "k"));

        assertEquals(2, score.words.size());
        assertTrue(score.words.get(0).isScorable());
        assertFalse(score.words.get(1).isScorable());
        // The unknown word must not pull the total down.
        assertEquals(100, score.overallPercent);
    }

    @Test
    public void silenceScoresZeroRatherThanCrashing() {
        UtteranceScore score = scorer.score("think", PronunciationScorer.emptyPhonemes());

        assertEquals(0, score.overallPercent);
        assertTrue(score.isEmpty());
    }

    @Test
    public void extraPhonemesArePenalisedButNotFatal() {
        // Every expected phoneme is present, plus two spurious ones.
        UtteranceScore score = scorer.score("think",
                Arrays.asList("θ", "ɪ", "ŋ", "k", "ə", "ə"));

        assertTrue(score.overallPercent < 100);
        assertTrue(score.overallPercent >= 50);
        assertEquals(2, score.words.get(0).insertions);
    }

    @Test
    public void punctuationDoesNotBreakLookup() {
        UtteranceScore score = scorer.score("Think,", Arrays.asList("θ", "ɪ", "ŋ", "k"));

        assertEquals(100, score.overallPercent);
        assertEquals("Think,", score.words.get(0).word);
    }
}
