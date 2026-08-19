package com.example.pronunciation.data;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The game's passage bank, loaded from {@code assets/game_problems.tsv}.
 *
 * <p>Problems are banded into {@link #LEVELS} difficulty levels by the generator, scored on how
 * much effort each word costs — hard phonemes, consonant clusters and syllable count — rather
 * than on length alone. Round N of a game draws at random from level N.
 */
public class GameProblems {

    private static final String TAG = "GameProblems";
    private static final String ASSET = "game_problems.tsv";
    private static final String PARAGRAPH_SEPARATOR = "\\|\\|";

    public static final int LEVELS = 10;

    private static volatile GameProblems instance;

    /** Index 0 is level 1. */
    private final List<List<GameProblem>> byLevel = new ArrayList<>();

    private GameProblems(Context context) {
        for (int i = 0; i < LEVELS; i++) byLevel.add(new ArrayList<>());
        load(context);

        StringBuilder counts = new StringBuilder();
        for (int i = 0; i < LEVELS; i++) counts.append(" L").append(i + 1).append("=").append(byLevel.get(i).size());
        Log.i(TAG, "Loaded game problems:" + counts);
    }

    public static GameProblems get(Context context) {
        if (instance == null) {
            synchronized (GameProblems.class) {
                if (instance == null) instance = new GameProblems(context.getApplicationContext());
            }
        }
        return instance;
    }

    private void load(Context context) {
        try (InputStream in = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8), 32 * 1024)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) continue;

                int level;
                try {
                    level = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (level < 1 || level > LEVELS) continue;

                List<String> paragraphs = Arrays.asList(parts[2].split(PARAGRAPH_SEPARATOR));
                byLevel.get(level - 1).add(new GameProblem(level, parts[1], paragraphs));
            }
        } catch (IOException e) {
            Log.w(TAG, "No " + ASSET + " bundled — run tools/generate_content.py", e);
        }
    }

    /**
     * A random passage for the given level, falling back to the nearest level that has any.
     *
     * @return null only if no problems loaded at all
     */
    public GameProblem randomForLevel(int level, Random random) {
        for (int distance = 0; distance < LEVELS; distance++) {
            for (int candidate : new int[]{level - distance, level + distance}) {
                if (candidate < 1 || candidate > LEVELS) continue;
                List<GameProblem> pool = byLevel.get(candidate - 1);
                if (!pool.isEmpty()) return pool.get(random.nextInt(pool.size()));
            }
        }
        return null;
    }

    public int total() {
        int n = 0;
        for (List<GameProblem> pool : byLevel) n += pool.size();
        return n;
    }

    public List<GameProblem> atLevel(int level) {
        if (level < 1 || level > LEVELS) return Collections.emptyList();
        return Collections.unmodifiableList(byLevel.get(level - 1));
    }
}
