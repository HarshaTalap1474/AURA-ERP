package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SettingsActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup themeToggleGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Back button
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Version
        TextView tvVersion = findViewById(R.id.tvVersion);
        try {
            String vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText("Version " + vn);
        } catch (Exception ignored) {}

        // Theme toggle
        themeToggleGroup = findViewById(R.id.themeToggleGroup);
        int currentMode = ThemeManager.getMode(this);
        switch (currentMode) {
            case ThemeManager.MODE_LIGHT:
                themeToggleGroup.check(R.id.btnThemeLight); break;
            case ThemeManager.MODE_DARK:
                themeToggleGroup.check(R.id.btnThemeDark); break;
            default:
                themeToggleGroup.check(R.id.btnThemeSystem);
        }

        themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnThemeLight) {
                ThemeManager.setMode(this, ThemeManager.MODE_LIGHT);
            } else if (checkedId == R.id.btnThemeDark) {
                ThemeManager.setMode(this, ThemeManager.MODE_DARK);
            } else {
                ThemeManager.setMode(this, ThemeManager.MODE_SYSTEM);
            }
            // Recreate to apply immediately
            recreate();
        });

        // Account rows
        findViewById(R.id.rowChangePassword).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.rowProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        // Logout
        ((MaterialButton) findViewById(R.id.btnLogout)).setOnClickListener(v -> {
            getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply();
            stopService(new Intent(this, BleBroadcastService.class));
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}
