package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    // UI Components
    private EditText etRollNo, etFirstName, etLastName, etEmail, etPhone;
    private Button btnSaveProfile, btnLogout, btnChangePass;

    // State Variables to track changes
    private String originalFirstName = "";
    private String originalLastName = "";
    private String originalEmail = "";
    private String originalPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        loadCurrentData();

        // 1. SETUP SAVE BUTTON LOGIC
        // Initially disable save button
        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setAlpha(0.5f); // Make it look disabled (dimmed)

        // Add listeners to watch for typing
        setupTextWatchers();

        btnSaveProfile.setOnClickListener(v -> saveChanges());

        // 2. SETUP LOGOUT BUTTON LOGIC
        // (Make sure you add a button with id 'btnLogout' to your XML later)
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> performLogout());
        }

        // 3. SETUP CHANGE PASSWORD BUTTON LOGIC
        btnChangePass.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class))
        );
    }

    private void initViews() {
        etRollNo = findViewById(R.id.etRollNo);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);
        btnChangePass = findViewById(R.id.btnChangePass);
    }

    private void loadCurrentData() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);

        // Identity (Read Only)
        etRollNo.setText(prefs.getString("username", "---"));

        // Name Parsing
        String fullName = prefs.getString("name", "");
        String[] parts = fullName.split(" ");
        if (parts.length > 0) originalFirstName = parts[0];

        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                sb.append(parts[i]).append(" ");
            }
            originalLastName = sb.toString().trim();
        } else {
            originalLastName = "";
        }

        // Contact Info
        originalEmail = prefs.getString("email", "");
        originalPhone = prefs.getString("phone", "");

        // Set values to UI
        etFirstName.setText(originalFirstName);
        etLastName.setText(originalLastName);
        etEmail.setText(originalEmail);
        etPhone.setText(originalPhone);
    }

    private void setupTextWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkIfModified();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etFirstName.addTextChangedListener(watcher);
        etLastName.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
    }

    private void checkIfModified() {
        String currentFirst = etFirstName.getText().toString().trim();
        String currentLast = etLastName.getText().toString().trim();
        String currentEmail = etEmail.getText().toString().trim();
        String currentPhone = etPhone.getText().toString().trim();

        // Logic: Is ANY field different from the original?
        boolean isChanged = !currentFirst.equals(originalFirstName) ||
                !currentLast.equals(originalLastName) ||
                !currentEmail.equals(originalEmail) ||
                !currentPhone.equals(originalPhone);

        // Logic: Are required fields filled?
        boolean isValid = !currentFirst.isEmpty() && !currentLast.isEmpty();

        // Enable button only if changed AND valid
        boolean shouldEnable = isChanged && isValid;

        btnSaveProfile.setEnabled(shouldEnable);
        btnSaveProfile.setAlpha(shouldEnable ? 1.0f : 0.5f);
    }

    private void saveChanges() {
        String fName = etFirstName.getText().toString().trim();
        String lName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        // Disable button immediately to prevent double-clicks
        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setAlpha(0.5f);

        JSONObject payload = new JSONObject();
        try {
            payload.put("first_name", fName);
            payload.put("last_name", lName);
            payload.put("email", email);

            // ✅ CORRECT KEY FOR BACKEND
            payload.put("phone_number", phone);

        } catch (JSONException e) {
            return;
        }

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                NetworkConfig.URL_PROFILE_UPDATE,
                payload,
                response -> {
                    // Update Local Storage on Success
                    SharedPreferences.Editor editor = getSharedPreferences("UserSession", MODE_PRIVATE).edit();
                    editor.putString("name", fName + " " + lName);
                    editor.putString("email", email);
                    editor.putString("phone", phone);
                    editor.apply();

                    // Update originals so button disables again
                    originalFirstName = fName;
                    originalLastName = lName;
                    originalEmail = email;
                    originalPhone = phone;
                    checkIfModified();

                    Toast.makeText(this, "Profile Updated Successfully", Toast.LENGTH_SHORT).show();
                    finish(); // Go back
                },
                error -> {
                    // Re-enable button on error
                    checkIfModified();
                    String errorMsg = "Update Failed";
                    if (error.networkResponse != null) {
                        errorMsg += " (Code " + error.networkResponse.statusCode + ")";
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
                // Send Token
                headers.put("Authorization", "Bearer " + prefs.getString("access_token", ""));
                return headers;
            }
        };

        queue.add(request);
    }

    private void performLogout() {
        // 1. Clear Local Session
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // 2. Stop the Attendance Service if running
        Intent serviceIntent = new Intent(this, BleBroadcastService.class);
        stopService(serviceIntent);

        // 3. Navigate back to Login
        Intent intent = new Intent(this, LoginActivity.class);
        // Clear back stack so user can't press "Back" to return to profile
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}