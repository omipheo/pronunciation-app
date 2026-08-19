package com.example.pronunciation.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.pronunciation.speech.PhonemeScore;
import com.example.pronunciation.speech.UtteranceScore;
import com.example.pronunciation.speech.WordScore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Running record of how practice is going, shown on the Main tab.
 *
 * <p>Deliberately small: counters in {@link SharedPreferences} rather than a database. The only
 * per-phoneme state is an error count and a seen count, which is enough to rank which sounds
 * need work without storing every attempt.
 */
public class PracticeStats {

    private static final String PREFS = "practice";

    private static final String KEY_ATTEMPTS = "attempts";
    private static final String KEY_SCORE_SUM = "score_sum";
    private static final String KEY_BEST = "best_percent";
    private static final String KEY_STREAK = "streak_days";
    private static final String KEY_LAST_DAY = "last_practice_day";

    private static final String PREFIX_ERRORS = "err_";
    private static final String PREFIX_SEEN = "seen_";

    /** Below this many observations a phoneme's error rate is too noisy to rank on. */
    private static final int MIN_OBSERVATIONS = 4;

    private static final int MAX_WEAK_SOUNDS = 4;

    private final SharedPreferences prefs;

    public PracticeStats(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Folds one scored attempt into the totals. Cheap; safe to call on the main thread. */
    public void record(UtteranceScore score) {
        if (score == null || score.isEmpty()) return;

        SharedPreferences.Editor editor = prefs.edit();

        int attempts = prefs.getInt(KEY_ATTEMPTS, 0) + 1;
        editor.putInt(KEY_ATTEMPTS, attempts);
        editor.putInt(KEY_SCORE_SUM, prefs.getInt(KEY_SCORE_SUM, 0) + score.overallPercent);
        editor.putInt(KEY_BEST, Math.max(prefs.getInt(KEY_BEST, 0), score.overallPercent));

        long today = todayEpochDay();
        editor.putInt(KEY_STREAK,
                nextStreak(prefs.getInt(KEY_STREAK, 0), prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE), today));
        editor.putLong(KEY_LAST_DAY, today);

        for (WordScore word : score.words) {
            for (PhonemeScore phoneme : word.phonemes) {
                String p = phoneme.expected;
                editor.putInt(PREFIX_SEEN + p, prefs.getInt(PREFIX_SEEN + p, 0) + 1);
                if (!phoneme.isCorrect()) {
                    editor.putInt(PREFIX_ERRORS + p, prefs.getInt(PREFIX_ERRORS + p, 0) + 1);
                }
            }
        }

        editor.apply();
    }

    /**
     * Streak rules: practising again the same day changes nothing, the next day extends the
     * streak, and any longer gap starts over at one.
     *
     * <p>Static and parameterised so it can be tested without a device clock.
     */
    public static int nextStreak(int currentStreak, long lastPracticeDay, long today) {
        if (lastPracticeDay == today) {
            return Math.max(currentStreak, 1);
        }
        if (lastPracticeDay == today - 1) {
            return currentStreak + 1;
        }
        return 1;
    }

    public Snapshot snapshot() {
        int attempts = prefs.getInt(KEY_ATTEMPTS, 0);
        int average = attempts == 0 ? -1 : prefs.getInt(KEY_SCORE_SUM, 0) / attempts;

        // A streak only counts if it was touched today or yesterday; otherwise it has lapsed.
        int streak = prefs.getInt(KEY_STREAK, 0);
        long last = prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE);
        long today = todayEpochDay();
        if (last != today && last != today - 1) streak = 0;

        return new Snapshot(attempts, average, streak, prefs.getInt(KEY_BEST, 0), weakestSounds());
    }

    /** The sounds most often got wrong, worst first, ignoring ones seen too few times. */
    private List<WeakSound> weakestSounds() {
        List<WeakSound> out = new ArrayList<>();

        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith(PREFIX_SEEN)) continue;

            String phoneme = key.substring(PREFIX_SEEN.length());
            int seen = prefs.getInt(key, 0);
            if (seen < MIN_OBSERVATIONS) continue;

            int errors = prefs.getInt(PREFIX_ERRORS + phoneme, 0);
            if (errors == 0) continue;

            out.add(new WeakSound(phoneme, errors * 100 / seen, seen));
        }

        Collections.sort(out, (a, b) -> b.errorPercent - a.errorPercent);
        return out.size() > MAX_WEAK_SOUNDS ? out.subList(0, MAX_WEAK_SOUNDS) : out;
    }

    public void reset() {
        prefs.edit().clear().apply();
    }

    private static long todayEpochDay() {
        return LocalDate.now().toEpochDay();
    }

    /** Immutable view of the counters, for rendering. */
    public static class Snapshot {
        public final int attempts;
        /** 0-100, or -1 when nothing has been practised yet. */
        public final int averagePercent;
        public final int streakDays;
        public final int bestPercent;
        public final List<WeakSound> weakest;

        Snapshot(int attempts, int averagePercent, int streakDays, int bestPercent,
                 List<WeakSound> weakest) {
            this.attempts = attempts;
            this.averagePercent = averagePercent;
            this.streakDays = streakDays;
            this.bestPercent = bestPercent;
            this.weakest = weakest;
        }

        public boolean isEmpty() {
            return attempts == 0;
        }
    }

    public static class WeakSound {
        public final String phoneme;
        public final int errorPercent;
        public final int occurrences;

        WeakSound(String phoneme, int errorPercent, int occurrences) {
            this.phoneme = phoneme;
            this.errorPercent = errorPercent;
            this.occurrences = occurrences;
        }
    }
}
