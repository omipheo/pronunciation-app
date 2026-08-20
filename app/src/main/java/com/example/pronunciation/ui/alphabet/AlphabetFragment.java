package com.example.pronunciation.ui.alphabet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.pronunciation.PronunciationApp;
import com.example.pronunciation.R;
import com.example.pronunciation.data.Alphabet;
import com.example.pronunciation.data.Language;
import com.example.pronunciation.data.Syllables;
import com.example.pronunciation.databinding.FragmentAlphabetBinding;

/** Section 1 — the A-Z reference grid. Tapping a letter opens its sounds. */
public class AlphabetFragment extends Fragment {

    private FragmentAlphabetBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlphabetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 6 : 4;
        binding.letterGrid.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        binding.letterGrid.setHasFixedSize(true);
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) render();
    }

    /**
     * Chinese has no alphabet, so this section shows the pinyin building blocks instead:
     * initials, finals and the four tones.
     */
    private void render() {
        Language language = PronunciationApp.from(requireContext()).language();
        if (language == activeLanguage && binding.letterGrid.getAdapter() != null) return;
        activeLanguage = language;

        if (language == Language.CHINESE) {
            binding.header.setText(R.string.syllables_header);
            binding.letterGrid.setAdapter(new SyllableAdapter(
                    Syllables.get(requireContext()).all(),
                    entry -> PronunciationApp.from(requireContext()).tts()
                            .speak(entry.exampleHanzi)));
        } else {
            binding.header.setText(R.string.alphabet_header);
            binding.letterGrid.setAdapter(new AlphabetAdapter(Alphabet.all(), letter ->
                    LetterSheet.newInstance(letter.upper)
                            .show(getChildFragmentManager(), "letter")));
        }
    }

    private Language activeLanguage;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
