package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TimetableActivity — Teacher's daily timetable.
 * Shows today's slots with UPCOMING/ACTIVE/FINISHED status badges.
 * Tap an UPCOMING slot → confirm → POST to start the class → go to LiveMonitorActivity.
 * Shows "End Class" button if a session is already active.
 */
public class TimetableActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvDay, tvNoSlots;
    private MaterialButton btnEndClass;
    private LinearLayout layoutActiveSession;

    private List<JSONObject> slots = new ArrayList<>();
    private int activeLectureId = -1;
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timetable);

        recyclerView        = findViewById(R.id.recyclerTimetable);
        progressBar         = findViewById(R.id.progressBar);
        tvDay               = findViewById(R.id.tvDay);
        tvNoSlots           = findViewById(R.id.tvNoSlots);
        btnEndClass         = findViewById(R.id.btnEndClass);
        layoutActiveSession = findViewById(R.id.layoutActiveSession);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        btnEndClass.setOnClickListener(v -> endClass());

        loadTimetable();
    }

    private void loadTimetable() {
        progressBar.setVisibility(View.VISIBLE);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                NetworkConfig.URL_TEACHER_TIMETABLE,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        tvDay.setText(response.optString("day", "Today"));
                        activeLectureId = response.optInt("active_lecture_id", -1);
                        boolean hasActive = response.optBoolean("has_active_lecture", false);

                        layoutActiveSession.setVisibility(hasActive ? View.VISIBLE : View.GONE);
                        if (hasActive) {
                            btnEndClass.setText("🔴 End Active Class");
                        }

                        JSONArray timetable = response.getJSONArray("timetable");
                        slots.clear();
                        for (int i = 0; i < timetable.length(); i++) {
                            slots.add(timetable.getJSONObject(i));
                        }

                        if (slots.isEmpty()) {
                            tvNoSlots.setVisibility(View.VISIBLE);
                        } else {
                            tvNoSlots.setVisibility(View.GONE);
                            setupAdapter();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load timetable", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + authToken);
                return headers;
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(6000, 0, 1.0f));
        VolleySingleton.getInstance(this).add(request);
    }

    private void setupAdapter() {
        TimetableAdapter adapter = new TimetableAdapter(slots, slot -> {
            // Card tapped — start class if UPCOMING
            String status = slot.optString("status", "");
            if ("ACTIVE".equals(status)) {
                int lectureId = slot.optInt("active_lecture_id", -1);
                if (lectureId > 0) openLiveMonitor(lectureId);
            } else if ("UPCOMING".equals(status)) {
                startClass(slot.optInt("timetable_id", -1));
            } else {
                Toast.makeText(this, "This class has already finished.", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void startClass(int timetableId) {
        if (timetableId < 0) return;
        progressBar.setVisibility(View.VISIBLE);
        try {
            JSONObject body = new JSONObject();
            body.put("timetable_id", timetableId);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    NetworkConfig.URL_START_CLASS,
                    body,
                    response -> {
                        progressBar.setVisibility(View.GONE);
                        int lectureId = response.optInt("lecture_id", -1);
                        Toast.makeText(this, response.optString("message", "Class started!"), Toast.LENGTH_SHORT).show();
                        if (lectureId > 0) openLiveMonitor(lectureId);
                    },
                    error -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Could not start class", Toast.LENGTH_SHORT).show();
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + authToken);
                    return headers;
                }
            };
            request.setRetryPolicy(new DefaultRetryPolicy(6000, 0, 1.0f));
            VolleySingleton.getInstance(this).add(request);
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void endClass() {
        progressBar.setVisibility(View.VISIBLE);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                NetworkConfig.URL_END_CLASS,
                new JSONObject(),
                response -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Session ended successfully.", Toast.LENGTH_SHORT).show();
                    loadTimetable(); // Refresh
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not end session", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + authToken);
                return headers;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(6000, 0, 1.0f));
        VolleySingleton.getInstance(this).add(request);
    }

    private void openLiveMonitor(int lectureId) {
        Intent intent = new Intent(this, LiveMonitorActivity.class);
        intent.putExtra("lecture_id", lectureId);
        startActivity(intent);
    }
}
