package com.example.pronunciation.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.pronunciation.PronunciationApp;
import com.example.pronunciation.audio.AudioRecorder;
import com.example.pronunciation.audio.TtsSpeaker;
import com.example.pronunciation.speech.SpeechEngine;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shared plumbing for the two screens that record the user: mic permission, the recorder itself,
 * and access to the shared speech and TTS engines.
 */
public abstract class RecordingFragment extends Fragment {

    protected final AudioRecorder recorder = new AudioRecorder();
    protected SpeechEngine engine;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = SpeechEngine.get(requireContext());
        engine.init();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        onMicPermissionGranted();
                    } else {
                        showMessage("Microphone access is needed to score your pronunciation");
                    }
                });
    }

    protected TtsSpeaker tts() {
        return PronunciationApp.from(requireContext()).tts();
    }

    protected boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @return true if recording can start right now; otherwise prompts and returns false
     */
    protected boolean ensureMicPermission() {
        if (hasMicPermission()) return true;
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        return false;
    }

    /** Called after the user grants the permission from a prompt this fragment raised. */
    protected void onMicPermissionGranted() {
    }

    protected void showMessage(String message) {
        View root = getView();
        if (root != null) {
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Never leave the mic open behind a backgrounded screen.
        if (recorder.isRecording()) {
            recorder.cancel();
            onRecordingCancelled();
        }
        tts().stop();
    }

    /** Hook so subclasses can reset their button state after an interrupted recording. */
    protected void onRecordingCancelled() {
    }
}
