package com.example.pronunciation.data;

import java.util.Locale;

/**
 * Which language the learner is practising.
 *
 * <p>Each carries its own model, lexicon and content. The asset suffix is empty for English so
 * the original filenames — and anything already installed — keep working.
 */
public enum Language {

    ENGLISH("en", "", "English", Locale.US),
    CHINESE("zh", "_zh", "中文", Locale.SIMPLIFIED_CHINESE);

    public final String code;
    /** Appended to asset stems: {@code phoneme_model.onnx} vs {@code phoneme_model_zh.onnx}. */
    public final String suffix;
    /** Shown in the picker, in the language itself rather than translated. */
    public final String label;
    /** For text-to-speech. */
    public final Locale locale;

    Language(String code, String suffix, String label, Locale locale) {
        this.code = code;
        this.suffix = suffix;
        this.label = label;
        this.locale = locale;
    }

    public String asset(String stem, String extension) {
        return stem + suffix + "." + extension;
    }

    /** Chinese is written without spaces, so prompts are split per character rather than per word. */
    public boolean splitsByCharacter() {
        return this == CHINESE;
    }

    /**
     * Mandarin tone is meaning-bearing but the bundled model's vocabulary has no tone 3 at all
     * and covers the rest unevenly, so tone is shown to the learner and left out of scoring.
     */
    public boolean scoresTone() {
        return false;
    }

    public static Language fromCode(String code) {
        for (Language l : values()) {
            if (l.code.equals(code)) return l;
        }
        return ENGLISH;
    }
}
