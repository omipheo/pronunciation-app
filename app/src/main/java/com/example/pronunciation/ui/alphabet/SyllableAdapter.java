package com.example.pronunciation.ui.alphabet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pronunciation.data.Syllables;
import com.example.pronunciation.databinding.ItemLetterBinding;

import java.util.List;

/**
 * The pinyin reference grid: initials, finals and tones, each with a character that carries
 * the sound. Reuses the letter tile so the two languages' reference sections look alike.
 */
public class SyllableAdapter extends RecyclerView.Adapter<SyllableAdapter.SyllableHolder> {

    public interface OnSyllableClick {
        void onSyllable(Syllables.Entry entry);
    }

    private final List<Syllables.Entry> entries;
    private final OnSyllableClick listener;

    public SyllableAdapter(List<Syllables.Entry> entries, OnSyllableClick listener) {
        this.entries = entries;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SyllableHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SyllableHolder(ItemLetterBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SyllableHolder holder, int position) {
        holder.bind(entries.get(position));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    class SyllableHolder extends RecyclerView.ViewHolder {

        private final ItemLetterBinding binding;

        SyllableHolder(ItemLetterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Syllables.Entry entry) {
            // Tones have no letter of their own, so the tone number stands in as the glyph.
            binding.letterGlyph.setText(entry.kind == Syllables.Kind.TONE
                    ? entry.exampleHanzi : entry.symbol);
            binding.letterLower.setText(entry.kind == Syllables.Kind.TONE
                    ? getToneLabel(entry.symbol) : entry.exampleHanzi);
            binding.letterIpa.setText(entry.examplePinyin);

            binding.getRoot().setContentDescription(
                    entry.symbol + ", " + entry.exampleHanzi + " " + entry.examplePinyin);
            binding.getRoot().setOnClickListener(v -> listener.onSyllable(entry));
        }

        private String getToneLabel(String symbol) {
            return "第" + symbol + "声";
        }
    }
}
