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

    private static final String API_URL =
            "http://10.77.107.238:8000/api/auth/login/";

    private EditText etUsername, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
            Toast.makeText(this,
                    "Please enter username and password",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        JSONObject payload = new JSONObject();
        try {
            payload.put("username", username);
            payload.put("password", password);
        } catch (JSONException e) {
            showLoading(false);
            Toast.makeText(this, "Invalid request data", Toast.LENGTH_SHORT).show();
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

            saveSession(role, name);

            Toast.makeText(this,
                    "Welcome " + name,
                    Toast.LENGTH_SHORT).show();

            navigateByRole(role);

        } catch (JSONException e) {
            Toast.makeText(this,
                    "Response parsing error",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLoginError(com.android.volley.VolleyError error) {
        showLoading(false);

        String message = "Login failed";

        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;

            if (statusCode == 401) {
                message = "Invalid username or password";
            } else if (statusCode == 404) {
                message = "Server not found";
            } else if (statusCode >= 500) {
                message = "Server error. Try again later";
            }
        } else {
            message = "Network error. Check your connection";
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void saveSession(String role, String name) {
        SharedPreferences prefs =
                getSharedPreferences("UserSession", MODE_PRIVATE);

        prefs.edit()
                .putString("username", etUsername.getText().toString().trim())
                .putString("role", role)
                .putString("name", name)
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
