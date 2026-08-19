package com.example.pronunciation.data;

/** One entry in the A-Z reference: how the letter is named, and the sound(s) it makes. */
public class Letter {

    public final char upper;
    /** How the letter is *called*, e.g. A = /eɪ/. */
    public final String nameIpa;
    /** The sound(s) it commonly *makes* in words, e.g. A = /æ/, /eɪ/. */
    public final String soundIpa;
    public final String exampleWord;
    public final String exampleIpa;

    public Letter(char upper, String nameIpa, String soundIpa, String exampleWord, String exampleIpa) {
        this.upper = upper;
        this.nameIpa = nameIpa;
        this.soundIpa = soundIpa;
        this.exampleWord = exampleWord;
        this.exampleIpa = exampleIpa;
    }

    public char lower() {
        return Character.toLowerCase(upper);
    }

    public String display() {
        return upper + " " + lower();
    }
}
