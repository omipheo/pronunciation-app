package com.example.pronunciation.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.pronunciation.PronunciationApp;
import com.example.pronunciation.R;
import com.example.pronunciation.data.Language;
import com.example.pronunciation.ui.PracticeFocusViewModel;
import com.example.pronunciation.data.Phonemes;
import com.example.pronunciation.data.PracticeStats;
import com.example.pronunciation.databinding.FragmentHomeBinding;
import com.example.pronunciation.speech.SpeechEngine;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;

import java.time.LocalTime;

/**
 * The Main tab: where practice stands, which sounds need work, and a way into the other
 * sections.
 *
 * <p>Reads {@link PracticeStats} on every resume rather than observing it, because the only
 * things that change it are the other tabs, and coming back here is the moment the numbers
 * matter.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private PracticeStats stats;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        stats = new PracticeStats(requireContext());

        bindSectionCard(binding.linkTraining, R.drawable.ic_training,
                R.string.section_training, R.string.home_link_training, R.id.trainingFragment);
        bindSectionCard(binding.linkGame, R.drawable.ic_game,
                R.string.section_game, R.string.home_link_game, R.id.gameFragment);
        bindSectionCard(binding.linkAlphabet, R.drawable.ic_alphabet,
                R.string.section_alphabet, R.string.home_link_alphabet, R.id.alphabetFragment);

        bindLanguageToggle();

        SpeechEngine engine = SpeechEngine.get(requireContext());
        engine.init();
        engine.state().observe(getViewLifecycleOwner(), this::onEngineState);
    }

    private void bindLanguageToggle() {
        PronunciationApp app = PronunciationApp.from(requireContext());
        Language current = app.language();

        binding.languageToggle.check(
                current == Language.CHINESE ? R.id.language_zh : R.id.language_en);
        showLanguageNote(current);

        binding.languageToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            Language picked = checkedId == R.id.language_zh ? Language.CHINESE : Language.ENGLISH;
            if (picked == app.language()) return;

            app.setLanguage(picked);
            showLanguageNote(picked);
            // Reloading a 339 MB model is not instant; say so rather than look frozen.
            showToast(getString(R.string.language_switching, picked.label));
        });
    }

    /**
     * Chinese carries a caveat worth stating on the screen that switches to it: the bundled
     * model's vocabulary has no third tone, so tone is displayed but never scored.
     */
    private void showLanguageNote(Language language) {
        boolean show = language == Language.CHINESE;
        binding.languageNote.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) binding.languageNote.setText(R.string.language_note_zh);
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(requireContext(), message,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        render(stats.snapshot());
    }

    private void render(PracticeStats.Snapshot s) {
        binding.greeting.setText(greeting());
        binding.greetingSub.setText(subtitleFor(s));

        binding.statAttempts.tileValue.setText(s.isEmpty() ? "—" : String.valueOf(s.attempts));
        binding.statAttempts.tileLabel.setText(R.string.stat_attempts);

        binding.statAverage.tileValue.setText(
                s.averagePercent < 0 ? "—" : s.averagePercent + "%");
        binding.statAverage.tileLabel.setText(R.string.stat_average);

        binding.statStreak.tileValue.setText(
                s.streakDays == 0 ? "—" : String.valueOf(s.streakDays));
        binding.statStreak.tileLabel.setText(R.string.stat_streak);

        binding.emptyHint.setVisibility(s.isEmpty() ? View.VISIBLE : View.GONE);

        renderWeakSounds(s);
    }

    private void renderWeakSounds(PracticeStats.Snapshot s) {
        binding.weakChips.removeAllViews();

        if (s.weakest.isEmpty()) {
            binding.weakCard.setVisibility(View.GONE);
            return;
        }
        binding.weakCard.setVisibility(View.VISIBLE);

        for (PracticeStats.WeakSound weak : s.weakest) {
            Chip chip = new Chip(requireContext());
            chip.setText(getString(R.string.weak_sound_chip,
                    Phonemes.describe(weak.phoneme), weak.errorPercent));
            chip.setCheckable(false);
            // Tapping a weak sound takes you to Training with that sound already selected.
            chip.setOnClickListener(v -> {
                new ViewModelProvider(requireActivity())
                        .get(PracticeFocusViewModel.class)
                        .request(weak.phoneme);
                switchTo(R.id.trainingFragment);
            });
            binding.weakChips.addView(chip);
        }
    }

    private String subtitleFor(PracticeStats.Snapshot s) {
        if (s.isEmpty()) {
            return getString(R.string.home_sub_first_time);
        }
        if (s.streakDays >= 2) {
            return getString(R.string.home_sub_streak, s.streakDays);
        }
        return getString(R.string.home_sub_attempts, s.attempts);
    }

    private String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return getString(R.string.greeting_morning);
        if (hour < 18) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }

    private void onEngineState(SpeechEngine.State state) {
        boolean ready = state == SpeechEngine.State.READY;
        binding.engineBanner.setVisibility(ready ? View.GONE : View.VISIBLE);

        switch (state) {
            case LOADING:
                binding.engineBannerText.setText(R.string.engine_loading);
                break;
            case MODEL_MISSING:
                binding.engineBannerText.setText(R.string.engine_missing);
                break;
            case ERROR:
                binding.engineBannerText.setText(R.string.engine_error);
                break;
            default:
                break;
        }
    }

    private void bindSectionCard(com.example.pronunciation.databinding.ItemSectionCardBinding card,
                                 int iconRes, int titleRes, int subtitleRes, int destinationId) {
        card.cardIcon.setImageResource(iconRes);
        card.cardTitle.setText(titleRes);
        card.cardSubtitle.setText(subtitleRes);
        card.getRoot().setOnClickListener(v -> switchTo(destinationId));
    }

    /**
     * Moves to another tab through the bottom navigation rather than the NavController, so the
     * selected tab indicator stays in step with the screen.
     */
    private void switchTo(int destinationId) {
        BottomNavigationView nav = requireActivity().findViewById(R.id.bottom_nav);
        if (nav != null) nav.setSelectedItemId(destinationId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
