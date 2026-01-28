package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    // Ensure this IP matches your local Django server
    private static final String API_URL = "http://10.77.107.238:8000/api/auth/login/";
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // [2] Auto-Login Check: If user is already bound/logged in, skip this screen
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        if (prefs.contains("username")) {
            navigateByRole(prefs.getString("role", "STUDENT"));
            return;
        }

        setContentView(R.layout.activity_login);

        initViews();
        requestQueue = Volley.newRequestQueue(this);

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
    }

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // [3] SECURITY: Fetch the unique Android Hardware ID
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        showLoading(true);

        JSONObject payload = new JSONObject();
        try {
            payload.put("username", username);
            payload.put("password", password);
            // [4] Send the Hardware ID to server
            payload.put("device_id", deviceId);
        } catch (JSONException e) {
            showLoading(false);
            Toast.makeText(this, "Error creating request", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                API_URL,
                payload,
                this::handleLoginSuccess,
                error -> handleLoginError(error)
        );

        requestQueue.add(request);
    }

    private void handleLoginSuccess(JSONObject response) {
        showLoading(false);

        try {
            String role = response.getString("role");
            String name = response.getString("name");

            // Server might return the fingerprint it saved
            String serverFingerprint = response.optString("device_fingerprint", "");

            // [5] Save session including the Hardware ID
            saveSession(role, name, serverFingerprint);

            Toast.makeText(this, "Device Verified. Welcome " + name, Toast.LENGTH_SHORT).show();

            navigateByRole(role);

        } catch (JSONException e) {
            Toast.makeText(this, "Response parsing error", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLoginError(com.android.volley.VolleyError error) {
        showLoading(false);

        String message = "Login failed";

        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;

            // [6] Specific Error Messages for Security Blocking
            if (statusCode == 401) {
                message = "Security Alert: This account is linked to another device.";
            } else if (statusCode == 403) {
                message = "Account Disabled. Contact Admin.";
            } else if (statusCode == 404) {
                message = "Server not found";
            } else {
                message = "Server error (" + statusCode + ")";
            }
        } else {
            message = "Network error. Check connection.";
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void saveSession(String role, String name, String deviceFingerprint) {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);

        prefs.edit()
                .putString("username", etUsername.getText().toString().trim())
                .putString("role", role)
                .putString("name", name)
                .putString("device_fingerprint", deviceFingerprint) // Saved for local checks
                .apply();
    }

    private void navigateByRole(String role) {
        Intent intent;

        if ("STUDENT".equalsIgnoreCase(role)) {
            intent = new Intent(this, StudentHomeActivity.class);
        } else {
            intent = new Intent(this, TeacherHomeActivity.class);
        }

        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        etUsername.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }
}