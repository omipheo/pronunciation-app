package com.example.pronunciation.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.pronunciation.speech.PhonemeRecognizer;

import java.io.ByteArrayOutputStream;

/**
 * Captures 16 kHz mono PCM straight from the mic and hands back normalised float samples.
 *
 * <p>Uses {@link MediaRecorder.AudioSource#VOICE_RECOGNITION} on purpose: the default MIC source
 * applies gain control and noise suppression that smear the very consonant detail we score on.
 */
public class AudioRecorder {

    private static final String TAG = "AudioRecorder";
    private static final int SAMPLE_RATE = PhonemeRecognizer.SAMPLE_RATE;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    /** Hard stop so a stuck recording cannot exhaust memory. */
    private static final int MAX_SECONDS = 30;

    public interface LevelListener {
        /** Current input loudness in [0, 1], delivered on the main thread ~20x/second. */
        void onLevel(float level);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioRecord record;
    private Thread thread;
    private volatile boolean recording = false;
    private ByteArrayOutputStream buffer;
    private LevelListener levelListener;

    public void setLevelListener(LevelListener listener) {
        this.levelListener = listener;
    }

    public boolean isRecording() {
        return recording;
    }

    /**
     * Begins capture. Caller must already hold RECORD_AUDIO.
     *
     * @return false if the mic could not be opened
     */
    @SuppressLint("MissingPermission")
    public boolean start() {
        if (recording) return true;

        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (minBuffer <= 0) {
            Log.e(TAG, "Unsupported audio config on this device");
            return false;
        }
        int bufferSize = minBuffer * 4;

        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, CHANNEL, ENCODING, bufferSize);
        } catch (SecurityException e) {
            Log.e(TAG, "RECORD_AUDIO not granted", e);
            return false;
        }

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            record = null;
            Log.e(TAG, "AudioRecord failed to initialise");
            return false;
        }

        buffer = new ByteArrayOutputStream(SAMPLE_RATE * 2 * 4);
        recording = true;
        record.startRecording();

        thread = new Thread(() -> readLoop(bufferSize), "audio-capture");
        thread.start();
        return true;
    }

    private void readLoop(int bufferSize) {
        byte[] chunk = new byte[bufferSize];
        int maxBytes = MAX_SECONDS * SAMPLE_RATE * 2;

        while (recording) {
            int read = record.read(chunk, 0, chunk.length);
            if (read <= 0) continue;

            buffer.write(chunk, 0, read);
            emitLevel(chunk, read);

            if (buffer.size() >= maxBytes) {
                Log.w(TAG, "Hit " + MAX_SECONDS + "s cap; stopping capture");
                recording = false;
            }
        }
    }

    private void emitLevel(byte[] chunk, int length) {
        LevelListener listener = levelListener;
        if (listener == null) return;

        long sum = 0;
        int samples = length / 2;
        for (int i = 0; i + 1 < length; i += 2) {
            short s = (short) ((chunk[i] & 0xFF) | (chunk[i + 1] << 8));
            sum += (long) s * s;
        }
        if (samples == 0) return;

        double rms = Math.sqrt((double) sum / samples) / Short.MAX_VALUE;
        float level = (float) Math.min(1.0, rms * 4);  // speech rarely exceeds ~0.25 RMS
        mainHandler.post(() -> listener.onLevel(level));
    }

    /**
     * Stops capture and returns everything recorded.
     *
     * @return mono samples in [-1, 1], or an empty array if nothing was captured
     */
    public float[] stop() {
        if (!recording && record == null) return new float[0];

        recording = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }

        if (record != null) {
            try {
                if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "stop() on a non-recording AudioRecord", e);
            }
            record.release();
            record = null;
        }

        byte[] pcm = buffer == null ? new byte[0] : buffer.toByteArray();
        buffer = null;
        return toFloat(pcm);
    }

    /** Abandons the recording without returning audio — used when a screen goes away mid-capture. */
    public void cancel() {
        stop();
    }

    private static float[] toFloat(byte[] pcm) {
        float[] out = new float[pcm.length / 2];
        for (int i = 0; i < out.length; i++) {
            short s = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
            out[i] = s / 32768f;
        }
        return out;
    }
}
