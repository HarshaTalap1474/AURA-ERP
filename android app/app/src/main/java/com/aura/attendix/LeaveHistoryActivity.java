package com.aura.attendix;

import android.content.SharedPreferences;
import android.os.Bundle;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LeaveHistoryActivity — Student's own leave application history.
 * Shows status badges: PENDING (yellow), APPROVED (green), REJECTED (red).
 */
public class LeaveHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_history);

        recyclerView  = findViewById(R.id.recyclerLeaves);
        progressBar   = findViewById(R.id.progressBar);
        tvEmpty       = findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        loadHistory();
    }

    private void loadHistory() {
        progressBar.setVisibility(View.VISIBLE);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                NetworkConfig.URL_STUDENT_LEAVE_HISTORY,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONArray arr = response.getJSONArray("leaves");
                        List<JSONObject> leaves = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) leaves.add(arr.getJSONObject(i));

                        if (leaves.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            recyclerView.setAdapter(new LeaveHistoryAdapter(leaves));
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load leave history", Toast.LENGTH_SHORT).show();
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
}
