package com.example.pronunciation.speech;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * wav2vec2-style CTC phoneme recogniser running on ONNX Runtime.
 *
 * <p>Expects three assets produced by {@code tools/prepare_assets.py}:
 * <ul>
 *   <li>{@code phoneme_model.onnx} — quantised CTC model, input {@code [1, samples]} float32</li>
 *   <li>{@code vocab.json} — {@code {"token": id}} map straight from the tokenizer</li>
 *   <li>{@code model_config.json} — blank id, normalisation flag, tensor names</li>
 * </ul>
 */
public class OnnxPhonemeRecognizer implements PhonemeRecognizer {

    private static final String TAG = "OnnxPhonemeRecognizer";

    public static final String MODEL_ASSET = "phoneme_model.onnx";
    private static final String VOCAB_ASSET = "vocab.json";
    private static final String CONFIG_ASSET = "model_config.json";

    /** Tokenizer bookkeeping symbols that are never real phonemes. */
    private static final Set<String> SPECIAL_TOKENS = new HashSet<>(Arrays.asList(
            "<pad>", "<s>", "</s>", "<unk>", "|", "<blank>", " "));

    private final Context appContext;

    private OrtEnvironment env;
    private OrtSession session;
    private String[] idToToken = new String[0];
    private int blankId = 0;
    private boolean normalizeInput = true;
    private String inputName = "input_values";
    private volatile boolean ready = false;

    public OnnxPhonemeRecognizer(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** True when the model asset was bundled at build time. Cheap; safe on the main thread. */
    public static boolean isModelBundled(Context context) {
        try (InputStream in = context.getAssets().open(MODEL_ASSET)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Loads the model. Blocking and slow (hundreds of ms to seconds) — call from a worker thread.
     *
     * @return true if the recogniser is usable afterwards
     */
    public boolean load() {
        if (ready) return true;
        try {
            readConfig();
            readVocab();

            // ORT cannot read straight out of the APK, so stage the model in filesDir once.
            File modelFile = stageAsset(MODEL_ASSET);

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            session = env.createSession(modelFile.getAbsolutePath(), opts);

            // Trust the graph over the config file if they disagree about the input name.
            Iterator<String> inputs = session.getInputNames().iterator();
            if (inputs.hasNext()) inputName = inputs.next();

            ready = true;
            Log.i(TAG, "Loaded " + MODEL_ASSET + " (" + idToToken.length + " tokens, blank=" + blankId + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load phoneme model — Section 2/3 scoring stays disabled", e);
            close();
            return false;
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public List<String> recognize(float[] samples) throws Exception {
        if (!ready) throw new IllegalStateException("Model not loaded");
        if (samples == null || samples.length < SAMPLE_RATE / 10) {
            return Collections.emptyList();  // under 100 ms — nothing worth decoding
        }

        float[] input = normalizeInput ? zeroMeanUnitVariance(samples) : samples;

        long[] shape = {1, input.length};
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {

            float[][][] logits = (float[][][]) result.get(0).getValue();
            return ctcGreedyDecode(logits[0]);
        }
    }

    /** Argmax per frame, then collapse repeats and drop blanks — standard CTC greedy decoding. */
    private List<String> ctcGreedyDecode(float[][] frames) {
        List<String> out = new ArrayList<>();
        int previous = -1;

        for (float[] frame : frames) {
            int best = 0;
            for (int i = 1; i < frame.length; i++) {
                if (frame[i] > frame[best]) best = i;
            }

            if (best != previous && best != blankId) {
                String token = best < idToToken.length ? idToToken[best] : null;
                if (token != null && !SPECIAL_TOKENS.contains(token) && !token.trim().isEmpty()) {
                    out.add(token);
                }
            }
            previous = best;
        }
        return out;
    }

    /** wav2vec2 feature extractors are trained on normalised waveforms; skipping this wrecks accuracy. */
    private static float[] zeroMeanUnitVariance(float[] x) {
        double sum = 0;
        for (float v : x) sum += v;
        double mean = sum / x.length;

        double sq = 0;
        for (float v : x) {
            double d = v - mean;
            sq += d * d;
        }
        double std = Math.sqrt(sq / x.length) + 1e-7;

        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = (float) ((x[i] - mean) / std);
        }
        return out;
    }

    private void readConfig() throws Exception {
        JSONObject cfg = new JSONObject(readAssetText(CONFIG_ASSET));
        blankId = cfg.optInt("blank_id", 0);
        normalizeInput = cfg.optBoolean("normalize_input", true);
        inputName = cfg.optString("input_name", "input_values");
    }

    private void readVocab() throws Exception {
        JSONObject vocab = new JSONObject(readAssetText(VOCAB_ASSET));

        int max = -1;
        Iterator<String> keys = vocab.keys();
        while (keys.hasNext()) {
            max = Math.max(max, vocab.getInt(keys.next()));
        }

        idToToken = new String[max + 1];
        keys = vocab.keys();
        while (keys.hasNext()) {
            String token = keys.next();
            idToToken[vocab.getInt(token)] = token;
        }
    }

    private String readAssetText(String name) throws IOException {
        try (InputStream in = appContext.getAssets().open(name)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private File stageAsset(String name) throws IOException {
        File out = new File(appContext.getFilesDir(), name);

        // Assets are immutable per install, so a size match means the copy is current.
        try (InputStream probe = appContext.getAssets().open(name)) {
            if (out.exists() && out.length() > 0 && out.length() == probe.available()) {
                return out;
            }
        } catch (IOException ignored) {
            // available() is unreliable for >2GB assets; fall through and re-copy.
        }

        try (InputStream in = appContext.getAssets().open(name);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out;
    }

    @Override
    public void close() {
        ready = false;
        try {
            if (session != null) session.close();
        } catch (Exception e) {
            Log.w(TAG, "Error closing session", e);
        }
        session = null;
        env = null;
    }
}
