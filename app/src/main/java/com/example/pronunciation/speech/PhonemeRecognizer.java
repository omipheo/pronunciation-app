package com.example.pronunciation.speech;

import java.io.Closeable;
import java.util.List;

/**
 * Turns raw audio into the sequence of phonemes the speaker actually produced.
 *
 * <p>Deliberately not a transcriber: we want what was said, not what was meant. A word-level
 * ASR model will happily "correct" a mispronunciation and report success, which is exactly
 * the failure mode this app exists to avoid.
 */
public interface PhonemeRecognizer extends Closeable {

    /** Sample rate every implementation expects. */
    int SAMPLE_RATE = 16000;

    /**
     * @param samples mono PCM in [-1, 1] at {@link #SAMPLE_RATE}
     * @return IPA phoneme symbols in the order they were spoken; empty if nothing was heard
     */
    List<String> recognize(float[] samples) throws Exception;

    /** False until the model has been loaded (or if the model asset is missing entirely). */
    boolean isReady();
}
