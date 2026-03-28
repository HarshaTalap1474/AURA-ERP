package com.aura.attendix;

import android.view.View;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SuperAdminHomeActivity extends AppCompatActivity {

    private TextView tvWelcomeName;
    private ImageView ivProfile, ivLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin_home);

        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        ivProfile     = findViewById(R.id.ivProfile);
        ivLogout      = findViewById(R.id.ivLogout);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        tvWelcomeName.setText(prefs.getString("name", "Admin"));

        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        ivLogout.setOnClickListener(v -> logout());

        findViewById(R.id.cardStudents).setOnClickListener(v ->
                startActivity(new Intent(this, ComingSoonActivity.class)));
        findViewById(R.id.cardTimetable).setOnClickListener(v ->
                startActivity(new Intent(this, ComingSoonActivity.class)));
        findViewById(R.id.cardDeviceIntegrity).setOnClickListener(v ->
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
