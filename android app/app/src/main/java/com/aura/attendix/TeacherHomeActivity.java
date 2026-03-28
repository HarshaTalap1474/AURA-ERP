package com.aura.attendix;

import android.view.View;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class TeacherHomeActivity extends AppCompatActivity {

    private TextView tvTeacherName, tvCurrentClass, tvRoom, tvTime;
    private ImageView ivProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_home);

        initViews();
        loadTeacherProfile();

        // Settings gear
        View ivSet = findViewById(R.id.ivSettings);
        if (ivSet != null) ivSet.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Profile icon in header
        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        // Monitor live attendance (opens Timetable)
        View btnViewAttendance = findViewById(R.id.btnViewAttendance);
        if (btnViewAttendance != null) btnViewAttendance.setOnClickListener(v ->
                startActivity(new Intent(this, TimetableActivity.class)));

        // Leave approvals card
        View cardLeaveApproval = findViewById(R.id.cardLeaveApproval);
        if (cardLeaveApproval != null) cardLeaveApproval.setOnClickListener(v ->
                startActivity(new Intent(this, LeaveApprovalsActivity.class)));

        // Timetable card
        View cardTimetable = findViewById(R.id.cardTimetable);
        if (cardTimetable != null) cardTimetable.setOnClickListener(v ->
                startActivity(new Intent(this, TimetableActivity.class)));
    }

    private void initViews() {
        tvTeacherName  = findViewById(R.id.tvTeacherName);
        tvCurrentClass = findViewById(R.id.tvCurrentClass);
        tvRoom         = findViewById(R.id.tvRoom);
        tvTime         = findViewById(R.id.tvTime);
        ivProfile      = findViewById(R.id.ivProfile);
    }

    private void loadTeacherProfile() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String name = prefs.getString("name", "Teacher");
        if (tvTeacherName != null) tvTeacherName.setText("Prof. " + name);
        fetchCurrentClass(prefs.getString("access_token", ""));
    }

    private void fetchCurrentClass(String token) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, NetworkConfig.URL_TEACHER_TIMETABLE, null,
                response -> {
                    try {
                        String subject = response.getString("subject");
                        String room    = response.getString("room");
                        String time    = response.getString("time_slot");
                        if (tvCurrentClass != null) tvCurrentClass.setText(subject);
                        if (tvRoom != null) tvRoom.setText("Room: " + room);
                        if (tvTime != null) tvTime.setText(time);
                    } catch (JSONException e) {
                        if (tvCurrentClass != null) tvCurrentClass.setText("No Active Class");
                    }
                },
                error -> { if (tvCurrentClass != null) tvCurrentClass.setText("No Active Class"); }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(6000, 0, 1f));
        VolleySingleton.getInstance(this).add(request);
    }
}