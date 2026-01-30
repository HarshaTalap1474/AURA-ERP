package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class TeacherHomeActivity extends AppCompatActivity {

    private TextView tvTeacherName, tvCurrentClass, tvRoom, tvTime;
    private Button btnViewAttendance, btnLogout;
    private ImageView ivTeacherProfile;

    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_home);

        initViews();
        loadTeacherProfile();

        fetchCurrentClass(); // Get live data

        btnLogout.setOnClickListener(v -> logout());
        btnViewAttendance.setOnClickListener(v -> {
            // We will build this Activity next
            Toast.makeText(this, "Opening Live Attendance...", Toast.LENGTH_SHORT).show();
            // Intent intent = new Intent(this, LiveAttendanceActivity.class);
            // startActivity(intent);
        });

        ivTeacherProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        });
    }

    private void initViews() {
        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvCurrentClass = findViewById(R.id.tvCurrentClass);
        tvRoom = findViewById(R.id.tvRoom);
        tvTime = findViewById(R.id.tvTime);
        btnViewAttendance = findViewById(R.id.btnViewAttendance);
        btnLogout = findViewById(R.id.btnLogout);
        requestQueue = Volley.newRequestQueue(this);
        ivTeacherProfile = findViewById(R.id.ivTeacherProfile);
    }

    private void loadTeacherProfile() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        tvTeacherName.setText("Prof. " + prefs.getString("name", "Teacher"));
    }

    private void fetchCurrentClass() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, NetworkConfig.URL_LOGIN, null,
                response -> {
                    try {
                        String subject = response.getString("subject");
                        String room = response.getString("room");
                        String time = response.getString("time_slot");
                        updateClassUI(subject, room, time);
                    } catch (JSONException e) {
                        tvCurrentClass.setText("No Active Class");
                    }
                },
                error -> tvCurrentClass.setText("No Active Class")
        );
        requestQueue.add(request);
    }

    private void updateClassUI(String subject, String room, String time) {
        tvCurrentClass.setText(subject);
        tvRoom.setText(room);
        tvTime.setText(time);
        btnViewAttendance.setEnabled(true);
    }

    private void logout() {
        getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}