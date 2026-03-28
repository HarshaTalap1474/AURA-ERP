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
 * ChildAttendanceActivity — Parent's view of their child's attendance.
 * Loads children list, shows each child's overall percentage and
 * per-subject breakdown in a RecyclerView.
 */
public class ChildAttendanceActivity extends AppCompatActivity {

    private RecyclerView recyclerChildren;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_attendance);

        recyclerChildren = findViewById(R.id.recyclerChildren);
        progressBar      = findViewById(R.id.progressBar);
        tvEmpty          = findViewById(R.id.tvEmpty);

        recyclerChildren.setLayoutManager(new LinearLayoutManager(this));
        authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        loadChildren();
    }

    private void loadChildren() {
        progressBar.setVisibility(View.VISIBLE);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                NetworkConfig.URL_PARENT_CHILDREN,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONArray arr = response.getJSONArray("children");
                        List<JSONObject> children = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) children.add(arr.getJSONObject(i));

                        if (children.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            recyclerChildren.setAdapter(new ChildrenAdapter(children));
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load children data", Toast.LENGTH_SHORT).show();
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
