package com.aura.attendix;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private com.google.android.material.textfield.TextInputEditText
            etRollNo, etFirstName, etLastName, etEmail, etPhone,
            etDob, etAddress, etEmergencyContact;
    private AutoCompleteTextView etBloodGroup;
    private com.google.android.material.button.MaterialButton btnSaveProfile;
    private TextView tvAvatar, tvProfileName, tvProfileRole;

    private String originalFirstName = "", originalLastName = "";
    private String originalEmail     = "", originalPhone     = "";
    private String originalDob       = "", originalBloodGroup = "";
    private String originalAddress   = "", originalEmergency  = "";

    private static final String[] BLOOD_GROUPS = {"A+","A-","B+","B-","O+","O-","AB+","AB-"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        loadCurrentData();
        setupBloodGroupDropdown();
        setupDobPicker();
        setupTextWatchers();
        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setAlpha(0.4f);
        btnSaveProfile.setOnClickListener(v -> saveChanges());
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        findViewById(R.id.ivSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void initViews() {
        etRollNo           = findViewById(R.id.etRollNo);
        etFirstName        = findViewById(R.id.etFirstName);
        etLastName         = findViewById(R.id.etLastName);
        etEmail            = findViewById(R.id.etEmail);
        etPhone            = findViewById(R.id.etPhone);
        etDob              = findViewById(R.id.etDob);
        etBloodGroup       = findViewById(R.id.etBloodGroup);
        etAddress          = findViewById(R.id.etAddress);
        etEmergencyContact = findViewById(R.id.etEmergencyContact);
        btnSaveProfile     = findViewById(R.id.btnSaveProfile);
        tvAvatar           = findViewById(R.id.tvAvatar);
        tvProfileName      = findViewById(R.id.tvProfileName);
        tvProfileRole      = findViewById(R.id.tvProfileRole);
    }

    private void loadCurrentData() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);

        String username  = prefs.getString("username", "—");
        String fullName  = prefs.getString("name", "");
        String role      = prefs.getString("role", "Student");
        String email     = prefs.getString("email", "");
        String phone     = prefs.getString("phone", "");
        String dob       = prefs.getString("dob", "");
        String blood     = prefs.getString("blood_group", "");
        String address   = prefs.getString("address", "");
        String emergency = prefs.getString("emergency_contact", "");

        // Parse name
        String[] parts = fullName.split(" ", 2);
        originalFirstName  = parts.length > 0 ? parts[0] : "";
        originalLastName   = parts.length > 1 ? parts[1] : "";
        originalEmail      = email;
        originalPhone      = phone;
        originalDob        = dob;
        originalBloodGroup = blood;
        originalAddress    = address;
        originalEmergency  = emergency;

        // Avatar initials
        String initials = "";
        if (!originalFirstName.isEmpty()) initials += originalFirstName.charAt(0);
        if (!originalLastName.isEmpty())  initials += originalLastName.charAt(0);
        tvAvatar.setText(initials.isEmpty() ? "?" : initials.toUpperCase());

        tvProfileName.setText(fullName.isEmpty() ? username : fullName);
        tvProfileRole.setText(formatRole(role));

        etRollNo.setText(username);
        etFirstName.setText(originalFirstName);
        etLastName.setText(originalLastName);
        etEmail.setText(originalEmail);
        etPhone.setText(originalPhone);
        etDob.setText(originalDob);
        etBloodGroup.setText(originalBloodGroup, false);
        etAddress.setText(originalAddress);
        etEmergencyContact.setText(originalEmergency);
    }

    private String formatRole(String raw) {
        if (raw == null) return "User";
        String s = raw.replace("_", " ").replace("-", " ").toLowerCase();
        StringBuilder sb = new StringBuilder(s.length());
        boolean capitalizeNext = true;
        for (char c : s.toCharArray()) {
            if (c == ' ') { sb.append(c); capitalizeNext = true; }
            else if (capitalizeNext) { sb.append(Character.toUpperCase(c)); capitalizeNext = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    private void setupBloodGroupDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, BLOOD_GROUPS);
        etBloodGroup.setAdapter(adapter);
    }

    private void setupDobPicker() {
        etDob.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                String date = String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
                etDob.setText(date);
                checkIfModified();
            }, c.get(Calendar.YEAR) - 20, c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupTextWatchers() {
        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { checkIfModified(); }
            public void afterTextChanged(Editable s) {}
        };
        etFirstName.addTextChangedListener(w);
        etLastName.addTextChangedListener(w);
        etEmail.addTextChangedListener(w);
        etPhone.addTextChangedListener(w);
        etAddress.addTextChangedListener(w);
        etEmergencyContact.addTextChangedListener(w);
        etBloodGroup.addTextChangedListener(w);
    }

    private void checkIfModified() {
        boolean changed =
            !etFirstName.getText().toString().trim().equals(originalFirstName) ||
            !etLastName.getText().toString().trim().equals(originalLastName)   ||
            !etEmail.getText().toString().trim().equals(originalEmail)          ||
            !etPhone.getText().toString().trim().equals(originalPhone)          ||
            !etDob.getText().toString().trim().equals(originalDob)              ||
            !etBloodGroup.getText().toString().trim().equals(originalBloodGroup)||
            !etAddress.getText().toString().trim().equals(originalAddress)      ||
            !etEmergencyContact.getText().toString().trim().equals(originalEmergency);

        boolean valid = !etFirstName.getText().toString().trim().isEmpty();
        btnSaveProfile.setEnabled(changed && valid);
        btnSaveProfile.setAlpha(changed && valid ? 1f : 0.4f);
    }

    private void saveChanges() {
        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setAlpha(0.4f);

        JSONObject payload = new JSONObject();
        try {
            payload.put("first_name",        etFirstName.getText().toString().trim());
            payload.put("last_name",         etLastName.getText().toString().trim());
            payload.put("email",             etEmail.getText().toString().trim());
            payload.put("phone_number",      etPhone.getText().toString().trim());
            payload.put("dob",               etDob.getText().toString().trim());
            payload.put("blood_group",       etBloodGroup.getText().toString().trim());
            payload.put("address",           etAddress.getText().toString().trim());
            payload.put("emergency_contact", etEmergencyContact.getText().toString().trim());
        } catch (JSONException e) { return; }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST, NetworkConfig.URL_PROFILE_UPDATE, payload,
                response -> {
                    SharedPreferences.Editor ed = getSharedPreferences("UserSession", MODE_PRIVATE).edit();
                    ed.putString("name",              etFirstName.getText().toString().trim() + " " + etLastName.getText().toString().trim());
                    ed.putString("email",             etEmail.getText().toString().trim());
                    ed.putString("phone",             etPhone.getText().toString().trim());
                    ed.putString("dob",               etDob.getText().toString().trim());
                    ed.putString("blood_group",       etBloodGroup.getText().toString().trim());
                    ed.putString("address",           etAddress.getText().toString().trim());
                    ed.putString("emergency_contact", etEmergencyContact.getText().toString().trim());
                    ed.apply();
                    Toast.makeText(this, "Profile updated ✓", Toast.LENGTH_SHORT).show();
                    finish();
                },
                error -> {
                    checkIfModified();
                    String msg = "Update failed";
                    if (error.networkResponse != null) msg += " (" + error.networkResponse.statusCode + ")";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + getSharedPreferences("UserSession", MODE_PRIVATE).getString("access_token", ""));
                h.put("Content-Type", "application/json");
                return h;
            }
        };
        req.setRetryPolicy(new DefaultRetryPolicy(6000, 0, 1f));
        VolleySingleton.getInstance(this).add(req);
    }
}