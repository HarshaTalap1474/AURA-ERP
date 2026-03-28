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
 * FeeInvoicesActivity — Finance Clerk / Student / Parent fee invoice list.
 * Role-aware: students see their own invoices, clerks see all unpaid ones.
 * Each invoice card shows amount, due date, and a Pay button (stub).
 */
public class FeeInvoicesActivity extends AppCompatActivity {

    private RecyclerView recyclerInvoices;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvTitle;
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fee_invoices);

        recyclerInvoices = findViewById(R.id.recyclerInvoices);
        progressBar      = findViewById(R.id.progressBar);
        tvEmpty          = findViewById(R.id.tvEmpty);
        tvTitle          = findViewById(R.id.tvTitle);

        recyclerInvoices.setLayoutManager(new LinearLayoutManager(this));
        authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        String role = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("role", "STUDENT");
        tvTitle.setText("FINANCE_CLERK".equals(role) ? "All Pending Invoices" : "My Fee Invoices");

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        loadInvoices();
    }

    private void loadInvoices() {
        progressBar.setVisibility(View.VISIBLE);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                NetworkConfig.URL_FEE_INVOICES,
                null,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONArray arr = response.getJSONArray("invoices");
                        List<JSONObject> invoices = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) invoices.add(arr.getJSONObject(i));

                        if (invoices.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText("✅ No pending invoices. You're all clear!");
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            recyclerInvoices.setAdapter(new InvoicesAdapter(invoices, this));
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Parse error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load invoices", Toast.LENGTH_SHORT).show();
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
