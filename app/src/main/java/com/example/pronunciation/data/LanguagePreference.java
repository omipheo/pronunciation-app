package com.example.pronunciation.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The learner's chosen practice language, remembered across launches.
 *
 * <p>Deliberately separate from the device locale: someone may well read a Chinese interface
 * while practising English, or the reverse.
 */
public class LanguagePreference {

    private static final String PREFS = "language";
    private static final String KEY = "practice_language";

    private final SharedPreferences prefs;

    public LanguagePreference(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Language get() {
        return Language.fromCode(prefs.getString(KEY, Language.ENGLISH.code));
    }

    public void set(Language language) {
        prefs.edit().putString(KEY, language.code).apply();
    }
}
