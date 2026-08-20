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

/**
 * The pinyin table that stands in for A–Z when practising Chinese.
 *
 * <p>Chinese has no alphabet, so the reference section shows what actually varies: the
 * initials (声母), the finals (韵母) and the four tones, each with a character that carries it.
 */
public class Syllables {

    private static final String TAG = "Syllables";
    private static final String ASSET = "zh_syllables.tsv";

    public enum Kind {INITIAL, FINAL, TONE}

    public static class Entry {
        public final Kind kind;
        /** "zh", "iang", or a tone number. */
        public final String symbol;
        public final String exampleHanzi;
        public final String examplePinyin;
        /** How to make the sound; empty for finals, where the example carries it. */
        public final String note;

        Entry(Kind kind, String symbol, String hanzi, String pinyin, String note) {
            this.kind = kind;
            this.symbol = symbol;
            this.exampleHanzi = hanzi;
            this.examplePinyin = pinyin;
            this.note = note;
        }

        public boolean hasNote() {
            return note != null && !note.trim().isEmpty();
        }
    }

    private static volatile Syllables instance;
    private final List<Entry> entries = new ArrayList<>();

    private Syllables(Context context) {
        try (InputStream in = context.getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\t", 5);
                if (p.length < 4) continue;
                try {
                    entries.add(new Entry(Kind.valueOf(p[0]), p[1], p[2], p[3],
                            p.length >= 5 ? p[4] : ""));
                } catch (IllegalArgumentException ignored) {
                    // Unknown kind in a regenerated file; skip rather than crash.
                }
            }
            Log.i(TAG, "Loaded " + entries.size() + " syllable rows");
        } catch (IOException e) {
            Log.w(TAG, "No " + ASSET + " bundled — run tools/generate_content_zh.py", e);
        }
    }

    public static Syllables get(Context context) {
        if (instance == null) {
            synchronized (Syllables.class) {
                if (instance == null) instance = new Syllables(context.getApplicationContext());
            }
        }
        return instance;
    }

    public List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }

    public List<Entry> ofKind(Kind kind) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.kind == kind) out.add(e);
        }
        return out;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
