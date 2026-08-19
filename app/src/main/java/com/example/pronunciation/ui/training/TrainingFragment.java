package com.example.pronunciation.ui.training;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pronunciation.R;
import com.example.pronunciation.data.Lesson;
import com.example.pronunciation.data.Lessons;
import com.example.pronunciation.data.Phonemes;
import com.example.pronunciation.ui.PracticeFocusViewModel;
import com.example.pronunciation.databinding.FragmentTrainingBinding;
import com.example.pronunciation.speech.SpeechEngine;
import com.example.pronunciation.speech.UtteranceScore;
import com.example.pronunciation.ui.RecordingFragment;
import com.example.pronunciation.ui.ScoreFormatter;

import java.util.List;

/**
 * Section 2 — read the prompt, get scored on it, see which sounds were wrong.
 *
 * <p>Prompts are grouped by unit (word / sentence / paragraph) so the learner can scale up from a
 * single tricky sound to connected speech.
 */
public class TrainingFragment extends RecordingFragment {

    private FragmentTrainingBinding binding;
    private WordScoreAdapter scoreAdapter;

    private List<Lesson> lessons = Lessons.byUnit(Lesson.Unit.WORD);
    private int index = 0;
    private boolean busy = false;
    /** Non-null while drilling one sound picked from the Main tab. */
    private String focusPhoneme;
    /** Requested before the lexicon finished loading; applied once the engine is ready. */
    private String pendingFocus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTrainingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        scoreAdapter = new WordScoreAdapter(word -> tts().speak(word.word));
        binding.wordScores.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.wordScores.setAdapter(scoreAdapter);

        binding.unitChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || focusPhoneme != null) return;
            int id = checkedIds.get(0);
            Lesson.Unit unit = id == R.id.chip_sentence ? Lesson.Unit.SENTENCE
                    : id == R.id.chip_paragraph ? Lesson.Unit.PARAGRAPH
                    : Lesson.Unit.WORD;
            lessons = Lessons.byUnit(unit);
            index = 0;
            showLesson();
        });

        binding.focusClear.setOnClickListener(v -> clearFocus());

        binding.listenButton.setOnClickListener(v -> tts().speak(current().text));
        binding.listenSlowButton.setOnClickListener(v -> tts().speakSlowly(current().text));
        binding.nextButton.setOnClickListener(v -> {
            index = (index + 1) % lessons.size();
            showLesson();
        });
        binding.recordButton.setOnClickListener(v -> toggleRecording());

        recorder.setLevelListener(level -> binding.levelMeter.setProgress((int) (level * 100)));

        engine.state().observe(getViewLifecycleOwner(), this::onEngineState);
        showLesson();
    }

    @Override
    public void onResume() {
        super.onResume();
        String requested = new ViewModelProvider(requireActivity())
                .get(PracticeFocusViewModel.class)
                .consume();
        if (requested != null) {
            pendingFocus = requested;
            tryApplyPendingFocus();
        }
    }

    /**
     * Applies a queued focus once the lexicon exists. Called again from the engine state
     * observer, because a request can arrive while the model is still loading and there is no
     * way to match prompts to a sound without the dictionary.
     */
    private void tryApplyPendingFocus() {
        if (pendingFocus == null || binding == null) return;
        if (engine.lexicon() == null) return;

        String phoneme = pendingFocus;
        pendingFocus = null;
        applyFocus(phoneme);
    }

    /** Narrows the prompt list to those that actually contain the chosen sound. */
    private void applyFocus(String phoneme) {
        List<Lesson> matching = Lessons.containingPhoneme(phoneme, engine.lexicon());

        if (matching.isEmpty()) {
            // Better to say so than to silently show an unrelated list.
            showMessage(getString(R.string.focus_none, Phonemes.describe(phoneme)));
            clearFocus();
            return;
        }

        focusPhoneme = phoneme;
        lessons = matching;
        index = 0;

        binding.focusCard.setVisibility(View.VISIBLE);
        binding.focusText.setText(getString(R.string.focus_practising, Phonemes.describe(phoneme)));
        binding.unitChips.setVisibility(View.GONE);
        showLesson();
    }

    private void clearFocus() {
        focusPhoneme = null;
        binding.focusCard.setVisibility(View.GONE);
        binding.unitChips.setVisibility(View.VISIBLE);

        int checked = binding.unitChips.getCheckedChipId();
        Lesson.Unit unit = checked == R.id.chip_sentence ? Lesson.Unit.SENTENCE
                : checked == R.id.chip_paragraph ? Lesson.Unit.PARAGRAPH
                : Lesson.Unit.WORD;
        lessons = Lessons.byUnit(unit);
        index = 0;
        showLesson();
    }

    private Lesson current() {
        return lessons.get(Math.min(index, lessons.size() - 1));
    }

    private void showLesson() {
        Lesson lesson = current();
        binding.promptText.setText(lesson.text);
        binding.promptFocus.setText(lesson.focus);
        binding.promptCounter.setText((index + 1) + " / " + lessons.size());

        // Only single-word prompts get a full IPA line; a whole sentence would be unreadable.
        String ipa = lesson.text.trim().contains(" ") ? null : engine.expectedIpa(lesson.text);
        binding.promptIpa.setVisibility(ipa == null ? View.GONE : View.VISIBLE);
        if (ipa != null) binding.promptIpa.setText("/" + ipa + "/");

        clearResult();
    }

    private void clearResult() {
        binding.resultGroup.setVisibility(View.GONE);
        scoreAdapter.clear();
    }

    private void onEngineState(SpeechEngine.State state) {
        boolean ready = state == SpeechEngine.State.READY;
        binding.recordButton.setEnabled(ready && !busy);

        switch (state) {
            case LOADING:
                binding.engineBanner.setVisibility(View.VISIBLE);
                binding.engineBannerText.setText(R.string.engine_loading);
                break;
            case MODEL_MISSING:
                binding.engineBanner.setVisibility(View.VISIBLE);
                binding.engineBannerText.setText(R.string.engine_missing);
                break;
            case ERROR:
                binding.engineBanner.setVisibility(View.VISIBLE);
                binding.engineBannerText.setText(R.string.engine_error);
                break;
            case READY:
                binding.engineBanner.setVisibility(View.GONE);
                tryApplyPendingFocus();
                showLesson();  // the IPA line only resolves once the lexicon is loaded
                break;
            default:
                binding.engineBanner.setVisibility(View.GONE);
        }
    }

    private void toggleRecording() {
        if (busy) return;

        if (recorder.isRecording()) {
            stopAndScore();
            return;
        }
        if (!ensureMicPermission()) return;

        clearResult();
        if (!recorder.start()) {
            showMessage("Could not open the microphone");
            return;
        }
        binding.recordButton.setText(R.string.stop_recording);
        binding.recordButton.setIconResource(R.drawable.ic_stop);
        binding.levelMeter.setVisibility(View.VISIBLE);
    }

    private void stopAndScore() {
        float[] samples = recorder.stop();
        resetRecordButton();

        busy = true;
        binding.recordButton.setEnabled(false);
        binding.analysingBar.setVisibility(View.VISIBLE);

        engine.score(current().text, samples, new SpeechEngine.ScoreCallback() {
            @Override
            public void onScored(UtteranceScore score) {
                if (binding == null) return;
                busy = false;
                binding.analysingBar.setVisibility(View.GONE);
                binding.recordButton.setEnabled(true);
                render(score);
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                busy = false;
                binding.analysingBar.setVisibility(View.GONE);
                binding.recordButton.setEnabled(true);
                showMessage(message);
            }
        });
    }

    private void render(UtteranceScore score) {
        stats.record(score);

        binding.resultGroup.setVisibility(View.VISIBLE);
        binding.overallPercent.setText(score.overallPercent + "%");
        binding.overallPercent.setTextColor(
                ScoreFormatter.colourForPercent(requireContext(), score.overallPercent));
        binding.overallRing.setProgress(score.overallPercent);
        binding.overallRing.setIndicatorColor(
                ScoreFormatter.colourForPercent(requireContext(), score.overallPercent));
        binding.verdict.setText(ScoreFormatter.verdict(score));
        scoreAdapter.submit(score.words);
    }

    private void resetRecordButton() {
        binding.recordButton.setText(R.string.start_recording);
        binding.recordButton.setIconResource(R.drawable.ic_mic);
        binding.levelMeter.setVisibility(View.GONE);
        binding.levelMeter.setProgress(0);
    }

    @Override
    protected void onRecordingCancelled() {
        if (binding != null) resetRecordButton();
    }

    @Override
    protected void onMicPermissionGranted() {
        toggleRecording();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recorder.setLevelListener(null);
        binding = null;
    }
}
