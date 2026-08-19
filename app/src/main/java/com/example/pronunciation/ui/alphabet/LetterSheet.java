package com.example.pronunciation.ui.alphabet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pronunciation.PronunciationApp;
import com.example.pronunciation.audio.TtsSpeaker;
import com.example.pronunciation.data.Alphabet;
import com.example.pronunciation.data.Letter;
import com.example.pronunciation.databinding.SheetLetterBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * The detail sheet for one letter: its name, the sound it makes, and an example word — each
 * playable, and each playable slowly.
 */
public class LetterSheet extends BottomSheetDialogFragment {

    private static final String ARG_LETTER = "letter";

    private SheetLetterBinding binding;
    private Letter letter;
    private boolean slow = false;

    public static LetterSheet newInstance(char upper) {
        LetterSheet sheet = new LetterSheet();
        Bundle args = new Bundle();
        args.putChar(ARG_LETTER, upper);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetLetterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        char upper = requireArguments().getChar(ARG_LETTER, 'A');
        letter = Alphabet.at(upper - 'A');

        binding.sheetGlyph.setText(letter.display());
        binding.sheetNameIpa.setText("/" + letter.nameIpa + "/");
        binding.sheetSoundIpa.setText("/" + letter.soundIpa + "/");
        binding.sheetExample.setText(letter.exampleWord);
        binding.sheetExampleIpa.setText("/" + letter.exampleIpa + "/");

        TtsSpeaker tts = PronunciationApp.from(requireContext()).tts();
        if (!tts.isReady()) {
            binding.sheetTtsWarning.setVisibility(View.VISIBLE);
        }

        // The letter name: TTS says "A" correctly when given the bare character.
        binding.playName.setOnClickListener(v -> say(tts, String.valueOf(letter.upper)));
        // No reliable way to make TTS utter a bare phoneme, so demonstrate it inside the example.
        binding.playSound.setOnClickListener(v -> say(tts, letter.exampleWord));
        binding.playExample.setOnClickListener(v -> say(tts, letter.exampleWord));

        binding.slowToggle.setOnCheckedChangeListener((b, checked) -> slow = checked);
    }

    private void say(TtsSpeaker tts, String text) {
        if (slow) {
            tts.speakSlowly(text);
        } else {
            tts.speak(text);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
