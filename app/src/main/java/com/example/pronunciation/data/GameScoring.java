package com.example.pronunciation.data;

/**
 * The rule that decides whether the fish moves.
 *
 * <p>Pulled out of the fragment so it can be tested without a device or a microphone. The
 * requirement is absolute: a sentence that is not pronounced well enough does not advance,
 * however many times it is attempted.
 */
public final class GameScoring {

    private GameScoring() {
    }

    /** Below this, the reader tries the sentence again. */
    public static final int PASS_THRESHOLD = 70;

    /** Floor so a hard-won pass is still worth something. */
    private static final int MINIMUM_POINTS = 10;

    public static boolean passes(int percent) {
        return percent >= PASS_THRESHOLD;
    }

    /**
     * Points for clearing a sentence, divided by how many attempts it took — reading it right
     * first time is worth more than grinding the same sentence down.
     *
     * @param attempts total attempts including the successful one; treated as at least 1
     * @return 0 if the attempt did not pass
     */
    public static int pointsFor(int percent, int attempts) {
        if (!passes(percent)) return 0;
        return Math.max(MINIMUM_POINTS, percent / Math.max(1, attempts));
    }
}
