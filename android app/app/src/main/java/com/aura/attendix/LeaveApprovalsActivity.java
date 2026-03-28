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
 * LeaveApprovalsActivity — Teacher / HOD / TG leave management.
 * Lists PENDING requests with Approve/Reject buttons.
 * Lists recently PROCESSED requests below.
 */
public class LeaveApprovalsActivity extends AppCompatActivity {

    private RecyclerView recyclerPending, recyclerProcessed;
    private ProgressBar progressBar;
    private TextView tvPendingCount, tvNoPending;
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_approvals);

        recyclerPending   = findViewById(R.id.recyclerPendingLeaves);
        recyclerProcessed = findViewById(R.id.recyclerProcessedLeaves);
        progressBar       = findViewById(R.id.progressBar);
        tvPendingCount    = findViewById(R.id.tvPendingCount);
        tvNoPending       = findViewById(R.id.tvNoPending);

        recyclerPending.setLayoutManager(new LinearLayoutManager(this));
        recyclerProcessed.setLayoutManager(new LinearLayoutManager(this));

        authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        loadLeaves();
    }

    private void loadLeaves() {
        progressBar.setVisibility(View.VISIBLE);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                NetworkConfig.URL_LEAVE_REQUESTS,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONArray pending   = response.getJSONArray("pending");
                        JSONArray processed = response.getJSONArray("processed");

                        tvPendingCount.setText(pending.length() + " Pending");
                        tvNoPending.setVisibility(pending.length() == 0 ? View.VISIBLE : View.GONE);

                        List<JSONObject> pendingList = toList(pending);
                        List<JSONObject> processedList = toList(processed);

                        // Pending adapter with action buttons
                        LeaveAdapter pendingAdapter = new LeaveAdapter(pendingList, true, this::processLeave);
                        recyclerPending.setAdapter(pendingAdapter);

                        // Processed adapter (read-only)
                        LeaveAdapter processedAdapter = new LeaveAdapter(processedList, false, null);
                        recyclerProcessed.setAdapter(processedAdapter);

                    } catch (Exception e) {
                        Toast.makeText(this, "Error loading leaves: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
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

    /**
     * Called by the adapter when Approve or Reject is tapped.
     * @param requestId  leave_request.id from backend
     * @param action     "approve" or "reject"
     */
    public void processLeave(int requestId, String action) {
        String url = String.format(NetworkConfig.URL_PROCESS_LEAVE, requestId);
        try {
            JSONObject body = new JSONObject();
            body.put("action", action);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    body,
                    response -> {
                        Toast.makeText(this,
                                response.optString("message", "Done"),
                                Toast.LENGTH_SHORT).show();
                        loadLeaves(); // Refresh
                    },
                    error -> Toast.makeText(this, "Action failed", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<JSONObject> toList(JSONArray array) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try { list.add(array.getJSONObject(i)); } catch (Exception ignored) {}
        }
        return list;
    }
}
