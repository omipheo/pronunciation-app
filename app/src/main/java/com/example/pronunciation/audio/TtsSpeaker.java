package com.example.pronunciation.audio;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Thin wrapper over Android's built-in TTS, used for the model answer in every section.
 *
 * <p>Works offline once the user has US English voice data installed; {@link #isReady()} stays
 * false otherwise so screens can point them at the system settings instead of failing silently.
 */
public class TtsSpeaker {

    private static final String TAG = "TtsSpeaker";
    private static final String UTTERANCE_ID = "pronunciation";

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private float rate = 1.0f;

    public interface ReadyListener {
        void onReady(boolean available);
    }

    public TtsSpeaker(Context context, ReadyListener listener) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                ready = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
                if (!ready) Log.w(TAG, "US English voice data is not installed");
            } else {
                Log.e(TAG, "TTS init failed with status " + status);
                ready = false;
            }
            if (listener != null) listener.onReady(ready);
        });
    }

    public boolean isReady() {
        return ready;
    }

    public void speak(String text) {
        if (!ready || text == null || text.trim().isEmpty()) return;
        tts.setSpeechRate(rate);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
    }

    /** Half speed, for hearing individual sounds in a long word. */
    public void speakSlowly(String text) {
        float previous = rate;
        rate = 0.5f;
        speak(text);
        rate = previous;
    }

    public void stop() {
        if (tts != null) tts.stop();
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
