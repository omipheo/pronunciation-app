package com.example.pronunciation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One game passage: several paragraphs, read aloud a sentence at a time.
 *
 * <p>The whole passage is shown so the reader has context, but scoring happens per sentence —
 * a 60-90 word recording would take far too long to run through the model, and a single bad
 * word would fail the lot.
 *
 * <p>Sentence spans are computed once here so the UI can colour the passage without re-parsing
 * on every frame.
 */
public class GameProblem {

    /** Splits on sentence-final punctuation, keeping the punctuation with the sentence. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    public final int level;
    public final String title;
    /** Paragraphs joined with blank lines, ready to display. */
    public final String displayText;
    public final List<String> sentences;
    /** {start, end} into {@link #displayText} for each entry in {@link #sentences}. */
    public final List<int[]> spans;

    public GameProblem(int level, String title, List<String> paragraphs) {
        this.level = level;
        this.title = title;

        StringBuilder text = new StringBuilder();
        List<String> found = new ArrayList<>();
        List<int[]> bounds = new ArrayList<>();

        for (int p = 0; p < paragraphs.size(); p++) {
            if (p > 0) text.append("\n\n");
            int paragraphStart = text.length();
            String paragraph = paragraphs.get(p).trim();
            text.append(paragraph);

            int cursor = 0;
            Matcher m = SENTENCE_END.matcher(paragraph);
            while (m.find()) {
                addSentence(found, bounds, paragraph, cursor, m.start(), paragraphStart);
                cursor = m.end();
            }
            addSentence(found, bounds, paragraph, cursor, paragraph.length(), paragraphStart);
        }

        this.displayText = text.toString();
        this.sentences = Collections.unmodifiableList(found);
        this.spans = Collections.unmodifiableList(bounds);
    }

    private static void addSentence(List<String> out, List<int[]> bounds, String paragraph,
                                    int from, int to, int offset) {
        String s = paragraph.substring(from, to).trim();
        if (s.isEmpty()) return;
        out.add(s);
        bounds.add(new int[]{offset + from, offset + from + s.length()});
    }

    public int sentenceCount() {
        return sentences.size();
    }

    public int wordCount() {
        return displayText.split("\\s+").length;
    }
}
