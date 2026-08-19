package com.example.pronunciation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Streak arithmetic only. The SharedPreferences side needs a device, but this is where the
 * off-by-one bugs live: practising twice in a day must not double-count, and a missed day must
 * reset rather than quietly continue.
 */
public class PracticeStatsTest {

    private static final long TODAY = 20000;  // arbitrary epoch day

    @Test
    public void firstEverPracticeStartsAtOne() {
        assertEquals(1, PracticeStats.nextStreak(0, Long.MIN_VALUE, TODAY));
    }

    @Test
    public void practisingAgainSameDayDoesNotIncrement() {
        assertEquals(3, PracticeStats.nextStreak(3, TODAY, TODAY));
    }

    @Test
    public void practisingNextDayExtendsStreak() {
        assertEquals(4, PracticeStats.nextStreak(3, TODAY - 1, TODAY));
    }

    @Test
    public void missingADayResetsToOne() {
        assertEquals(1, PracticeStats.nextStreak(9, TODAY - 2, TODAY));
    }

    @Test
    public void longAbsenceResetsToOne() {
        assertEquals(1, PracticeStats.nextStreak(30, TODAY - 400, TODAY));
    }

    /** Same-day practice with a zero streak still counts as day one, not zero. */
    @Test
    public void sameDayWithNoExistingStreakCountsAsOne() {
        assertEquals(1, PracticeStats.nextStreak(0, TODAY, TODAY));
    }

    @Test
    public void phonemeDescriptionsUseExampleWords() {
        assertEquals("θ as in think", Phonemes.describe("θ"));
        assertEquals("ʃ as in ship", Phonemes.describe("ʃ"));
    }

    @Test
    public void stressMarksDoNotHideTheExample() {
        assertEquals("think", Phonemes.exampleFor("ˈθ"));
    }

    @Test
    public void unknownPhonemeFallsBackToTheSymbol() {
        assertNull(Phonemes.exampleFor("qq"));
        assertEquals("qq", Phonemes.describe("qq"));
    }
}
