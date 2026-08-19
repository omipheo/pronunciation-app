package com.example.pronunciation.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Static A-Z reference data for Section 1. */
public final class Alphabet {

    private Alphabet() {
    }

    private static final List<Letter> LETTERS = Collections.unmodifiableList(Arrays.asList(
            new Letter('A', "eɪ", "æ, eɪ", "apple", "ˈæpəl"),
            new Letter('B', "biː", "b", "ball", "bɔːl"),
            new Letter('C', "siː", "k, s", "cat", "kæt"),
            new Letter('D', "diː", "d", "dog", "dɒɡ"),
            new Letter('E', "iː", "ɛ, iː", "egg", "ɛɡ"),
            new Letter('F', "ɛf", "f", "fish", "fɪʃ"),
            new Letter('G', "dʒiː", "ɡ, dʒ", "goat", "ɡoʊt"),
            new Letter('H', "eɪtʃ", "h", "hat", "hæt"),
            new Letter('I', "aɪ", "ɪ, aɪ", "igloo", "ˈɪɡluː"),
            new Letter('J', "dʒeɪ", "dʒ", "jump", "dʒʌmp"),
            new Letter('K', "keɪ", "k", "kite", "kaɪt"),
            new Letter('L', "ɛl", "l", "lion", "ˈlaɪən"),
            new Letter('M', "ɛm", "m", "moon", "muːn"),
            new Letter('N', "ɛn", "n", "nose", "noʊz"),
            new Letter('O', "oʊ", "ɒ, oʊ", "orange", "ˈɒrɪndʒ"),
            new Letter('P', "piː", "p", "pen", "pɛn"),
            new Letter('Q', "kjuː", "kw", "queen", "kwiːn"),
            new Letter('R', "ɑːr", "r", "rain", "reɪn"),
            new Letter('S', "ɛs", "s, z", "sun", "sʌn"),
            new Letter('T', "tiː", "t", "tree", "triː"),
            new Letter('U', "juː", "ʌ, juː", "umbrella", "ʌmˈbrɛlə"),
            new Letter('V', "viː", "v", "van", "væn"),
            new Letter('W', "ˈdʌbəljuː", "w", "water", "ˈwɔːtər"),
            new Letter('X', "ɛks", "ks", "box", "bɒks"),
            new Letter('Y', "waɪ", "j, aɪ", "yellow", "ˈjɛloʊ"),
            new Letter('Z', "zɛd", "z", "zebra", "ˈziːbrə")
    ));

    public static List<Letter> all() {
        return LETTERS;
    }

    public static Letter at(int index) {
        return LETTERS.get(index);
    }
}
