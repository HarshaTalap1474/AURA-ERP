package com.aura.attendix;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.toolbox.Volley;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SecurityScanActivity — Flagship 2-Stage Security Scanner
 *
 * Stage 1 (Primary):  CameraX + ML Kit scans student's Virtual ID QR code.
 *                     POST token to /api/verify-gate-pass/
 *                     ✅ SUCCESS → show green result card (name, roll no, reason)
 *                     ❌ FAIL    → reveal "Manual BLE Scan" fallback button
 *
 * Stage 2 (Fallback): BLE scan of nearby devices. Detects broadcasting students
 *                     within Bluetooth range, lists their bluetooth MAC addresses
 *                     for manual identity verification / roll call.
 */
public class SecurityScanActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────
    private PreviewView cameraPreview;
    private ProgressBar progressBar;
    private TextView tvScanStatus, tvResultName, tvResultRoll, tvResultReason;
    private LinearLayout layoutResult, layoutBleResults;
    private Button btnManualBle, btnScanAgain;
    private TextView tvBleList;
    private ImageView ivResultIcon;

    // ── State ──────────────────────────────────────────────────────
    private boolean hasScanned = false;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;

    // ── BLE ────────────────────────────────────────────────────────
    private BluetoothLeScanner leScanner;
    private Handler bleStopHandler = new Handler(Looper.getMainLooper());
    private List<String> detectedDevices = new ArrayList<>();

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = !result.containsValue(false);
                if (allGranted) startCamera();
                else Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_scan);

        cameraPreview    = findViewById(R.id.cameraPreview);
        progressBar      = findViewById(R.id.progressBar);
        tvScanStatus     = findViewById(R.id.tvScanStatus);
        layoutResult     = findViewById(R.id.layoutResult);
        ivResultIcon     = findViewById(R.id.ivResultIcon);
        tvResultName     = findViewById(R.id.tvResultName);
        tvResultRoll     = findViewById(R.id.tvResultRoll);
        tvResultReason   = findViewById(R.id.tvResultReason);
        btnManualBle     = findViewById(R.id.btnManualBle);
        btnScanAgain     = findViewById(R.id.btnScanAgain);
        layoutBleResults = findViewById(R.id.layoutBleResults);
        tvBleList        = findViewById(R.id.tvBleList);

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupBarcode();

        // Back arrow
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Scan again → reset to QR mode
        btnScanAgain.setOnClickListener(v -> resetToScanMode());

        // Fallback: Manual BLE scanner (stage 2)
        btnManualBle.setOnClickListener(v -> startBleScan());

        requestCameraAndStart();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // STAGE 1: CameraX QR Scanner
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void setupBarcode() {
        BarcodeScannerOptions opts = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(opts);
    }

    private void requestCameraAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Toast.makeText(this, "Camera failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (hasScanned || imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }

            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String raw = barcode.getRawValue();
                            if (raw != null && !raw.isEmpty()) {
                                hasScanned = true;
                                runOnUiThread(() -> verifyGatePass(raw));
                                break;
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // STAGE 1: API Gate Pass Verification
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void verifyGatePass(String token) {
        progressBar.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Verifying identity…");

        String authToken = getSharedPreferences("UserSession", MODE_PRIVATE)
                .getString("access_token", "");

        try {
            JSONObject body = new JSONObject();
            body.put("qr_token", token);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    NetworkConfig.URL_VERIFY_GATE_PASS,
                    body,
                    response -> {
                        progressBar.setVisibility(View.GONE);
                        boolean success = response.optBoolean("success", false);
                        if (success) {
                            showSuccess(
                                    response.optString("student_name", "Unknown"),
                                    response.optString("roll_no", "--"),
                                    response.optString("reason", "--")
                            );
                        } else {
                            showFailure(response.optString("message", "Verification failed"));
                        }
                    },
                    error -> {
                        progressBar.setVisibility(View.GONE);
                        String msg = error.networkResponse != null
                                ? "Error " + error.networkResponse.statusCode
                                : "Network error";
                        showFailure(msg);
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
            showFailure("Scan error: " + e.getMessage());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // RESULT UI
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void showSuccess(String name, String roll, String reason) {
        cameraPreview.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        layoutResult.setBackgroundResource(R.drawable.bg_success_card);
        ivResultIcon.setImageResource(R.drawable.ic_check_circle);
        tvScanStatus.setText("✅ IDENTITY VERIFIED");
        tvResultName.setText(name);
        tvResultRoll.setText("Roll No: " + roll);
        tvResultReason.setText("Authorized Exit: " + reason);
        tvResultReason.setVisibility(View.VISIBLE);
        btnManualBle.setVisibility(View.GONE);
        btnScanAgain.setVisibility(View.VISIBLE);
    }

    private void showFailure(String message) {
        cameraPreview.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        layoutResult.setBackgroundResource(R.drawable.bg_error_card);
        ivResultIcon.setImageResource(R.drawable.ic_warning);
        tvScanStatus.setText("❌ " + message);
        tvResultName.setText("Gate Pass Invalid");
        tvResultRoll.setText("Could not verify this QR Code");
        tvResultReason.setVisibility(View.GONE);
        // Show the fallback BLE scanner button
        btnManualBle.setVisibility(View.VISIBLE);
        btnScanAgain.setVisibility(View.VISIBLE);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // STAGE 2: BLE Fallback Scanner
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void startBleScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is off. Please enable it.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth scan permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        leScanner = adapter.getBluetoothLeScanner();
        detectedDevices.clear();
        layoutBleResults.setVisibility(View.VISIBLE);
        tvBleList.setText("Scanning nearby devices…");
        btnManualBle.setText("Scanning… (10s)");
        btnManualBle.setEnabled(false);

        leScanner.startScan(bleScanCallback);

        // Auto-stop after 10 seconds
        bleStopHandler.postDelayed(() -> {
            leScanner.stopScan(bleScanCallback);
            btnManualBle.setEnabled(true);
            btnManualBle.setText("Re-Scan BLE");
            if (detectedDevices.isEmpty()) {
                tvBleList.setText("No broadcasting student devices detected nearby.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < detectedDevices.size(); i++) {
                    sb.append((i + 1)).append(". ").append(detectedDevices.get(i)).append("\n");
                }
                tvBleList.setText(sb.toString().trim());
            }
        }, 10_000);
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String deviceInfo = result.getDevice().getName() != null
                    ? result.getDevice().getName() + " (" + result.getDevice().getAddress() + ")"
                    : result.getDevice().getAddress();

            if (!detectedDevices.contains(deviceInfo)) {
                detectedDevices.add(deviceInfo);
                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < detectedDevices.size(); i++) {
                        sb.append((i + 1)).append(". ").append(detectedDevices.get(i)).append("\n");
                    }
                    tvBleList.setText(sb.toString().trim());
                });
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() ->
                    tvBleList.setText("BLE Scan failed (Error " + errorCode + ")")
            );
        }
    };

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // HELPERS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void resetToScanMode() {
        hasScanned = false;
        layoutResult.setVisibility(View.GONE);
        layoutBleResults.setVisibility(View.GONE);
        cameraPreview.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Point camera at student's Virtual ID QR code");
        btnManualBle.setVisibility(View.GONE);
        btnScanAgain.setVisibility(View.GONE);
        startCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        bleStopHandler.removeCallbacksAndMessages(null);
        if (leScanner != null &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED) {
            leScanner.stopScan(bleScanCallback);
        }
    }
}
