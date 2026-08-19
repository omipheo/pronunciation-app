package com.example.pronunciation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.pronunciation.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

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
            navController.addOnDestinationChangedListener((c, destination, args) ->
                    binding.toolbar.setTitle(destination.getLabel()));
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
