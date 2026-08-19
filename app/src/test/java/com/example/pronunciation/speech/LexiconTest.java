package com.example.pronunciation.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.Locale;

public class LexiconTest {

    private final Locale original = Locale.getDefault();

    @After
    public void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    public void stripsSurroundingPunctuationButKeepsApostrophes() {
        assertEquals("think", Lexicon.normalize("\"Think,\""));
        assertEquals("don't", Lexicon.normalize("don't."));
        assertEquals("hello", Lexicon.normalize("Hello!"));
    }

    @Test
    public void tokenizePreservesOriginalSpelling() {
        assertEquals(3, Lexicon.tokenize("The quick fox.").size());
        assertEquals("fox.", Lexicon.tokenize("The quick fox.").get(2));
    }

    /**
     * Turkish lowercases "I" to the dotless "ı", which is not in [a-z] and matches nothing in an
     * English dictionary. Normalisation must be locale-independent or every prompt containing
     * "I" silently drops out of scoring on a tr-TR device.
     */
    @Test
    public void turkishLocaleDoesNotBreakEnglishLookup() {
        Locale.setDefault(new Locale("tr", "TR"));

        assertEquals("i", Lexicon.normalize("I"));
        assertEquals("it", Lexicon.normalize("It"));
        assertEquals("i", Lexicon.normalize("I"));
    }

    @Test
    public void turkishLocaleStillResolvesAgainstALoadedLexicon() {
        Lexicon lexicon = Lexicon.inMemory(
                Collections.singletonMap("i", new String[]{"a", "ɪ"}));

        Locale.setDefault(new Locale("tr", "TR"));

        assertNotNull("\"I\" must resolve regardless of device locale", lexicon.lookup("I"));
    }
}
