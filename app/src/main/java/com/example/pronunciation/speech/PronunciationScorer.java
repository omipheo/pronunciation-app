package com.example.pronunciation.speech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compares the phonemes a learner produced against the phonemes the target text calls for.
 *
 * <p>Uses Levenshtein alignment over the whole utterance rather than per word, so a phoneme
 * dropped at a word boundary lands on the right word instead of wrecking both.
 */
public class PronunciationScorer {

    /** An inserted phoneme is half as bad as a missing one — extra noise beats a missing sound. */
    private static final double INSERTION_PENALTY = 0.5;

    private final Lexicon lexicon;

    public PronunciationScorer(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    public UtteranceScore score(String targetText, List<String> recognized) {
        List<String> words = Lexicon.tokenize(targetText);

        // Flatten the target into one phoneme stream, remembering which word each came from.
        List<String> expected = new ArrayList<>();
        List<Integer> ownerWord = new ArrayList<>();
        boolean[] scorable = new boolean[words.size()];

        for (int w = 0; w < words.size(); w++) {
            String[] phonemes = lexicon.lookup(words.get(w));
            if (phonemes == null) continue;

            scorable[w] = true;
            for (String p : phonemes) {
                expected.add(p);
                ownerWord.add(w);
            }
        }

        if (expected.isEmpty()) {
            List<WordScore> none = new ArrayList<>();
            for (String w : words) none.add(WordScore.unscorable(w));
            return new UtteranceScore(targetText, 0, none, recognized);
        }

        Alignment aligned = alignAndClassify(expected, ownerWord, recognized, words.size());

        List<WordScore> wordScores = new ArrayList<>();
        int totalCorrect = 0, totalExpected = 0, totalInsertions = 0;

        for (int w = 0; w < words.size(); w++) {
            if (!scorable[w]) {
                wordScores.add(WordScore.unscorable(words.get(w)));
                continue;
            }

            List<PhonemeScore> phonemes = aligned.perWord[w];
            int insertions = aligned.insertions[w];

            int correct = 0;
            for (PhonemeScore p : phonemes) {
                if (p.isCorrect()) correct++;
            }

            int percent = toPercent(correct, phonemes.size(), insertions);
            wordScores.add(new WordScore(words.get(w), phonemes, percent, insertions));

            totalCorrect += correct;
            totalExpected += phonemes.size();
            totalInsertions += insertions;
        }

        int overall = toPercent(totalCorrect, totalExpected, totalInsertions);
        return new UtteranceScore(targetText, overall, wordScores, recognized);
    }

    private static int toPercent(int correct, int expectedCount, int insertions) {
        if (expectedCount == 0) return -1;
        double effective = correct - INSERTION_PENALTY * insertions;
        double ratio = Math.max(0, effective) / expectedCount;
        return (int) Math.round(Math.min(1.0, ratio) * 100);
    }

    /** Alignment output: phonemes grouped by word, plus the extra phonemes charged to each. */
    private static class Alignment {
        final List<PhonemeScore>[] perWord;
        final int[] insertions;

        Alignment(List<PhonemeScore>[] perWord, int[] insertions) {
            this.perWord = perWord;
            this.insertions = insertions;
        }
    }

    @SuppressWarnings("unchecked")
    private Alignment alignAndClassify(List<String> expected, List<Integer> ownerWord,
                                       List<String> actual, int wordCount) {
        int n = expected.size();
        int m = actual.size();

        int[][] cost = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) cost[i][0] = i;
        for (int j = 0; j <= m; j++) cost[0][j] = j;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int sub = cost[i - 1][j - 1] + (equal(expected.get(i - 1), actual.get(j - 1)) ? 0 : 1);
                int del = cost[i - 1][j] + 1;
                int ins = cost[i][j - 1] + 1;
                cost[i][j] = Math.min(sub, Math.min(del, ins));
            }
        }

        List<PhonemeScore>[] perWord = new List[wordCount];
        for (int w = 0; w < wordCount; w++) perWord[w] = new ArrayList<>();

        int[] insertions = new int[wordCount];
        PhonemeScore[] results = new PhonemeScore[n];

        // Walk the DP table back to recover the operation applied to each expected phoneme.
        int i = n, j = m;
        while (i > 0 || j > 0) {
            String exp = i > 0 ? expected.get(i - 1) : null;
            String act = j > 0 ? actual.get(j - 1) : null;

            if (i > 0 && j > 0
                    && cost[i][j] == cost[i - 1][j - 1] + (equal(exp, act) ? 0 : 1)) {
                results[i - 1] = equal(exp, act)
                        ? new PhonemeScore(exp, act, PhonemeScore.Status.CORRECT)
                        : new PhonemeScore(exp, act, PhonemeScore.Status.SUBSTITUTED);
                i--;
                j--;
            } else if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) {
                results[i - 1] = new PhonemeScore(exp, null, PhonemeScore.Status.MISSING);
                i--;
            } else {
                // Extra phoneme: charge it to the word we are currently sitting inside.
                int owner = ownerWord.get(Math.min(Math.max(i - 1, 0), n - 1));
                insertions[owner]++;
                j--;
            }
        }

        for (int k = 0; k < n; k++) {
            perWord[ownerWord.get(k)].add(results[k]);
        }

        return new Alignment(perWord, insertions);
    }

    /** Ignores stress and length marks — this app scores sounds, not prosody. */
    private static boolean equal(String a, String b) {
        if (a == null || b == null) return false;
        return strip(a).equals(strip(b));
    }

    private static String strip(String p) {
        return p.replace("ˈ", "").replace("ˌ", "").replace("ː", "").trim();
    }

    /** Convenience for the game screen, which only needs a pass/fail verdict. */
    public static boolean isPass(UtteranceScore score, int threshold) {
        return score != null && score.overallPercent >= threshold;
    }

    public static List<String> emptyPhonemes() {
        return Collections.emptyList();
    }
}
