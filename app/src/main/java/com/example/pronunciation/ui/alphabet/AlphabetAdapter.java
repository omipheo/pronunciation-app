package com.example.pronunciation.ui.alphabet;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pronunciation.data.Letter;
import com.example.pronunciation.databinding.ItemLetterBinding;

import java.util.List;

public class AlphabetAdapter extends RecyclerView.Adapter<AlphabetAdapter.LetterHolder> {

    public interface OnLetterClick {
        void onLetter(Letter letter);
    }

    private final List<Letter> letters;
    private final OnLetterClick listener;

    public AlphabetAdapter(List<Letter> letters, OnLetterClick listener) {
        this.letters = letters;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return letters.get(position).upper;
    }

    @NonNull
    @Override
    public LetterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLetterBinding binding = ItemLetterBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LetterHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LetterHolder holder, int position) {
        holder.bind(letters.get(position));
    }

    @Override
    public int getItemCount() {
        return letters.size();
    }

    class LetterHolder extends RecyclerView.ViewHolder {

        private final ItemLetterBinding binding;

        LetterHolder(ItemLetterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Letter letter) {
            binding.letterGlyph.setText(String.valueOf(letter.upper));
            binding.letterLower.setText(String.valueOf(letter.lower()));
            binding.letterIpa.setText("/" + letter.nameIpa + "/");
            binding.getRoot().setContentDescription(
                    "Letter " + letter.upper + ", example " + letter.exampleWord);
            binding.getRoot().setOnClickListener(v -> listener.onLetter(letter));
        }
    }
}
