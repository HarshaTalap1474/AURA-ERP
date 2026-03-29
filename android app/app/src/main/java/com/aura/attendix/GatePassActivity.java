package com.aura.attendix;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class GatePassActivity extends AppCompatActivity {

    private ImageView ivGatePassQr;
    private TextView tvStudentDetails, tvLeaveReason;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gate_pass);

        ivGatePassQr     = findViewById(R.id.ivGatePassQr);
        tvStudentDetails = findViewById(R.id.tvStudentDetails);
        tvLeaveReason    = findViewById(R.id.tvLeaveReason);

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Unpack Intent Extras
        String token  = getIntent().getStringExtra("GATE_PASS_TOKEN");
        String reason = getIntent().getStringExtra("LEAVE_REASON");

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Gate Pass Token missing or invalid.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String name   = prefs.getString("name", "Student");
        String rollNo = prefs.getString("username", "--");

        tvStudentDetails.setText(name + "\nRoll No: " + rollNo);
        tvLeaveReason.setText("Exit Reason: " + (reason != null ? reason : "Authorized Leave"));

        generateQrCode(token);
    }

    private void generateQrCode(String data) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(data, BarcodeFormat.QR_CODE, 600, 600);
            ivGatePassQr.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Toast.makeText(this, "Failed to generate Gate Pass QR", Toast.LENGTH_SHORT).show();
        }
    }
}
