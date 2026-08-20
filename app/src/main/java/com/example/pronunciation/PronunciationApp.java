package com.example.pronunciation;

import android.app.Application;

import com.example.pronunciation.audio.TtsSpeaker;
import com.example.pronunciation.data.Language;
import com.example.pronunciation.data.LanguagePreference;
import com.example.pronunciation.data.LessonRepository;
import com.example.pronunciation.speech.SpeechEngine;

/**
 * Owns the two expensive singletons: the TTS engine and the phoneme model.
 *
 * <p>Both are shared across all three sections. Creating a {@link TtsSpeaker} per fragment would
 * spin up a separate system TTS connection each time, and the ONNX session is far too large to
 * duplicate.
 */
public class PronunciationApp extends Application {

    private TtsSpeaker tts;
    private LanguagePreference languagePreference;

    @Override
    public void onCreate() {
        super.onCreate();
        languagePreference = new LanguagePreference(this);
        Language language = languagePreference.get();

        tts = new TtsSpeaker(this, language.locale, null);

        // Warm the model up now so Section 2 is usable by the time the user navigates there.
        SpeechEngine engine = SpeechEngine.get(this);
        engine.setLanguage(language);

        // Parsing the corpus is fast but not free; do it before the user opens Training
        // rather than blocking that first frame.
        new Thread(() -> LessonRepository.get(this, language), "corpus-warmup").start();
    }

    public TtsSpeaker tts() {
        return tts;
    }

    public LanguagePreference languagePreference() {
        return languagePreference;
    }

    /** Switches practice language: the model, the corpus and the voice all follow. */
    public void setLanguage(Language language) {
        languagePreference.set(language);
        SpeechEngine.get(this).setLanguage(language);
        tts.setLanguage(language.locale);
        new Thread(() -> LessonRepository.get(this, language), "corpus-warmup").start();
    }

    public Language language() {
        return languagePreference.get();
    }

    public static PronunciationApp from(android.content.Context context) {
        return (PronunciationApp) context.getApplicationContext();
    }
}
