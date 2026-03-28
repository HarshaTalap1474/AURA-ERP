package com.aura.attendix;

import android.view.View;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.DefaultRetryPolicy;
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
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_home);

        initViews();
        View ivSet = findViewById(R.id.ivSettings);
        if (ivSet != null) ivSet.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        loadTeacherProfile();

        btnLogout.setOnClickListener(v -> logout());

        // Open today's timetable (start/end classes from there)
        btnViewAttendance.setOnClickListener(v ->
                startActivity(new Intent(this, TimetableActivity.class)));

        // Leave approvals — Teacher/HOD/TG can approve pending requests
        android.view.View btnLeaves = findViewById(R.id.cardLeaveApproval);
        if (btnLeaves != null) {
            btnLeaves.setOnClickListener(v ->
                    startActivity(new Intent(this, LeaveApprovalsActivity.class)));
        }

        ivTeacherProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void initViews() {
        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvCurrentClass = findViewById(R.id.tvCurrentClass);
        tvRoom = findViewById(R.id.tvRoom);
        tvTime = findViewById(R.id.tvTime);
        btnViewAttendance = findViewById(R.id.btnViewAttendance);
        btnLogout = findViewById(R.id.btnLogout);
        requestQueue = VolleySingleton.getInstance(this).getRequestQueue();
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