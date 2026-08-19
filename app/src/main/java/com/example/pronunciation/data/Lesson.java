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

    public Lesson(Unit unit, String text, String focus) {
        this.unit = unit;
        this.text = text;
        this.focus = focus;
    }
}
