package com.example.pronunciation.data;

import com.example.pronunciation.speech.Lexicon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Practice content for Sections 2 and 3.
 *
 * <p>Grouped by the sound each prompt drills. The bias is deliberate: prompts lean on the
 * contrasts non-native speakers actually lose — θ/ð, l/ɹ, v/w, ʃ/tʃ/ʒ, ɪ/iː, æ/ʌ and the
 * unstressed schwa. A neutral sentence scores well without teaching anything.
 *
 * <p>Two constraints hold for every prompt, both checked by {@code tools/check_content.py}:
 * every word must exist in the bundled lexicon (an unknown word cannot be scored), and every
 * phoneme the model can emit must appear in enough prompts that "practise this sound" from the
 * Main tab lands somewhere useful.
 */
public final class Lessons {

    private Lessons() {
    }

    private static final List<Lesson> ALL = Collections.unmodifiableList(Arrays.asList(

            // ---- Words: θ, the sound most often replaced with s, t or f ----------------
            new Lesson(Lesson.Unit.WORD, "think", "θ — tongue tip between the teeth, not s"),
            new Lesson(Lesson.Unit.WORD, "three", "θ followed straight into ɹ"),
            new Lesson(Lesson.Unit.WORD, "thank", "θ at the start, before a vowel"),
            new Lesson(Lesson.Unit.WORD, "throw", "θɹ cluster"),
            new Lesson(Lesson.Unit.WORD, "month", "θ at the end, after a nasal"),
            new Lesson(Lesson.Unit.WORD, "teeth", "final θ — do not turn it into t"),
            new Lesson(Lesson.Unit.WORD, "healthy", "θ in the middle of a word"),
            new Lesson(Lesson.Unit.WORD, "birthday", "θ between two syllables"),
            new Lesson(Lesson.Unit.WORD, "something", "θ then ŋ"),
            new Lesson(Lesson.Unit.WORD, "thirsty", "θ then ɝ"),

            // ---- Words: ð, the voiced pair, often flattened to d or z ------------------
            new Lesson(Lesson.Unit.WORD, "this", "ð — voiced, you should feel the buzz"),
            new Lesson(Lesson.Unit.WORD, "that", "ð at the start, not d"),
            new Lesson(Lesson.Unit.WORD, "they", "ð before a diphthong"),
            new Lesson(Lesson.Unit.WORD, "mother", "ð in the middle"),
            new Lesson(Lesson.Unit.WORD, "weather", "ð between vowels"),
            new Lesson(Lesson.Unit.WORD, "together", "ð in an unstressed syllable"),
            new Lesson(Lesson.Unit.WORD, "breathe", "final ð, longer than you expect"),
            new Lesson(Lesson.Unit.WORD, "another", "ð after a schwa"),
            new Lesson(Lesson.Unit.WORD, "clothes", "ð then z, a hard ending"),

            // ---- Words: ɹ and l, the classic confusion --------------------------------
            new Lesson(Lesson.Unit.WORD, "really", "ɹ then l in one word"),
            new Lesson(Lesson.Unit.WORD, "world", "ɹ into l into d"),
            new Lesson(Lesson.Unit.WORD, "girl", "ɝ then a dark l"),
            new Lesson(Lesson.Unit.WORD, "learn", "l then ɝ then n"),
            new Lesson(Lesson.Unit.WORD, "library", "two ɹ sounds, one l"),
            new Lesson(Lesson.Unit.WORD, "parallel", "three l sounds"),
            new Lesson(Lesson.Unit.WORD, "regular", "ɹ at both ends"),
            new Lesson(Lesson.Unit.WORD, "little", "double l, swallowed middle"),
            new Lesson(Lesson.Unit.WORD, "problem", "ɹ inside a cluster"),
            new Lesson(Lesson.Unit.WORD, "rural", "the hardest ɹ word in English"),

            // ---- Words: v and w ------------------------------------------------------
            new Lesson(Lesson.Unit.WORD, "very", "v — teeth on lip, not w"),
            new Lesson(Lesson.Unit.WORD, "van", "v at the start"),
            new Lesson(Lesson.Unit.WORD, "wave", "w at the start, v at the end"),
            new Lesson(Lesson.Unit.WORD, "invite", "v in the middle"),
            new Lesson(Lesson.Unit.WORD, "wonderful", "w then a schwa"),
            new Lesson(Lesson.Unit.WORD, "water", "w then a long vowel"),
            new Lesson(Lesson.Unit.WORD, "valuable", "v then j"),
            new Lesson(Lesson.Unit.WORD, "wealth", "w then ɛ then lθ"),

            // ---- Words: ʃ, tʃ, dʒ, ʒ --------------------------------------------------
            new Lesson(Lesson.Unit.WORD, "ship", "ʃ — no hard start"),
            new Lesson(Lesson.Unit.WORD, "shoes", "ʃ then a long u"),
            new Lesson(Lesson.Unit.WORD, "machine", "ʃ spelled ch"),
            new Lesson(Lesson.Unit.WORD, "cheap", "tʃ — a hard start onto ʃ"),
            new Lesson(Lesson.Unit.WORD, "chair", "tʃ before a diphthong"),
            new Lesson(Lesson.Unit.WORD, "watch", "final tʃ"),
            new Lesson(Lesson.Unit.WORD, "kitchen", "tʃ in the middle"),
            new Lesson(Lesson.Unit.WORD, "jump", "dʒ — the voiced pair of tʃ"),
            new Lesson(Lesson.Unit.WORD, "language", "dʒ at the end"),
            new Lesson(Lesson.Unit.WORD, "measure", "ʒ, rare and easy to miss"),
            new Lesson(Lesson.Unit.WORD, "usually", "ʒ in an unstressed syllable"),
            new Lesson(Lesson.Unit.WORD, "decision", "ʒ before an n"),

            // ---- Words: ŋ -------------------------------------------------------------
            new Lesson(Lesson.Unit.WORD, "sing", "ŋ — no g sound after it"),
            new Lesson(Lesson.Unit.WORD, "morning", "ŋ at the end of an -ing"),
            new Lesson(Lesson.Unit.WORD, "strong", "ŋ after a cluster"),
            new Lesson(Lesson.Unit.WORD, "english", "ŋ then ɡ, both pronounced"),

            // ---- Words: vowel length and quality --------------------------------------
            new Lesson(Lesson.Unit.WORD, "beach", "long i — keep it long"),
            new Lesson(Lesson.Unit.WORD, "sheep", "long i versus the ɪ in ship"),
            new Lesson(Lesson.Unit.WORD, "sit", "short ɪ, relaxed"),
            new Lesson(Lesson.Unit.WORD, "seat", "long i, tenser than sit"),
            new Lesson(Lesson.Unit.WORD, "full", "ʊ, short and rounded"),
            new Lesson(Lesson.Unit.WORD, "food", "long u"),
            new Lesson(Lesson.Unit.WORD, "cat", "æ — wide and flat"),
            new Lesson(Lesson.Unit.WORD, "cut", "ʌ versus the æ in cat"),
            new Lesson(Lesson.Unit.WORD, "bad", "æ before a voiced stop"),
            new Lesson(Lesson.Unit.WORD, "bed", "ɛ versus the æ in bad"),
            new Lesson(Lesson.Unit.WORD, "walk", "ɔ, no l sound"),
            new Lesson(Lesson.Unit.WORD, "boy", "ɔɪ diphthong"),
            new Lesson(Lesson.Unit.WORD, "voice", "ɔɪ before s"),
            new Lesson(Lesson.Unit.WORD, "choice", "tʃ then ɔɪ"),
            new Lesson(Lesson.Unit.WORD, "enjoy", "ɔɪ at the end of a word"),
            new Lesson(Lesson.Unit.WORD, "house", "aʊ diphthong"),
            new Lesson(Lesson.Unit.WORD, "mouth", "aʊ then θ"),
            new Lesson(Lesson.Unit.WORD, "eight", "eɪ diphthong"),

            // ---- Words: the schwa and swallowed syllables ------------------------------
            new Lesson(Lesson.Unit.WORD, "comfortable", "the middle syllable disappears"),
            new Lesson(Lesson.Unit.WORD, "vegetable", "three syllables, not four"),
            new Lesson(Lesson.Unit.WORD, "chocolate", "two schwas, one stress"),
            new Lesson(Lesson.Unit.WORD, "temperature", "unstressed middle"),
            new Lesson(Lesson.Unit.WORD, "interesting", "three syllables in speech"),
            new Lesson(Lesson.Unit.WORD, "rhythm", "two syllables, no vowel letter in the second"),
            new Lesson(Lesson.Unit.WORD, "family", "schwa in the middle"),
            new Lesson(Lesson.Unit.WORD, "camera", "two schwas"),

            // ---- Words: consonant clusters ---------------------------------------------
            new Lesson(Lesson.Unit.WORD, "asked", "skt at the end"),
            new Lesson(Lesson.Unit.WORD, "texts", "ksts, the hardest cluster in English"),
            new Lesson(Lesson.Unit.WORD, "strength", "stɹ then ŋθ"),
            new Lesson(Lesson.Unit.WORD, "twelfth", "lfθ at the end"),
            new Lesson(Lesson.Unit.WORD, "desks", "sks at the end"),
            new Lesson(Lesson.Unit.WORD, "worlds", "ɹldz — do not drop the d"),

            // ---- Sentences: θ and ð ----------------------------------------------------
            new Lesson(Lesson.Unit.SENTENCE, "The three thin thieves left.", "repeated θ"),
            new Lesson(Lesson.Unit.SENTENCE, "That is the third time this month.", "θ and ð together"),
            new Lesson(Lesson.Unit.SENTENCE, "They both thought it was healthy.", "ð then θ"),
            new Lesson(Lesson.Unit.SENTENCE, "Thank you for the birthday card.", "θ across a phrase"),
            new Lesson(Lesson.Unit.SENTENCE, "My mother and father are together.", "three ð sounds"),
            new Lesson(Lesson.Unit.SENTENCE, "Breathe through your nose and think.", "ð and θ in one breath"),
            new Lesson(Lesson.Unit.SENTENCE, "There is something in the weather.", "ð, θ and ŋ"),

            // ---- Sentences: ɹ and l ----------------------------------------------------
            new Lesson(Lesson.Unit.SENTENCE, "Red lorry, yellow lorry.", "ɹ and l alternating"),
            new Lesson(Lesson.Unit.SENTENCE, "I really like the little library.", "ɹ and l in every word"),
            new Lesson(Lesson.Unit.SENTENCE, "The girl learned the rules early.", "ɝ, l and ɹ"),
            new Lesson(Lesson.Unit.SENTENCE, "All around the rural world.", "the hardest ɹ and l run"),
            new Lesson(Lesson.Unit.SENTENCE, "Please call me later.", "l at the start and end"),

            // ---- Sentences: v and w ----------------------------------------------------
            new Lesson(Lesson.Unit.SENTENCE, "The weather is very cold today.", "w versus v"),
            new Lesson(Lesson.Unit.SENTENCE, "We were very worried.", "w and v side by side"),
            new Lesson(Lesson.Unit.SENTENCE, "Every visitor was welcome.", "v then w"),

            // ---- Sentences: ʃ, tʃ, dʒ, s -----------------------------------------------
            new Lesson(Lesson.Unit.SENTENCE, "She sells sea shells by the shore.", "s versus ʃ"),
            new Lesson(Lesson.Unit.SENTENCE, "The children watched the machine.", "tʃ and ʃ"),
            new Lesson(Lesson.Unit.SENTENCE, "Just change the language settings.", "dʒ twice"),
            new Lesson(Lesson.Unit.SENTENCE, "I usually measure it first.", "ʒ twice"),
            new Lesson(Lesson.Unit.SENTENCE, "Which chair is cheaper?", "tʃ across a question"),

            // ---- Sentences: vowels ------------------------------------------------------
            new Lesson(Lesson.Unit.SENTENCE, "He caught a big fish in the river.", "short ɪ versus long i"),
            new Lesson(Lesson.Unit.SENTENCE, "The sheep is on the ship.", "ɪ against i directly"),
            new Lesson(Lesson.Unit.SENTENCE, "I would like a glass of water.", "l, w and the schwa"),
            new Lesson(Lesson.Unit.SENTENCE, "The cat sat on the mat.", "repeated æ"),
            new Lesson(Lesson.Unit.SENTENCE, "Put the book on the desk.", "ʊ then ɛ"),
            new Lesson(Lesson.Unit.SENTENCE, "Walk down the road and turn left.", "ɔ, oʊ and ɝ"),
            new Lesson(Lesson.Unit.SENTENCE, "The boy enjoys the noise.", "repeated ɔɪ"),
            new Lesson(Lesson.Unit.SENTENCE, "Raise your voice and make a choice.", "ɔɪ and eɪ"),
            new Lesson(Lesson.Unit.SENTENCE, "How now, brown cow.", "repeated aʊ"),

            // ---- Sentences: everyday phrases, natural rhythm ----------------------------
            new Lesson(Lesson.Unit.SENTENCE, "Please call me back this evening.", "long i, final ŋ"),
            new Lesson(Lesson.Unit.SENTENCE, "Could you say that again, please?", "weak forms in a question"),
            new Lesson(Lesson.Unit.SENTENCE, "I am looking forward to it.", "linking across words"),
            new Lesson(Lesson.Unit.SENTENCE, "What time does the train leave?", "question intonation"),
            new Lesson(Lesson.Unit.SENTENCE, "It is a comfortable temperature.", "two swallowed syllables"),
            new Lesson(Lesson.Unit.SENTENCE, "She asked for the texts yesterday.", "two hard clusters"),
            new Lesson(Lesson.Unit.SENTENCE, "The strength of the wind surprised us.", "ŋθ then cluster"),
            new Lesson(Lesson.Unit.SENTENCE, "My family lives in a small house.", "schwa and aʊ"),

            // ---- Paragraphs ---------------------------------------------------------------
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "Every morning I walk to the park near my house. "
                            + "The air is fresh and the birds are singing. "
                            + "I think it is the best part of my day.",
                    "connected speech at a natural pace"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "Learning a language takes time and patience. "
                            + "You will make mistakes, and that is completely normal. "
                            // "practising" is British; the lexicon is CMUdict, which is US English.
                            + "What matters is that you practice every single day.",
                    "longer phrases, weak forms"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "The weather changed quickly this afternoon. "
                            + "Thick grey clouds covered the whole sky, "
                            + "and within thirty minutes the rain was falling hard.",
                    "θ, ð and consonant clusters"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "My brother and I usually travel together in the summer. "
                            + "Last year we visited three different countries, "
                            + "and the whole trip was really worth the trouble.",
                    "ð, ɹ and l under load"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "She asked whether the machine was working properly. "
                            + "The engineer checked it twice, changed a small part, "
                            + "and told her it should be fine for another month.",
                    "ʃ, tʃ, dʒ and θ mixed"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "There is nothing better than a quiet evening at home. "
                            + "I make something warm to drink, find a comfortable chair, "
                            + "and read until I can no longer keep my eyes open.",
                    "θ, ð and swallowed syllables"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "The children were playing in the garden all afternoon. "
                            + "They built a small house out of boxes and old wood, "
                            + "then argued about who was allowed to go inside first.",
                    "ɹ, l and past-tense endings"),
            new Lesson(Lesson.Unit.PARAGRAPH,
                    "Good pronunciation is not about speaking quickly. "
                            + "It is about making every sound clear enough to be understood. "
                            + "Slow down, finish your words, and people will follow you easily.",
                    "clear final consonants")
    ));

    /** The hand-written prompts. The bulk of the corpus is generated; see {@link LessonRepository}. */
    public static List<Lesson> curated() {
        return ALL;
    }

    /**
     * Every prompt in {@code pool} whose expected pronunciation contains the given sound,
     * easiest unit first.
     *
     * <p>Backs the "practise this sound" path from the Main tab: a weak sound is only actionable
     * if it leads somewhere that actually drills it.
     *
     * <p>Takes the pool as an argument rather than reading a global so it can be tested against
     * a handful of prompts and a small in-memory lexicon.
     *
     * @return matching prompts, or an empty list if the sound appears in none of them
     */
    public static List<Lesson> containingPhoneme(List<Lesson> pool, String phoneme,
                                                 Lexicon lexicon) {
        List<Lesson> out = new ArrayList<>();
        if (pool == null || phoneme == null || lexicon == null || !lexicon.isLoaded()) return out;

        String target = Phonemes.normalize(phoneme);
        if (target.isEmpty()) return out;

        for (Lesson lesson : pool) {
            if (mentions(lesson.text, target, lexicon)) out.add(lesson);
        }

        // Words before sentences before paragraphs, so practice ramps up.
        Collections.sort(out, (a, b) -> a.unit.ordinal() - b.unit.ordinal());
        return out;
    }

    private static boolean mentions(String text, String target, Lexicon lexicon) {
        for (String word : Lexicon.tokenize(text)) {
            String[] phonemes = lexicon.lookup(word);
            if (phonemes == null) continue;

            for (String p : phonemes) {
                if (Phonemes.normalize(p).equals(target)) return true;
            }
        }
        return false;
    }

}
