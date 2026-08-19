package com.example.pronunciation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The game must only advance on an accurate reading. These tests are the guard on that rule,
 * since nothing else in the suite can speak into a microphone.
 */
public class GameScoringTest {

    @Test
    public void doesNotAdvanceBelowThreshold() {
        assertFalse(GameScoring.passes(0));
        assertFalse(GameScoring.passes(50));
        assertFalse(GameScoring.passes(GameScoring.PASS_THRESHOLD - 1));
    }

    @Test
    public void advancesAtOrAboveThreshold() {
        assertTrue(GameScoring.passes(GameScoring.PASS_THRESHOLD));
        assertTrue(GameScoring.passes(85));
        assertTrue(GameScoring.passes(100));
    }

    @Test
    public void aFailedAttemptEarnsNothing() {
        assertEquals(0, GameScoring.pointsFor(69, 1));
        assertEquals(0, GameScoring.pointsFor(0, 5));
    }

    @Test
    public void firstTimePassScoresMoreThanARepeatedOne() {
        int once = GameScoring.pointsFor(90, 1);
        int thrice = GameScoring.pointsFor(90, 3);

        assertTrue("grinding must not pay as well as reading it right", once > thrice);
        assertEquals(90, once);
    }

    @Test
    public void aHardWonPassStillScoresSomething() {
        assertEquals(10, GameScoring.pointsFor(70, 50));
    }

    /** Callers pass an attempt counter; zero would divide by zero. */
    @Test
    public void zeroAttemptsIsTreatedAsOne() {
        assertEquals(GameScoring.pointsFor(80, 1), GameScoring.pointsFor(80, 0));
    }
}
