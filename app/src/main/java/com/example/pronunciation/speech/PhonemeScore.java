package com.example.pronunciation.speech;

/** How one expected phoneme fared against what the speaker actually produced. */
public class PhonemeScore {

    public enum Status {
        /** Spoken as expected. */
        CORRECT,
        /** A different phoneme was produced in its place. */
        SUBSTITUTED,
        /** Not produced at all. */
        MISSING
    }

    public final String expected;
    /** What was heard instead; null when {@link #status} is not {@link Status#SUBSTITUTED}. */
    public final String actual;
    public final Status status;

    public PhonemeScore(String expected, String actual, Status status) {
        this.expected = expected;
        this.actual = actual;
        this.status = status;
    }

    public boolean isCorrect() {
        return status == Status.CORRECT;
    }
}
