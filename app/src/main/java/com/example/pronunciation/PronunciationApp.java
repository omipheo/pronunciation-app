package com.example.pronunciation;

import android.app.Application;

import com.example.pronunciation.audio.TtsSpeaker;
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

    @Override
    public void onCreate() {
        super.onCreate();
        tts = new TtsSpeaker(this, null);
        // Warm the model up now so Section 2 is usable by the time the user navigates there.
        SpeechEngine.get(this).init();

        // Parsing ~2000 prompts is fast but not free; do it before the user opens Training
        // rather than blocking that first frame.
        new Thread(() -> LessonRepository.get(this), "corpus-warmup").start();
    }

    public TtsSpeaker tts() {
        return tts;
    }

    public static PronunciationApp from(android.content.Context context) {
        return (PronunciationApp) context.getApplicationContext();
    }
}
