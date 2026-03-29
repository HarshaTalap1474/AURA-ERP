package com.aura.attendix;

import android.view.View;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SecurityHomeActivity extends AppCompatActivity {

    private TextView tvWelcomeName;
    private ImageView ivProfile, ivLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_home);

        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        ivProfile     = findViewById(R.id.ivProfile);
        ivLogout      = findViewById(R.id.ivLogout);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        tvWelcomeName.setText(prefs.getString("name", "Officer"));

        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        ivLogout.setOnClickListener(v -> logout());

        // Primary action: QR scanner for Virtual ID verification
        findViewById(R.id.btnScanQr).setOnClickListener(v ->
                startActivity(new Intent(this, SecurityScanActivity.class)));

        // Secondary action: QR scanner for Gate Pass verification
        findViewById(R.id.btnScanGatePass).setOnClickListener(v ->
                startActivity(new Intent(this, GatePassScannerActivity.class)));

        findViewById(R.id.cardProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.cardChangePassword).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
    }

    private void logout() {
        getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
