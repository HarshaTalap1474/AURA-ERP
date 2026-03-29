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
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import androidx.core.content.FileProvider;
import java.io.File;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class LoginActivity extends AppCompatActivity {
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
        requestQueue = VolleySingleton.getInstance(this).getRequestQueue();
        
        // [2b] OTA Update Sync: Query Django for active releases
        checkForUpdates();

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
                NetworkConfig.URL_LOGIN,
                payload,
                this::handleLoginSuccess,
                error -> handleLoginError(error)
        );

        request.setRetryPolicy(new DefaultRetryPolicy(6000, 0, 1.0f));
        requestQueue.add(request);
    }

    private void handleLoginSuccess(JSONObject response) {
        try {
            String role = response.getString("role");
            String name = response.getString("name");
            String token = response.getString("access_token");
            String email = response.optString("email", "");
            String phone = response.optString("phone", "");
            if (phone.isEmpty()) {
                phone = response.optString("phone_number", "");
            }

            SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
            prefs.edit()
                    .putString("username", etUsername.getText().toString().trim())
                    .putString("role", role)
                    .putString("name", name)
                    .putString("access_token", token)
                    .putString("email", email)
                    .putString("phone", phone)
                    .apply();

            // Hide loading BEFORE navigating (progressBar is guaranteed non-null here
            // because handleLoginSuccess is only reachable after a real network call
            // which means setContentView + initViews() have already run)
            showLoading(false);
            navigateByRole(role);

        } catch (JSONException e) {
            showLoading(false);
            Toast.makeText(this, "Login Parsing Error", Toast.LENGTH_SHORT).show();
        }
    }

    // Update signature to accept token
    private void saveSession(String role, String name, String deviceId, String token) {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        prefs.edit()
                .putString("username", etUsername.getText().toString().trim())
                .putString("role", role)
                .putString("name", name)
                .putString("device_fingerprint", deviceId)
                .putString("access_token", token) // 💾 SAVE IT!
                .apply();
    }

    private void handleLoginError(com.android.volley.VolleyError error) {
        showLoading(false);

        String message = "Login failed";

        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;
            String serverMsg = "";

            // Try to parse the actual error message from the server (if available)
            try {
                String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject data = new JSONObject(responseBody);
                serverMsg = data.optString("detail", data.optString("error", ""));
            } catch (Exception e) {
                // formatting error, ignore
            }

            // 1. Wrong Password / Username (Standard HTTP 401)
            if (statusCode == 401) {
                message = "Invalid Username or Password";
            }
            // 2. Device Mismatch or Account Ban (Backend sends 403 for this)
            else if (statusCode == 403) {
                if (serverMsg.toLowerCase().contains("device")) {
                    message = "Security Alert: This account is linked to another device.";
                } else {
                    message = "Access Denied: " + serverMsg;
                }
            }
            // 3. Other Errors
            else if (statusCode == 404) {
                message = "Server endpoint not found";
            } else if (statusCode >= 500) {
                message = "Server Error (Try again later)";
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

        switch (role.toUpperCase()) {

            // ── Student ────────────────────────────────────
            case "STUDENT":
                intent = new Intent(this, StudentHomeActivity.class);
                break;

            // ── Teaching Staff ─────────────────────────────
            case "TEACHER":
                intent = new Intent(this, TeacherHomeActivity.class);
                break;

            case "TEACHER_GUARDIAN":
                // TG shares the Teacher dashboard + extra mentorship tab
                intent = new Intent(this, TeacherHomeActivity.class);
                intent.putExtra("role", "TEACHER_GUARDIAN");
                break;

            case "HOD":
                intent = new Intent(this, HodHomeActivity.class);
                break;

            case "ACADEMIC_COORDINATOR":
                intent = new Intent(this, AcademicCoordinatorHomeActivity.class);
                break;

            // ── Administration ─────────────────────────────
            case "SUPER_ADMIN":
            case "ADMIN":          // legacy fallback also goes to SuperAdmin dashboard
                intent = new Intent(this, SuperAdminHomeActivity.class);
                break;

            // ── Support Roles ──────────────────────────────
            case "SECURITY_OFFICER":
                intent = new Intent(this, SecurityHomeActivity.class);
                break;

            case "FINANCE_CLERK":
                intent = new Intent(this, FinanceHomeActivity.class);
                break;

            case "LIBRARIAN":
                intent = new Intent(this, LibrarianHomeActivity.class);
                break;

            // ── Parent Portal ───────────────────────────────
            case "PARENT":
                intent = new Intent(this, ParentHomeActivity.class);
                break;

            // ── Safety net: unknown role → Teacher dashboard ─
            default:
                intent = new Intent(this, TeacherHomeActivity.class);
                break;
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

    private void checkForUpdates() {
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            NetworkConfig.URL_API_LATEST_APP,
            null,
            response -> {
                try {
                    boolean success = response.optBoolean("success", false);
                    if (success) {
                        int apiVersion = response.getInt("version_code");
                        int currentVersion = BuildConfig.VERSION_CODE;
                        
                        if (apiVersion > currentVersion) {
                            String releaseNotes = response.getString("release_notes");
                            String apkUrl = response.getString("apk_url");
                            showUpdateDialog(releaseNotes, apkUrl);
                        }
                    }
                } catch (JSONException e) {
                    // Ignore background parsing errors
                }
            },
            error -> {
                // Ignore network errors on background update probe
            }
        );
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 0, 1.0f));
        requestQueue.add(request);
    }

    private void showUpdateDialog(String releaseNotes, String apkUrl) {
        new AlertDialog.Builder(this)
            .setTitle("New Update Available")
            .setMessage(releaseNotes)
            .setCancelable(false)
            .setPositiveButton("Update Now", (dialog, which) -> {
                downloadAndInstallApk(apkUrl);
            })
            .setNegativeButton("Later", null)
            .show();
    }

    private void downloadAndInstallApk(String url) {
        Toast.makeText(this, "Downloading AURA-ERP update in background...", Toast.LENGTH_LONG).show();
        
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setTitle("AURA-ERP System Update");
        request.setDescription("Downloading latest APK...");
        
        // Save to public Downloads directory
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AURA_ERP_Latest.apk");
        
        long downloadId = manager.enqueue(request);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctxt, Intent intent) {
                long receivedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (receivedId == downloadId) {
                    try {
                        File apkFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AURA_ERP_Latest.apk");
                        Uri fileUri = FileProvider.getUriForFile(
                            LoginActivity.this,
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            apkFile
                        );
                        
                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.setDataAndType(fileUri, "application/vnd.android.package-archive");
                        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(installIntent);
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, "Installation Failed. Please find the APK in your Downloads folder.", Toast.LENGTH_LONG).show();
                    }
                    try {
                        unregisterReceiver(this);
                    } catch (Exception ignored) {}
                }
            }
        };
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
}