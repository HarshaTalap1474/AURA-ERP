package com.aura.attendix;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GatePassScannerActivity — Security Officer scans a Student's single-use Gate Pass.
 * Calls /api/verify-gate-pass/ on the backend, which exhausts the cryptographic token.
 */
public class GatePassScannerActivity extends AppCompatActivity {

    private PreviewView cameraPreview;
    private ProgressBar progressBar;
    private TextView tvScanStatus, tvResultName, tvResultRoll, tvResultReason;
    private LinearLayout layoutResult, layoutBleResults;
    private Button btnManualBle, btnScanAgain;
    private ImageView ivResultIcon;

    private boolean hasScanned = false;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = !result.containsValue(false);
                if (allGranted) startCamera();
                else Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        // Reuse the same layout as SecurityScanActivity
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

        // Hide BLE references because Gate Pass doesn't need nearby fallback scans
        btnManualBle.setVisibility(View.GONE);
        layoutBleResults.setVisibility(View.GONE);

        tvScanStatus.setText("Point camera at student's Gate Pass QR code");

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupBarcode();

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        btnScanAgain.setOnClickListener(v -> resetToScanMode());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void setupBarcode() {
        BarcodeScannerOptions opts = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(opts);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show();
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

    private void verifyGatePass(String token) {
        progressBar.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Verifying Gate Pass cryptographic lock…");

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
                            String name   = response.optString("student_name", "Unknown");
                            String rollNo = response.optString("roll_no", "--");
                            String reason = response.optString("reason", "Authorized Leave");
                            showSuccess(name, rollNo, reason, response.optString("message"));
                        } else {
                            showFailure(response.optString("message", "Verification failed"));
                        }
                    },
                    error -> {
                        progressBar.setVisibility(View.GONE);
                        showFailure("Network Error or Invalid Token");
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
            showFailure("App Scan Error");
        }
    }

    private void showSuccess(String name, String roll, String reason, String msg) {
        cameraPreview.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        layoutResult.setBackgroundResource(R.drawable.bg_success_card);
        ivResultIcon.setImageResource(R.drawable.ic_check_circle);
        tvScanStatus.setText("✅ GATE PASS AUTHORIZED");
        tvResultName.setText(name);
        tvResultRoll.setText("Roll No: " + roll);
        tvResultReason.setText("Reason: " + reason);
        tvResultReason.setVisibility(View.VISIBLE);
        btnScanAgain.setVisibility(View.VISIBLE);
    }

    private void showFailure(String message) {
        cameraPreview.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        layoutResult.setBackgroundResource(R.drawable.bg_error_card);
        ivResultIcon.setImageResource(R.drawable.ic_warning);
        tvScanStatus.setText("❌ " + message);
        tvResultName.setText("Unauthorized");
        tvResultRoll.setText("This Gate Pass is inactive or forged.");
        tvResultReason.setVisibility(View.GONE);
        btnScanAgain.setVisibility(View.VISIBLE);
    }

    private void resetToScanMode() {
        hasScanned = false;
        layoutResult.setVisibility(View.GONE);
        cameraPreview.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Point camera at student's Gate Pass QR code");
        btnScanAgain.setVisibility(View.GONE);
        startCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
