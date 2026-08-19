package com.example.pronunciation.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.pronunciation.speech.Lexicon;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filtering prompts by a target sound. This is what makes a weak sound on the Main tab
 * actionable rather than decorative, so a silent empty result would be worse than useless.
 */
public class LessonFocusTest {

    /** Enough of the real lexicon to cover the bundled word prompts. */
    private static Lexicon lexicon() {
        Map<String, String[]> m = new HashMap<>();
        m.put("think", new String[]{"θ", "ɪ", "ŋ", "k"});
        m.put("this", new String[]{"ð", "ɪ", "s"});
        m.put("world", new String[]{"w", "ɚ", "l", "d"});
        m.put("really", new String[]{"ɹ", "ɪ", "l", "i"});
        m.put("very", new String[]{"v", "ɛ", "ɹ", "i"});
        m.put("ship", new String[]{"ʃ", "ɪ", "p"});
        m.put("cheap", new String[]{"tʃ", "i", "p"});
        m.put("beach", new String[]{"b", "iː", "tʃ"});
        m.put("rhythm", new String[]{"ɹ", "ɪ", "ð", "ə", "m"});
        m.put("comfortable", new String[]{"k", "ʌ", "m", "f", "t", "ə", "b", "ə", "l"});
        return Lexicon.inMemory(m);
    }

    @Test
    public void findsPromptsContainingTheSound() {
        List<Lesson> matches = Lessons.containingPhoneme(Lessons.curated(),"θ", lexicon());

        assertFalse(matches.isEmpty());
        for (Lesson l : matches) {
            assertTrue("expected a θ prompt, got: " + l.text,
                    l.text.toLowerCase().contains("th"));
        }
    }

    @Test
    public void returnsEmptyForASoundNoPromptUses() {
        assertTrue(Lessons.containingPhoneme(Lessons.curated(),"ʒ", lexicon()).isEmpty());
    }

    @Test
    public void ordersWordsBeforeSentencesBeforeParagraphs() {
        List<Lesson> matches = Lessons.containingPhoneme(Lessons.curated(),"ɪ", lexicon());

        int previous = -1;
        for (Lesson l : matches) {
            assertTrue("units must not go backwards", l.unit.ordinal() >= previous);
            previous = l.unit.ordinal();
        }
    }

    /** The model may emit "iː" where the lexicon holds "i"; they are the same sound to a learner. */
    @Test
    public void lengthMarksDoNotSplitASound() {
        assertEquals(
                Lessons.containingPhoneme(Lessons.curated(),"i", lexicon()).size(),
                Lessons.containingPhoneme(Lessons.curated(),"iː", lexicon()).size());
    }

    @Test
    public void stressMarksDoNotSplitASound() {
        assertEquals(
                Lessons.containingPhoneme(Lessons.curated(),"θ", lexicon()).size(),
                Lessons.containingPhoneme(Lessons.curated(),"ˈθ", lexicon()).size());
    }

    @Test
    public void handlesNullsAndUnloadedLexiconWithoutThrowing() {
        assertTrue(Lessons.containingPhoneme(Lessons.curated(),null, lexicon()).isEmpty());
        assertTrue(Lessons.containingPhoneme(Lessons.curated(),"θ", null).isEmpty());
        assertTrue(Lessons.containingPhoneme(Lessons.curated(),"", lexicon()).isEmpty());
        assertTrue(Lessons.containingPhoneme(null, "θ", lexicon()).isEmpty());
    }

    /** The pool is a parameter precisely so callers can search a subset. */
    @Test
    public void searchesOnlyTheGivenPool() {
        List<Lesson> pool = java.util.Collections.singletonList(
                new Lesson(Lesson.Unit.WORD, "this", "ð"));

        assertTrue(Lessons.containingPhoneme(pool, "θ", lexicon()).isEmpty());
        assertEquals(1, Lessons.containingPhoneme(pool, "ð", lexicon()).size());
    }

    /** Words absent from the dictionary must be skipped, not crash the filter. */
    @Test
    public void unknownWordsInPromptsAreSkipped() {
        Lexicon sparse = Lexicon.inMemory(
                java.util.Collections.singletonMap("think", new String[]{"θ", "ɪ", "ŋ", "k"}));

        List<Lesson> matches = Lessons.containingPhoneme(Lessons.curated(),"θ", sparse);
        assertFalse(matches.isEmpty());
    }
}
