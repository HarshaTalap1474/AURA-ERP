package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class StudentHomeActivity extends AppCompatActivity {

    TextView tvWelcomeName, tvUsername;
    Button btnScanAttendance, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_home);

        // 1. Link UI Elements
        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        tvUsername = findViewById(R.id.tvUsername);
        btnScanAttendance = findViewById(R.id.btnScanAttendance);
        btnLogout = findViewById(R.id.btnLogout);

        // 2. Load User Data from Session
        loadUserData();

        // 3. Setup Buttons
        btnScanAttendance.setOnClickListener(v -> {
            // We will build this Bluetooth Scanner next!
            Toast.makeText(this, "Opening Bluetooth Scanner...", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> logout());
    }

    private void loadUserData() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String name = prefs.getString("name", "Student");
        String username = prefs.getString("username", "---");

        tvWelcomeName.setText("Welcome, " + name);
        tvUsername.setText("ID: " + username);
    }

    private void logout() {
        // Clear Session
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        // Go back to Login
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}