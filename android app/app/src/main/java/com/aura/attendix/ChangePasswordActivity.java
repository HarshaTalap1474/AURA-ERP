package com.aura.attendix;

import android.content.SharedPreferences;
import android.os.Bundle;
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

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText etOldPass, etNewPass, etConfirmPass;
    private Button btnUpdatePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        etOldPass = findViewById(R.id.etOldPass);
        etNewPass = findViewById(R.id.etNewPass);
        etConfirmPass = findViewById(R.id.etConfirmPass);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);

        btnUpdatePassword.setOnClickListener(v -> attemptChange());
    }

    private void attemptChange() {
        String oldP = etOldPass.getText().toString().trim();
        String newP = etNewPass.getText().toString().trim();
        String confirmP = etConfirmPass.getText().toString().trim();

        // 1. Client-Side Validation
        if (oldP.isEmpty() || newP.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newP.equals(confirmP)) {
            Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newP.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 chars", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Prepare Request
        JSONObject payload = new JSONObject();
        try {
            payload.put("old_password", oldP);
            payload.put("new_password", newP);
        } catch (JSONException e) { return; }

        btnUpdatePassword.setEnabled(false); // Prevent spam clicking

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                NetworkConfig.URL_CHANGE_PASSWORD,
                payload,
                response -> {
                    Toast.makeText(this, "Password Updated Successfully!", Toast.LENGTH_LONG).show();
                    finish(); // Close activity on success
                },
                error -> {
                    btnUpdatePassword.setEnabled(true);
                    String msg = "Update Failed";
                    if (error.networkResponse != null && error.networkResponse.statusCode == 400) {
                        msg = "Incorrect Old Password"; // Specific feedback
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
                headers.put("Authorization", "Bearer " + prefs.getString("access_token", ""));
                return headers;
            }
        };

        queue.add(request);
    }
}