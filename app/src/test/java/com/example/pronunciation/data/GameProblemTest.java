package com.example.pronunciation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Sentence splitting and span arithmetic. The game highlights the current sentence inside the
 * full passage, so an off-by-one here paints the wrong text or crashes on a bad span.
 */
public class GameProblemTest {

    private static GameProblem problem() {
        return new GameProblem(3, "Travel · level 3", Arrays.asList(
                "One two. Three four five? Six!",
                "Seven eight. Nine ten."));
    }

    @Test
    public void splitsIntoSentencesAcrossParagraphs() {
        GameProblem p = problem();
        assertEquals(5, p.sentenceCount());
        assertEquals("One two.", p.sentences.get(0));
        assertEquals("Three four five?", p.sentences.get(1));
        assertEquals("Six!", p.sentences.get(2));
        assertEquals("Seven eight.", p.sentences.get(3));
        assertEquals("Nine ten.", p.sentences.get(4));
    }

    @Test
    public void paragraphsAreSeparatedByABlankLine() {
        assertTrue(problem().displayText.contains("Six!\n\nSeven eight."));
    }

    /** Every span must select exactly the sentence it belongs to, or the wrong text lights up. */
    @Test
    public void spansPointAtTheirOwnSentence() {
        GameProblem p = problem();
        assertEquals(p.sentenceCount(), p.spans.size());

        for (int i = 0; i < p.sentenceCount(); i++) {
            int[] span = p.spans.get(i);
            assertEquals(p.sentences.get(i), p.displayText.substring(span[0], span[1]));
        }
    }

    @Test
    public void spansStayInsideTheDisplayText() {
        GameProblem p = problem();
        for (int[] span : p.spans) {
            assertTrue(span[0] >= 0);
            assertTrue(span[1] <= p.displayText.length());
            assertTrue(span[0] < span[1]);
        }
    }

    @Test
    public void handlesASingleUnterminatedSentence() {
        GameProblem p = new GameProblem(1, "t", Collections.singletonList("No full stop here"));

        assertEquals(1, p.sentenceCount());
        assertEquals("No full stop here", p.sentences.get(0));
        assertEquals("No full stop here",
                p.displayText.substring(p.spans.get(0)[0], p.spans.get(0)[1]));
    }

    @Test
    public void countsWords() {
        assertEquals(10, problem().wordCount());
    }
}
