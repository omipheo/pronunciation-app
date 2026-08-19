package com.example.pronunciation.ui.game;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pronunciation.R;
import com.example.pronunciation.data.Lesson;
import com.example.pronunciation.data.Lessons;
import com.example.pronunciation.databinding.FragmentGameBinding;
import com.example.pronunciation.speech.SpeechEngine;
import com.example.pronunciation.speech.UtteranceScore;
import com.example.pronunciation.speech.WordScore;
import com.example.pronunciation.ui.RecordingFragment;
import com.example.pronunciation.ui.ScoreFormatter;

import java.util.List;

/**
 * Section 3 — a timed run of five sentences.
 *
 * <p>Same engine as Section 2, different pressure: a countdown per round and a running score,
 * so the learner has to produce the sounds without rehearsing them first.
 */
public class GameFragment extends RecordingFragment {

    private static final int ROUNDS = 5;
    private static final int SECONDS_PER_ROUND = 25;
    private static final int PASS_THRESHOLD = 70;
    private static final String PREFS = "game";
    private static final String KEY_BEST = "best_score";

    private FragmentGameBinding binding;

    private List<Lesson> round;
    private int roundIndex = 0;
    private int totalScore = 0;
    private int streak = 0;
    private boolean playing = false;
    private boolean busy = false;
    /** Set when a run was abandoned by backgrounding; the screen resets on return. */
    private boolean abandoned = false;
    private CountDownTimer timer;

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

        binding.startButton.setOnClickListener(v -> startGame());
        binding.playAgainButton.setOnClickListener(v -> startGame());
        binding.recordButton.setOnClickListener(v -> toggleRecording());
        binding.skipButton.setOnClickListener(v -> {
            if (playing && !busy) finishRound(0, "Skipped");
        });

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
        round = Lessons.gameRound(ROUNDS, System.nanoTime());
        roundIndex = 0;
        totalScore = 0;
        streak = 0;
        playing = true;
        showPlaying();
        startRound();
    }

    private void startRound() {
        Lesson lesson = round.get(roundIndex);
        binding.roundLabel.setText(getString(R.string.round_of, roundIndex + 1, round.size()));
        binding.sentenceText.setText(lesson.text);
        binding.runningScore.setText(String.valueOf(totalScore));
        binding.streakLabel.setVisibility(streak >= 2 ? View.VISIBLE : View.GONE);
        binding.streakLabel.setText(getString(R.string.streak, streak));
        binding.roundFeedback.setVisibility(View.GONE);
        binding.recordButton.setEnabled(engine.isReady());

        startTimer();
    }

    private void startTimer() {
        cancelTimer();
        binding.timerBar.setMax(SECONDS_PER_ROUND * 10);
        timer = new CountDownTimer(SECONDS_PER_ROUND * 1000L, 100) {
            @Override
            public void onTick(long remaining) {
                if (binding == null) return;
                binding.timerBar.setProgress((int) (remaining / 100));
                binding.timerText.setText(String.valueOf((remaining / 1000) + 1));
            }

            @Override
            public void onFinish() {
                if (binding == null) return;
                if (recorder.isRecording()) {
                    stopAndScore();  // ran out mid-attempt; score what we got
                } else {
                    finishRound(0, "Out of time");
                }
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void toggleRecording() {
        if (busy || !playing) return;

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
    }

    private void stopAndScore() {
        cancelTimer();
        float[] samples = recorder.stop();
        resetRecordButton();

        busy = true;
        binding.recordButton.setEnabled(false);
        binding.analysingBar.setVisibility(View.VISIBLE);

        engine.score(round.get(roundIndex).text, samples, new SpeechEngine.ScoreCallback() {
            @Override
            public void onScored(UtteranceScore score) {
                if (binding == null) return;
                busy = false;
                stats.record(score);
                binding.analysingBar.setVisibility(View.GONE);
                finishRound(score.overallPercent, feedbackFor(score));
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                busy = false;
                binding.analysingBar.setVisibility(View.GONE);
                binding.recordButton.setEnabled(true);
                showMessage(message);
                startTimer();  // give the round back rather than eating it
            }
        });
    }

    private String feedbackFor(UtteranceScore score) {
        if (score.overallPercent >= PASS_THRESHOLD) {
            return "Clear — " + score.overallPercent + "%";
        }
        WordScore weakest = score.weakestWord();
        return weakest == null
                ? "Missed it — " + score.overallPercent + "%"
                : "\"" + weakest.word + "\" tripped you up — " + score.overallPercent + "%";
    }

    private void finishRound(int percent, String feedback) {
        cancelTimer();
        if (recorder.isRecording()) recorder.cancel();
        resetRecordButton();

        totalScore += percent;
        streak = percent >= PASS_THRESHOLD ? streak + 1 : 0;

        binding.roundFeedback.setVisibility(View.VISIBLE);
        binding.roundFeedback.setText(feedback);
        binding.roundFeedback.setTextColor(
                ScoreFormatter.colourForPercent(requireContext(), percent));
        binding.runningScore.setText(String.valueOf(totalScore));

        roundIndex++;
        if (roundIndex >= round.size()) {
            binding.getRoot().postDelayed(this::endGame, 1200);
        } else {
            binding.getRoot().postDelayed(() -> {
                if (binding != null && playing) startRound();
            }, 1200);
        }
    }

    private void endGame() {
        if (binding == null) return;
        playing = false;
        cancelTimer();

        int max = round.size() * 100;
        int percent = max == 0 ? 0 : totalScore * 100 / max;

        binding.finalScore.setText(totalScore + " / " + max);
        binding.finalVerdict.setText(verdictFor(percent));
        binding.finalStars.setText(stars(percent));

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

    private static String stars(int percent) {
        int earned = percent >= 90 ? 3 : percent >= 70 ? 2 : percent >= 45 ? 1 : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) sb.append(i < earned ? "★" : "☆");
        return sb.toString();
    }

    private static String verdictFor(int percent) {
        if (percent >= 90) return "Outstanding.";
        if (percent >= 70) return "Solid run.";
        if (percent >= 45) return "Keep at it.";
        return "Try the training section first.";
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
        // A run cannot be resumed fairly after being backgrounded, so start over cleanly
        // rather than leaving a stopped clock on screen.
        if (abandoned && binding != null) {
            abandoned = false;
            showIdle();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        cancelTimer();
        if (playing) {
            playing = false;
            abandoned = true;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
        recorder.setLevelListener(null);
        binding = null;
    }
}
