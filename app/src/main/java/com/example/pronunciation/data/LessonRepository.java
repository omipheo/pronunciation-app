package com.example.pronunciation.data;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The full practice corpus: the curated prompts in {@link Lessons} plus the generated ones in
 * {@code assets/prompts.tsv}.
 *
 * <p>The generated file holds a couple of thousand prompts, far too many for Java source, so it
 * is built by {@code tools/generate_content.py} and shipped as an asset. Curated prompts come
 * first in the pool because they target specific contrasts deliberately; the generated ones give
 * the corpus enough breadth that a session never repeats.
 */
public class LessonRepository {

    private static final String TAG = "LessonRepository";
    private static final String ASSET = "prompts.tsv";

    private static volatile LessonRepository instance;

    private final List<Lesson> all = new ArrayList<>();
    private final List<Lesson> words = new ArrayList<>();
    private final List<Lesson> sentences = new ArrayList<>();
    private final List<Lesson> paragraphs = new ArrayList<>();

    private LessonRepository(Context context) {
        all.addAll(Lessons.curated());
        loadGenerated(context);

        for (Lesson lesson : all) {
            switch (lesson.unit) {
                case WORD:
                    words.add(lesson);
                    break;
                case SENTENCE:
                    sentences.add(lesson);
                    break;
                case PARAGRAPH:
                    paragraphs.add(lesson);
                    break;
            }
        }
        Log.i(TAG, "Corpus: " + words.size() + " words, " + sentences.size()
                + " sentences, " + paragraphs.size() + " paragraphs");
    }

    /** Blocking on first call (~2000 lines of TSV). Warm it off the main thread at startup. */
    public static LessonRepository get(Context context) {
        if (instance == null) {
            synchronized (LessonRepository.class) {
                if (instance == null) instance = new LessonRepository(context.getApplicationContext());
            }
        }
        return instance;
    }

    private void loadGenerated(Context context) {
        try (InputStream in = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8), 32 * 1024)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) continue;

                Lesson.Unit unit;
                try {
                    unit = Lesson.Unit.valueOf(parts[0]);
                } catch (IllegalArgumentException e) {
                    continue;  // unknown unit in a regenerated file; skip rather than crash
                }
                all.add(new Lesson(unit, parts[1], parts[2]));
            }
        } catch (IOException e) {
            // Not fatal: the curated prompts alone still make a usable app.
            Log.w(TAG, "No " + ASSET + " bundled — run tools/generate_content.py", e);
        }
    }

    /** A fresh random draw. Callers get a different set each time. */
    public PracticeSession newSession(Random random) {
        return new PracticeSession(
                sample(words, PracticeSession.WORDS, random),
                sample(sentences, PracticeSession.SENTENCES, random),
                sample(paragraphs, PracticeSession.PARAGRAPHS, random));
    }

    private static List<Lesson> sample(List<Lesson> pool, int count, Random random) {
        if (pool.size() <= count) return new ArrayList<>(pool);

        List<Lesson> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return new ArrayList<>(copy.subList(0, count));
    }

    public List<Lesson> byUnit(Lesson.Unit unit) {
        switch (unit) {
            case SENTENCE:
                return Collections.unmodifiableList(sentences);
            case PARAGRAPH:
                return Collections.unmodifiableList(paragraphs);
            case WORD:
            default:
                return Collections.unmodifiableList(words);
        }
    }

    public List<Lesson> all() {
        return Collections.unmodifiableList(all);
    }

    /** Random sentences for one game round. */
    public List<Lesson> gameRound(int count, long seed) {
        return sample(sentences, count, new Random(seed));
    }
}
