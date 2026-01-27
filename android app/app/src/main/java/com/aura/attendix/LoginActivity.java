package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
        private static final String API_URL = "http://10.77.107.238:8000/api/auth/login/";

    EditText etUsername, etPassword;
    Button btnLogin;
    ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Link UI Elements
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        // 2. Button Click Listener
        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show Loading
        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        // 3. Create JSON Payload
        JSONObject params = new JSONObject();
        try {
            params.put("username", username);
            params.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 4. Send Request via Volley
        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, API_URL, params,
                response -> {
                    // ✅ SUCCESS
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    handleLoginSuccess(response);
                },
                error -> {
                    // ❌ FAILURE
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);
                    String errorMsg = "Login Failed";
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        errorMsg = "Server not found (Check IP)";
                    } else if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        errorMsg = "Invalid Username/Password";
                    }
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
        );

        queue.add(request);
    }

    private void handleLoginSuccess(JSONObject response) {
        try {
            String role = response.getString("role");
            String name = response.getString("name");

            // 1. Save Session (Keep user logged in)
            SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("username", etUsername.getText().toString());
            editor.putString("role", role);
            editor.putString("name", name);
            editor.apply();

            Toast.makeText(this, "Welcome " + name, Toast.LENGTH_SHORT).show();

            // 2. Navigate based on Role
            Intent intent;
            if (role.equals("STUDENT")) {
                intent = new Intent(LoginActivity.this, StudentHomeActivity.class);
            } else {
                intent = new Intent(LoginActivity.this, TeacherHomeActivity.class);
            }
            startActivity(intent);
            finish(); // Close Login Activity so they can't go back

        } catch (JSONException e) {
            Toast.makeText(this, "Parsing Error", Toast.LENGTH_SHORT).show();
        }
    }
}