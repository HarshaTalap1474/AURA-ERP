package com.aura.attendix;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.Volley;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * LeaveApplyActivity — Student leave application form.
 * Fields: leave type (spinner), start date, end date, reason.
 * POSTs to /api/student/leave/apply/
 */
public class LeaveApplyActivity extends AppCompatActivity {

    private Spinner spinnerLeaveType;
    private TextInputEditText etStartDate, etEndDate, etReason;
    private MaterialButton btnSubmit;
    private ProgressBar progressBar;
    private String authToken;

    private static final String[] LEAVE_TYPES = {
            "MEDICAL", "PERSONAL", "FAMILY", "SPORTS", "OTHER"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_apply);

        spinnerLeaveType = findViewById(R.id.spinnerLeaveType);
        etStartDate      = findViewById(R.id.etStartDate);
        etEndDate        = findViewById(R.id.etEndDate);
        etReason         = findViewById(R.id.etReason);
        btnSubmit        = findViewById(R.id.btnSubmit);
        progressBar      = findViewById(R.id.progressBar);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, LEAVE_TYPES);
        spinnerLeaveType.setAdapter(adapter);

        authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> submitLeave());
    }

    private void submitLeave() {
        String leaveType  = LEAVE_TYPES[spinnerLeaveType.getSelectedItemPosition()];
        String startDate  = etStartDate.getText() != null ? etStartDate.getText().toString().trim() : "";
        String endDate    = etEndDate.getText()   != null ? etEndDate.getText().toString().trim()   : "";
        String reason     = etReason.getText()    != null ? etReason.getText().toString().trim()    : "";

        if (startDate.isEmpty() || endDate.isEmpty() || reason.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);

        try {
            JSONObject body = new JSONObject();
            body.put("leave_type", leaveType);
            body.put("start_date", startDate);
            body.put("end_date", endDate);
            body.put("reason", reason);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    NetworkConfig.URL_STUDENT_APPLY_LEAVE,
                    body,
                    response -> {
                        progressBar.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        Toast.makeText(this,
                                response.optString("message", "Application submitted!"),
                                Toast.LENGTH_LONG).show();
                        finish();
                    },
                    error -> {
                        progressBar.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        Toast.makeText(this, "Submission failed. Try again.", Toast.LENGTH_SHORT).show();
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
            btnSubmit.setEnabled(true);
        }
    }
}
