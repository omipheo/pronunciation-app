package com.example.pronunciation.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Process-wide owner of the phoneme model, the lexicon and the scorer.
 *
 * <p>Loading costs hundreds of megabytes and several seconds, so it happens once and is shared by
 * Sections 2 and 3. Everything heavy runs on a single worker thread; results come back on the main
 * thread.
 */
public class SpeechEngine {

    private static final String TAG = "SpeechEngine";

    public enum State {
        /** Nothing attempted yet. */
        IDLE,
        LOADING,
        READY,
        /** No model bundled — see SETUP.md. Scoring UI should be disabled, not faked. */
        MODEL_MISSING,
        ERROR
    }

    public interface ScoreCallback {
        void onScored(UtteranceScore score);

        void onError(String message);
    }

    private static volatile SpeechEngine instance;

    private final Context appContext;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "speech-engine");
        t.setPriority(Thread.NORM_PRIORITY + 1);
        return t;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);

    private OnnxPhonemeRecognizer recognizer;
    private Lexicon lexicon;
    private PronunciationScorer scorer;

    private SpeechEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static SpeechEngine get(Context context) {
        if (instance == null) {
            synchronized (SpeechEngine.class) {
                if (instance == null) instance = new SpeechEngine(context);
            }
        }
        return instance;
    }

    public LiveData<State> state() {
        return state;
    }

    public boolean isReady() {
        return state.getValue() == State.READY;
    }

    /** Idempotent; safe to call from every screen's onCreate. */
    public void init() {
        State current = state.getValue();
        if (current == State.LOADING || current == State.READY) return;

        state.setValue(State.LOADING);
        worker.execute(() -> {
            if (!OnnxPhonemeRecognizer.isModelBundled(appContext)) {
                Log.w(TAG, "No phoneme model in assets — run tools/prepare_assets.py");
                mainHandler.post(() -> state.setValue(State.MODEL_MISSING));
                return;
            }

            Lexicon loadedLexicon = new Lexicon();
            boolean lexiconOk = loadedLexicon.load(appContext);

            OnnxPhonemeRecognizer loadedRecognizer = new OnnxPhonemeRecognizer(appContext);
            boolean modelOk = loadedRecognizer.load();

            if (!lexiconOk || !modelOk) {
                loadedRecognizer.close();
                mainHandler.post(() -> state.setValue(State.ERROR));
                return;
            }

            lexicon = loadedLexicon;
            recognizer = loadedRecognizer;
            scorer = new PronunciationScorer(loadedLexicon);
            mainHandler.post(() -> state.setValue(State.READY));
        });
    }

    /**
     * Recognises and scores one attempt. Returns immediately; {@code callback} fires on the main
     * thread.
     */
    public void score(String targetText, float[] samples, ScoreCallback callback) {
        if (!isReady()) {
            callback.onError("Pronunciation model is not loaded yet");
            return;
        }
        if (samples == null || samples.length == 0) {
            callback.onError("No audio was recorded");
            return;
        }

        worker.execute(() -> {
            try {
                List<String> phonemes = recognizer.recognize(samples);
                UtteranceScore result = scorer.score(targetText, phonemes);
                mainHandler.post(() -> callback.onScored(result));
            } catch (Exception e) {
                Log.e(TAG, "Scoring failed", e);
                mainHandler.post(() -> callback.onError("Could not analyse the recording"));
            }
        });
    }

    /** The expected IPA for a word, for display next to a prompt. Null if unknown or not loaded. */
    public String expectedIpa(String word) {
        if (lexicon == null) return null;
        String[] phonemes = lexicon.lookup(word);
        if (phonemes == null) return null;

        StringBuilder sb = new StringBuilder();
        for (String p : phonemes) sb.append(p);
        return sb.toString();
    }
}
