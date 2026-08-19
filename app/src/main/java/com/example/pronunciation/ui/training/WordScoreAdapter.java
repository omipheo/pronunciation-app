package com.example.pronunciation.ui.training;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pronunciation.databinding.ItemWordScoreBinding;
import com.example.pronunciation.speech.WordScore;
import com.example.pronunciation.ui.ScoreFormatter;

import java.util.ArrayList;
import java.util.List;

/** The per-word breakdown under a scored attempt. */
public class WordScoreAdapter extends RecyclerView.Adapter<WordScoreAdapter.WordHolder> {

    public interface OnWordClick {
        /** Replay just this word through TTS. */
        void onWord(WordScore word);
    }

    private final List<WordScore> words = new ArrayList<>();
    private final OnWordClick listener;

    public WordScoreAdapter(OnWordClick listener) {
        this.listener = listener;
    }

    public void submit(List<WordScore> newWords) {
        words.clear();
        if (newWords != null) words.addAll(newWords);
        notifyDataSetChanged();
    }

    public void clear() {
        submit(null);
    }

    @NonNull
    @Override
    public WordHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new WordHolder(ItemWordScoreBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull WordHolder holder, int position) {
        holder.bind(words.get(position));
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    class WordHolder extends RecyclerView.ViewHolder {

        private final ItemWordScoreBinding binding;

        WordHolder(ItemWordScoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WordScore word) {
            binding.wordText.setText(word.word);
            binding.getRoot().setOnClickListener(v -> listener.onWord(word));

            if (!word.isScorable()) {
                binding.wordIpa.setText("not in dictionary");
                binding.wordPercent.setText("—");
                binding.wordPercent.setTextColor(
                        ScoreFormatter.colourForPercent(itemView.getContext(), 100));
                binding.wordHint.setVisibility(View.GONE);
                binding.wordBar.setProgress(0);
                return;
            }

            binding.wordIpa.setText(ScoreFormatter.colouredIpa(itemView.getContext(), word));
            binding.wordPercent.setText(word.percent + "%");
            binding.wordPercent.setTextColor(
                    ScoreFormatter.colourForPercent(itemView.getContext(), word.percent));

            binding.wordBar.setProgress(word.percent);
            binding.wordBar.setIndicatorColor(
                    ScoreFormatter.colourForPercent(itemView.getContext(), word.percent));

            String hint = ScoreFormatter.substitutionHint(word);
            binding.wordHint.setVisibility(hint == null ? View.GONE : View.VISIBLE);
            binding.wordHint.setText(hint);
        }
    }
}
