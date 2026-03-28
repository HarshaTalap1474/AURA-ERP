package com.aura.attendix;

import android.view.View;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ParentHomeActivity extends AppCompatActivity {

    private TextView tvWelcomeName;
    private ImageView ivProfile, ivLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_home);

        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        ivProfile     = findViewById(R.id.ivProfile);
        ivLogout      = findViewById(R.id.ivLogout);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        tvWelcomeName.setText(prefs.getString("name", "Parent"));

        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        ivLogout.setOnClickListener(v -> logout());

        // Primary: view children attendance
        findViewById(R.id.btnViewAttendance).setOnClickListener(v ->
                startActivity(new Intent(this, ChildAttendanceActivity.class)));

        // Fee invoices (child's dues)
        android.view.View cardFees = findViewById(R.id.cardFeeInvoices);
        if (cardFees != null) {
            cardFees.setOnClickListener(v ->
                    startActivity(new Intent(this, FeeInvoicesActivity.class)));
        }

        findViewById(R.id.cardLeaves).setOnClickListener(v ->
                startActivity(new Intent(this, ComingSoonActivity.class)));
        findViewById(R.id.cardProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void logout() {
        getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
