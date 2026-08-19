package com.example.pronunciation.ui.game;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pronunciation.R;
import com.example.pronunciation.data.GameProblem;
import com.example.pronunciation.data.GameProblems;
import com.example.pronunciation.data.GameScoring;
import com.example.pronunciation.databinding.FragmentGameBinding;
import com.example.pronunciation.speech.SpeechEngine;
import com.example.pronunciation.speech.UtteranceScore;
import com.example.pronunciation.speech.WordScore;
import com.example.pronunciation.ui.RecordingFragment;
import com.example.pronunciation.ui.ScoreFormatter;

import java.util.Random;

/**
 * Section 3 — read a multi-paragraph passage aloud, one sentence at a time.
 *
 * <p>The fish above the passage only moves when a sentence is pronounced well enough. There is
 * no way to skip a sentence: the point is that progress is earned, not that the passage is
 * finished.
 *
 * <p>Ten rounds, one passage each, level 1 through 10. Scoring per sentence rather than per
 * passage is a hard requirement, not a preference — a 90-word recording takes tens of seconds
 * to run through the model, where a single sentence takes under a second.
 */
public class GameFragment extends RecordingFragment {

    private static final int ROUNDS = GameProblems.LEVELS;
    private static final String PREFS = "game";
    private static final String KEY_BEST = "best_score";

    private FragmentGameBinding binding;
    private GameProblems problems;
    private final Random random = new Random();

    private GameProblem problem;
    private int roundIndex = 0;       // 0-based; level is roundIndex + 1
    private int sentenceIndex = 0;
    private int attempts = 0;         // attempts at the current sentence
    private int totalScore = 0;
    private boolean playing = false;
    private boolean busy = false;
    private boolean abandoned = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        problems = GameProblems.get(requireContext());

        int fishColor = ContextCompat.getColor(requireContext(), R.color.fish);
        int waterColor = ContextCompat.getColor(requireContext(), R.color.water);
        binding.passageText.setFishColor(fishColor);
        binding.idleFish.setColors(fishColor, waterColor);
        binding.idleFish.resetTo(0.12f);

        binding.startButton.setOnClickListener(v -> startGame());
        binding.playAgainButton.setOnClickListener(v -> startGame());
        binding.recordButton.setOnClickListener(v -> toggleRecording());
        binding.listenButton.setOnClickListener(v -> {
            if (problem != null && sentenceIndex < problem.sentenceCount()) {
                tts().speak(problem.sentences.get(sentenceIndex));
            }
        });
        binding.quitButton.setOnClickListener(v -> endGame());

        recorder.setLevelListener(level -> binding.levelMeter.setProgress((int) (level * 100)));
        engine.state().observe(getViewLifecycleOwner(), this::onEngineState);

        binding.bestScore.setText(getString(R.string.best_score, bestScore()));
        showIdle();
    }

    private void onEngineState(SpeechEngine.State state) {
        boolean ready = state == SpeechEngine.State.READY;
        binding.startButton.setEnabled(ready);
        binding.recordButton.setEnabled(ready && !busy);

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

    // --- game flow -------------------------------------------------------------------------

    private void startGame() {
        roundIndex = 0;
        totalScore = 0;
        playing = true;
        showPlaying();
        startRound();
    }

    private void startRound() {
        problem = problems.randomForLevel(roundIndex + 1, random);
        if (problem == null) {
            showMessage(getString(R.string.game_no_problems));
            endGame();
            return;
        }

        sentenceIndex = 0;
        attempts = 0;

        binding.roundFeedback.setVisibility(View.GONE);
        renderPassage();
        binding.passageText.jumpTo(currentSentenceStart());
        binding.passageScroll.scrollTo(0, 0);
    }

    private void renderPassage() {
        binding.roundLabel.setText(getString(R.string.round_of, roundIndex + 1, ROUNDS));
        binding.problemLabel.setText(getString(R.string.game_sentence_of,
                problem.title, sentenceIndex + 1, problem.sentenceCount()));
        binding.runningScore.setText(String.valueOf(totalScore));
        binding.passageText.setText(buildPassage());
    }

    /** Character offset the fish should sit above: the start of the sentence to read next. */
    private int currentSentenceStart() {
        if (problem == null || sentenceIndex >= problem.spans.size()) return -1;
        return problem.spans.get(sentenceIndex)[0];
    }

    /** Done sentences in green, the one to read now highlighted, the rest muted. */
    private CharSequence buildPassage() {
        SpannableStringBuilder sb = new SpannableStringBuilder(problem.displayText);

        int done = ContextCompat.getColor(requireContext(), R.color.score_good);
        int upcoming = ContextCompat.getColor(requireContext(), R.color.score_missing);
        int current = ContextCompat.getColor(requireContext(), R.color.current_sentence);

        for (int i = 0; i < problem.spans.size(); i++) {
            int[] span = problem.spans.get(i);
            if (i < sentenceIndex) {
                sb.setSpan(new ForegroundColorSpan(done), span[0], span[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (i == sentenceIndex) {
                sb.setSpan(new BackgroundColorSpan(current), span[0], span[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), span[0], span[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.setSpan(new ForegroundColorSpan(upcoming), span[0], span[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private void toggleRecording() {
        if (busy || !playing || problem == null) return;

        if (recorder.isRecording()) {
            stopAndScore();
            return;
        }
        if (!ensureMicPermission()) return;

        if (!recorder.start()) {
            showMessage("Could not open the microphone");
            return;
        }
        binding.recordButton.setText(R.string.stop_recording);
        binding.recordButton.setIconResource(R.drawable.ic_stop);
        binding.levelMeter.setVisibility(View.VISIBLE);
        binding.roundFeedback.setVisibility(View.GONE);
    }

    private void stopAndScore() {
        float[] samples = recorder.stop();
        resetRecordButton();

        busy = true;
        binding.recordButton.setEnabled(false);
        binding.analysingBar.setVisibility(View.VISIBLE);

        final String target = problem.sentences.get(sentenceIndex);
        engine.score(target, samples, new SpeechEngine.ScoreCallback() {
            @Override
            public void onScored(UtteranceScore score) {
                if (binding == null) return;
                busy = false;
                stats.record(score);
                binding.analysingBar.setVisibility(View.GONE);
                binding.recordButton.setEnabled(true);
                judge(score);
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

    /** Advance only on a good enough attempt. Anything else leaves the fish where it is. */
    private void judge(UtteranceScore score) {
        attempts++;

        if (!GameScoring.passes(score.overallPercent)) {
            binding.roundFeedback.setVisibility(View.VISIBLE);
            binding.roundFeedback.setText(retryMessage(score));
            binding.roundFeedback.setTextColor(
                    ScoreFormatter.colourForPercent(requireContext(), score.overallPercent));
            return;
        }

        int earned = GameScoring.pointsFor(score.overallPercent, attempts);
        totalScore += earned;

        sentenceIndex++;
        attempts = 0;

        binding.roundFeedback.setVisibility(View.VISIBLE);
        binding.roundFeedback.setText(getString(R.string.game_passed, score.overallPercent, earned));
        binding.roundFeedback.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.score_good));

        if (sentenceIndex >= problem.sentenceCount()) {
            binding.getRoot().postDelayed(this::finishRound, 1100);
        } else {
            // Re-highlight first, then swim — the new text must be laid out before the fish can
            // resolve where the next sentence starts.
            renderPassage();
            binding.passageText.post(() -> {
                if (binding == null) return;
                int offset = currentSentenceStart();
                binding.passageText.swimTo(offset);
                // Follow the fish, or it swims off the bottom of a long passage.
                binding.passageScroll.smoothScrollTo(0, binding.passageText.scrollTargetFor(offset));
            });
        }
    }

    private String retryMessage(UtteranceScore score) {
        if (score.isEmpty()) {
            return getString(R.string.game_heard_nothing);
        }
        WordScore weakest = score.weakestWord();
        if (weakest != null) {
            String hint = ScoreFormatter.substitutionHint(weakest);
            if (hint != null) {
                return getString(R.string.game_retry_word, weakest.word, hint);
            }
            return getString(R.string.game_retry_simple, weakest.word, score.overallPercent);
        }
        return getString(R.string.game_retry_generic, score.overallPercent);
    }

    private void finishRound() {
        if (binding == null || !playing) return;

        roundIndex++;
        if (roundIndex >= ROUNDS) {
            endGame();
        } else {
            startRound();
        }
    }

    private void endGame() {
        if (binding == null) return;
        playing = false;
        if (recorder.isRecording()) recorder.cancel();
        resetRecordButton();

        binding.finalScore.setText(String.valueOf(totalScore));
        binding.finalVerdict.setText(verdictFor(roundIndex));
        binding.finalStars.setText(stars(roundIndex));

        int best = bestScore();
        if (totalScore > best) {
            prefs().edit().putInt(KEY_BEST, totalScore).apply();
            binding.newBest.setVisibility(View.VISIBLE);
            best = totalScore;
        } else {
            binding.newBest.setVisibility(View.GONE);
        }
        binding.bestScore.setText(getString(R.string.best_score, best));

        showFinished();
    }

    /** Stars reflect how far through the ten rounds the reader got. */
    private static String stars(int roundsCompleted) {
        int earned = roundsCompleted >= ROUNDS ? 3 : roundsCompleted >= 6 ? 2 : roundsCompleted >= 3 ? 1 : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) sb.append(i < earned ? "★" : "☆");
        return sb.toString();
    }

    private String verdictFor(int roundsCompleted) {
        if (roundsCompleted >= ROUNDS) return getString(R.string.game_verdict_all);
        return getString(R.string.game_verdict_partial, roundsCompleted, ROUNDS);
    }

    // --- view states -----------------------------------------------------------------------

    private void showIdle() {
        binding.idleGroup.setVisibility(View.VISIBLE);
        binding.playingGroup.setVisibility(View.GONE);
        binding.finishedGroup.setVisibility(View.GONE);
    }

    private void showPlaying() {
        binding.idleGroup.setVisibility(View.GONE);
        binding.playingGroup.setVisibility(View.VISIBLE);
        binding.finishedGroup.setVisibility(View.GONE);
    }

    private void showFinished() {
        binding.idleGroup.setVisibility(View.GONE);
        binding.playingGroup.setVisibility(View.GONE);
        binding.finishedGroup.setVisibility(View.VISIBLE);
    }

    private void resetRecordButton() {
        if (binding == null) return;
        binding.recordButton.setText(R.string.start_recording);
        binding.recordButton.setIconResource(R.drawable.ic_mic);
        binding.levelMeter.setVisibility(View.GONE);
        binding.levelMeter.setProgress(0);
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, 0);
    }

    private int bestScore() {
        return prefs().getInt(KEY_BEST, 0);
    }

    @Override
    protected void onRecordingCancelled() {
        resetRecordButton();
    }

    @Override
    protected void onMicPermissionGranted() {
        toggleRecording();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (abandoned && binding != null) {
            abandoned = false;
            showIdle();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (playing) {
            playing = false;
            abandoned = true;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recorder.setLevelListener(null);
        binding = null;
    }
}
