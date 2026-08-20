package com.example.pronunciation.data;

/** One practice prompt in Section 2. */
public class Lesson {

    public enum Unit {
        WORD("Word"),
        SENTENCE("Sentence"),
        PARAGRAPH("Paragraph");

        public final String label;

        Unit(String label) {
            this.label = label;
        }
    }

    public final Unit unit;
    public final String text;
    /** What the prompt is drilling, shown as a subtitle. */
    public final String focus;
    /**
     * Romanisation shown under the prompt; null for languages that do not need one.
     *
     * <p>For Chinese this is pinyin, and it is generated with the prompt rather than derived
     * on the device — the reading of a polyphonic character depends on its context.
     */
    public final String romanisation;

    public Lesson(Unit unit, String text, String focus) {
        this(unit, text, focus, null);
    }

    public Lesson(Unit unit, String text, String focus, String romanisation) {
        this.unit = unit;
        this.text = text;
        this.focus = focus;
        this.romanisation = romanisation;
    }

    public boolean hasRomanisation() {
        return romanisation != null && !romanisation.trim().isEmpty();
    }
}
