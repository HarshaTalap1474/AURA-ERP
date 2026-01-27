package com.harshatalap1474.attendix;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    // --- CONSTANTS ---
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final String TAG = "FORTRESS";
    // Arbitrary Manufacturer ID (0xFFFF is reserved for testing, so it's safe)
    private static final int MANUFACTURER_ID = 0xFFFF;

    // --- UI ELEMENTS ---
    private TextView tvStatus, tvPayload;

    // --- BLUETOOTH VARIABLES ---
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Link UI Variables
        tvStatus = findViewById(R.id.tvStatus);
        tvPayload = findViewById(R.id.tvPayload);

        // 2. Check Permissions on Launch
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            // Permissions already granted -> Ready to initialize BLE
            initBleSystem();
        }
    }

    // --- PERMISSION LOGIC ---

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT
                },
                PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Security Access Granted", Toast.LENGTH_SHORT).show();
                initBleSystem();
            } else {
                Toast.makeText(this, "Permission Denied. App cannot function.", Toast.LENGTH_LONG).show();
                tvStatus.setText("Status: PERMISSION DENIED");
                tvStatus.setTextColor(android.graphics.Color.RED);
            }
        }
    }

    // --- MAIN BLUETOOTH LOGIC ---
    private void initBleSystem() {
        Log.d(TAG, "Permissions OK. Starting BLE Subsystem...");

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Please Turn ON Bluetooth", Toast.LENGTH_LONG).show();
            tvStatus.setText("Status: Bluetooth is OFF");
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();

        // --- OPTIMIZATION FOR SIZE ---
        // 1. Shorten the ID to fit in one block (Max 15 chars)
        // Format: "R_01" (Roll) + "|" + "12345" (Last 5 digits of timestamp)
        String shortRoll = "B069";
        long time = System.currentTimeMillis() / 1000;
        String shortTime = String.valueOf(time).substring(5); // Take last 5 digits

        String rawData = shortRoll + "|" + shortTime; // Example: "R_01|67890" (10 chars)

        // 2. Get RAW BYTES (16 Bytes total)
        byte[] payloadBytes = AESUtils.encryptToBytes(rawData);

        if (payloadBytes == null) {
            Log.e(TAG, "Encryption Failed!");
            return;
        }

        // Display Base64 for humans, but send Raw Bytes to machine
        String displayString = android.util.Base64.encodeToString(payloadBytes, android.util.Base64.NO_WRAP);
        runOnUiThread(() -> tvPayload.setText("Sending (16 bytes):\n" + displayString));

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        // 3. Add RAW BYTES to Packet
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // CRITICAL: Hides name to save space
                .addManufacturerData(MANUFACTURER_ID, payloadBytes) // <--- Send Bytes, not String
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.i(TAG, ">>> BROADCASTING ACTIVE <<<");
                runOnUiThread(() -> {
                    tvStatus.setText("STATUS: BROADCASTING ACTIVE");
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#008000"));
                });
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
                Log.e(TAG, "Broadcast Failed: " + errorCode);
                // ... (Keep your existing error handling here) ...
                runOnUiThread(() -> {
                    tvStatus.setText("STATUS: FAILED (" + errorCode + ")");
                    tvStatus.setTextColor(android.graphics.Color.RED);
                });
            }
        };

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception: " + e.getMessage());
        }
    }
}