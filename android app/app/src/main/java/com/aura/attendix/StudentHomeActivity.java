package com.aura.attendix;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StudentHomeActivity extends AppCompatActivity {

    private TextView tvWelcomeName, tvUsername, tvStatus;
    private Button btnScanAttendance, btnViewHistory, btnProfile;
    private ImageView ivBeaconIcon, ivProfile;
    private boolean isServiceRunning = false;

    // Permission Launcher
    private final ActivityResultLauncher<String[]> bluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    toggleService(); // Try starting again now that we have permission
                } else {
                    Toast.makeText(this, "Bluetooth permissions are required for attendance", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_home);

        verifyDeviceIdentity();
        initViews();
        loadERPProfile();

        // Button Logic: Check Permissions FIRST, then start service
        btnScanAttendance.setOnClickListener(v -> checkPermissionsAndToggle());
        // Inside initViews() or onCreate()
    }

    private void checkPermissionsAndToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ Permissions (Runtime)
            List<String> permissionsNeeded = new ArrayList<>();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            // Android 14+ FGS Permission (Usually auto-granted but good to check)
            if (Build.VERSION.SDK_INT >= 34) {
                // Note: FOREGROUND_SERVICE_CONNECTED_DEVICE is a normal permission, not runtime,
                // but checking prevents crashes on some custom ROMs
            }

            if (!permissionsNeeded.isEmpty()) {
                bluetoothPermissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
                return;
            }
        }

        // If we reach here, we have permissions (or are on old Android)
        toggleService();
    }

    private void toggleService() {
        Intent serviceIntent = new Intent(this, BleBroadcastService.class);

        if (!isServiceRunning) {
            serviceIntent.setAction(BleBroadcastService.ACTION_START_BROADCAST);
            // This call will now succeed because permissions are granted
            ContextCompat.startForegroundService(this, serviceIntent);
            updateUI(true);
            Toast.makeText(this, "Attendance Signal Active", Toast.LENGTH_SHORT).show();
        } else {
            serviceIntent.setAction(BleBroadcastService.ACTION_STOP_BROADCAST);
            startService(serviceIntent);
            updateUI(false);
            Toast.makeText(this, "Signal Stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(boolean active) {
        isServiceRunning = active;
        runOnUiThread(() -> {
            if (active) {
                tvStatus.setText("Status: ● Broadcasting (Background Mode)");
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                btnScanAttendance.setText("Stop Signal");
                btnScanAttendance.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
                ivBeaconIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            } else {
                tvStatus.setText("Status: ○ Offline");
                tvStatus.setTextColor(0xFF718096);
                btnScanAttendance.setText("Activate Signal");
                btnScanAttendance.setBackgroundColor(0xFF3F51B5);
                ivBeaconIcon.setColorFilter(0xFF3F51B5);
            }
        });
    }

    private void initViews() {
        tvWelcomeName = findViewById(R.id.tvWelcomeName);
        tvUsername = findViewById(R.id.tvUsername);
        tvStatus = findViewById(R.id.tvStatus);

        btnScanAttendance = findViewById(R.id.btnScanAttendance);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnProfile = findViewById(R.id.btnViewProfile);

        ivBeaconIcon = findViewById(R.id.ivBeaconIcon);
        ivProfile = findViewById(R.id.ivProfile);

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );

        btnViewHistory.setOnClickListener(v ->
                Toast.makeText(this, "Fetching Records...", Toast.LENGTH_SHORT).show()
        );

        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );
    }

    private void loadERPProfile() {
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        tvWelcomeName.setText("Welcome, " + prefs.getString("name", "Student"));
        tvUsername.setText("Roll No: " + prefs.getString("username", "---"));
    }

    private void verifyDeviceIdentity() {
        String currentId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String savedId = prefs.getString("device_fingerprint", "");

        if (!savedId.isEmpty() && !savedId.equals(currentId)) {
            Toast.makeText(this, "Security Violation: Device ID Mismatch", Toast.LENGTH_LONG).show();
            finishAffinity();
            System.exit(0);
        }
    }
}