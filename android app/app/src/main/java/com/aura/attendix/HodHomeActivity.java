package com.aura.attendix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HodHomeActivity extends AppCompatActivity {

    private TextView tvWelcomeName;
    private ImageView ivProfile, ivLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hod_home);

        tvWelcomeName  = findViewById(R.id.tvWelcomeName);
        ivProfile      = findViewById(R.id.ivProfile);
        ivLogout       = findViewById(R.id.ivLogout);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        tvWelcomeName.setText("Dr. " + prefs.getString("name", "HOD"));

        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        ivLogout.setOnClickListener(v -> logout());

        // Card click listeners — navigate to real Activities in Phase 3
        findViewById(R.id.cardManageStudents).setOnClickListener(v ->
                startActivity(new Intent(this, ComingSoonActivity.class)));
        findViewById(R.id.cardTimetable).setOnClickListener(v ->
                startActivity(new Intent(this, ComingSoonActivity.class)));
        findViewById(R.id.cardLeaveApprovals).setOnClickListener(v ->
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
