package com.example.pronunciation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.pronunciation.data.Language;
import com.example.pronunciation.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private int currentDestination = 0;

    /**
     * Chinese has no alphabet, so the reference tab is called Pinyin there. Re-applied on every
     * resume because the language is changed from the Main tab, not from here.
     */
    @Override
    protected void onResume() {
        super.onResume();
        boolean chinese = PronunciationApp.from(this).language() == Language.CHINESE;
        int label = chinese ? R.string.section_syllables : R.string.section_alphabet;

        MenuItem item = binding.bottomNav.getMenu().findItem(R.id.alphabetFragment);
        if (item != null) item.setTitle(label);

        if (currentDestination == R.id.alphabetFragment) binding.toolbar.setTitle(label);
    }

    private CharSequence titleFor(int destinationId, CharSequence fallback) {
        if (destinationId == R.id.alphabetFragment
                && PronunciationApp.from(this).language() == Language.CHINESE) {
            return getString(R.string.section_syllables);
        }
        return fallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        NavHostFragment host =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host != null) {
            NavController navController = host.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
            navController.addOnDestinationChangedListener((c, destination, args) -> {
                currentDestination = destination.getId();
                binding.toolbar.setTitle(titleFor(destination.getId(), destination.getLabel()));
            });
        }

        // Registration must happen unconditionally so it survives state restoration; only the
        // launch is conditional. The recording screens re-request if the user declines here.
        ActivityResultLauncher<String> micPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }
}
