package com.aura.attendix;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LiveMonitorActivity — Real-time attendance dashboard (Teacher).
 *
 * Polls GET /api/teacher/lecture/<id>/live/ every 3 seconds.
 * Shows present count, absent count, percentage bar, and a
 * scrollable list of all expected students colored green/red.
 */
public class LiveMonitorActivity extends AppCompatActivity {

    private TextView tvCourseName, tvPresentCount, tvAbsentCount, tvPercentage, tvStatus;
    private LinearProgressIndicator progressIndicator;
    private ProgressBar progressBar;
    private RecyclerView recyclerStudents;

    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private static final int POLL_INTERVAL_MS = 3_000;
    private int lectureId;
    private String authToken;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchLiveData();
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_monitor);

        lectureId         = getIntent().getIntExtra("lecture_id", -1);
        tvCourseName      = findViewById(R.id.tvCourseName);
        tvPresentCount    = findViewById(R.id.tvPresentCount);
        tvAbsentCount     = findViewById(R.id.tvAbsentCount);
        tvPercentage      = findViewById(R.id.tvPercentage);
        tvStatus          = findViewById(R.id.tvStatus);
        progressIndicator = findViewById(R.id.progressAttendance);
        progressBar       = findViewById(R.id.progressBar);
        recyclerStudents  = findViewById(R.id.recyclerStudents);

        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        if (lectureId < 0) {
            Toast.makeText(this, "Invalid lecture ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pollRunnable.run(); // Initial immediate load
    }

    private void fetchLiveData() {
        String url = String.format(NetworkConfig.URL_LIVE_MONITOR, lectureId);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        tvCourseName.setText(response.optString("course", "Live Class"));
                        int present = response.optInt("present_count", 0);
                        int absent  = response.optInt("absent_count", 0);
                        int total   = response.optInt("total", 0);
                        double pct  = response.optDouble("percentage", 0.0);

                        tvPresentCount.setText(String.valueOf(present));
                        tvAbsentCount.setText(String.valueOf(absent));
                        tvPercentage.setText(String.format("%.1f%%", pct));
                        progressIndicator.setProgressCompat((int) pct, true);
                        tvStatus.setText(response.optBoolean("is_active", false) ? "● LIVE" : "● ENDED");

                        // Populate student list
                        JSONArray studentArray = response.getJSONArray("students");
                        List<JSONObject> students = new ArrayList<>();
                        for (int i = 0; i < studentArray.length(); i++) {
                            students.add(studentArray.getJSONObject(i));
                        }
                        LiveStudentAdapter adapter = new LiveStudentAdapter(students);
                        recyclerStudents.setAdapter(adapter);

                    } catch (Exception e) {
                        // Silently ignore parse errors during live polling
                    }
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    // Don't toast every 3 seconds on error, just stop polling
                    pollHandler.removeCallbacks(pollRunnable);
                    tvStatus.setText("● DISCONNECTED");
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

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(pollRunnable);
    }
}
