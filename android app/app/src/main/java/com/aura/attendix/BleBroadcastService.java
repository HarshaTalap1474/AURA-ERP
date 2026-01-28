package com.aura.attendix;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo; // Import for ServiceType
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BleBroadcastService extends Service {

    public static final String ACTION_START_BROADCAST = "ACTION_START";
    public static final String ACTION_STOP_BROADCAST = "ACTION_STOP";
    private static final String CHANNEL_ID = "AuraServiceChannel";

    // The "Frequency" your ESP32 listens to
    private static final String SERVICE_UUID = "0000FEAA-0000-1000-8000-00805F9B34FB";

    private BluetoothLeAdvertiser advertiser;
    private boolean isBroadcasting = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_BROADCAST.equals(action)) {
                startForegroundService();
                startAdvertising();
            } else if (ACTION_STOP_BROADCAST.equals(action)) {
                stopAdvertising();
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundService() {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, StudentHomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Aura Attendance Active")
                .setContentText("Broadcasting ID to Classroom Sensor...")
                .setSmallIcon(R.drawable.ic_fingerprint)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();

        // ANDROID 14 COMPLIANCE FIX:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // "connectedDevice" type is required for Bluetooth operations
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            } else {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
                // Fallback for Android 10-13 if strict type needed, usually just '0' works before 14
            }

            try {
                startForeground(1, notification, type);
            } catch (Exception e) {
                // Fallback in case of weird OEM implementations
                startForeground(1, notification);
            }
        } else {
            startForeground(1, notification);
        }
    }

    private void startAdvertising() {
        if (isBroadcasting) return;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            advertiser = adapter.getBluetoothLeAdvertiser();
        }

        if (advertiser == null) return;

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String rollNo = prefs.getString("username", "UNKNOWN");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        // ESP32 FILTER LOGIC:
        // We include the SERVICE_UUID in the header.
        // The ESP32 scans for this specific UUID and ignores all other Bluetooth noise (headphones, watches, etc.)
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(new ParcelUuid(UUID.fromString(SERVICE_UUID)))
                .addServiceData(new ParcelUuid(UUID.fromString(SERVICE_UUID)), rollNo.getBytes(StandardCharsets.UTF_8))
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
        isBroadcasting = true;
    }

    private void stopAdvertising() {
        if (advertiser != null && isBroadcasting) {
            advertiser.stopAdvertising(advertiseCallback);
            isBroadcasting = false;
        }
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d("AuraBLE", "Service: Broadcasting Started Successfully");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e("AuraBLE", "Service: Broadcasting Failed - " + errorCode);
            stopSelf();
        }
    };

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Aura Attendance Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}