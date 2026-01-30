package com.aura.attendix;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;

public class AttendanceHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvTermInfo, tvOverallStats;
    private ImageView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_history);

        // Bind Views
        recyclerView = findViewById(R.id.rvHistory);
        tvTermInfo = findViewById(R.id.tvTermInfo);
        tvOverallStats = findViewById(R.id.tvOverallStats);
        btnBack = findViewById(R.id.btnBack);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Back Button Logic
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        fetchHistory();
    }

    private void fetchHistory() {
        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                NetworkConfig.URL_ATTENDANCE_HISTORY,
                null,
                response -> {
                    try {
                        // 1. Update Top Header Cards
                        String semester = response.optString("semester", "SEM --");
                        tvTermInfo.setText("Course of Term (Semester) > " + semester);

                        double overallPct = response.optDouble("overall_percentage", 0.0);
                        int overallPres = response.optInt("overall_present", 0);
                        int overallTot = response.optInt("overall_total", 0);

                        String overallText;
                        if (overallTot == 0) {
                            overallText = "Overall Attendance Percentage : 0 / 0 = 0%";
                        } else {
                            overallText = String.format("Overall Attendance Percentage : %d / %d = %.1f%%",
                                    overallPres, overallTot, overallPct);
                        }
                        tvOverallStats.setText(overallText);

                        // 2. Populate List
                        JSONArray history = response.getJSONArray("history");
                        if (history.length() > 0) {
                            AttendanceAdapter adapter = new AttendanceAdapter(history);
                            recyclerView.setAdapter(adapter);
                        } else {
                            Toast.makeText(this, "No subjects found for this semester.", Toast.LENGTH_LONG).show();
                        }

                    } catch (JSONException e) {
                        Toast.makeText(this, "Error parsing attendance data", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    String errorMsg = "Failed to load history";
                    if (error.networkResponse != null) {
                        errorMsg += " (Code " + error.networkResponse.statusCode + ")";
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
                // Send Token
                headers.put("Authorization", "Bearer " + prefs.getString("access_token", ""));
                return headers;
            }
        };

        queue.add(request);
    }
}