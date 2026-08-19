package com.example.pronunciation.data;

import java.util.Collections;
import java.util.List;

/**
 * One sitting's worth of practice: a small random draw from the full corpus.
 *
 * <p>The corpus runs to a couple of thousand prompts, which is unusable as a flat list — "1 / 1200"
 * is discouraging and nobody works through it in order. A session is what the learner actually
 * sees, and a fresh one is drawn every time they open Training.
 */
public class PracticeSession {

    public static final int WORDS = 10;
    public static final int SENTENCES = 8;
    public static final int PARAGRAPHS = 3;

    private final List<Lesson> words;
    private final List<Lesson> sentences;
    private final List<Lesson> paragraphs;

    PracticeSession(List<Lesson> words, List<Lesson> sentences, List<Lesson> paragraphs) {
        this.words = Collections.unmodifiableList(words);
        this.sentences = Collections.unmodifiableList(sentences);
        this.paragraphs = Collections.unmodifiableList(paragraphs);
    }

    public List<Lesson> forUnit(Lesson.Unit unit) {
        switch (unit) {
            case SENTENCE:
                return sentences;
            case PARAGRAPH:
                return paragraphs;
            case WORD:
            default:
                return words;
        }
    }

    public boolean isEmpty() {
        return words.isEmpty() && sentences.isEmpty() && paragraphs.isEmpty();
    }
}
