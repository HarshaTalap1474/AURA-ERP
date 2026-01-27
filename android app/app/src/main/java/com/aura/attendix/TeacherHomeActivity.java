package com.aura.attendix;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;

public class TeacherHomeActivity extends AppCompatActivity {
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Toast.makeText(this, "Teacher Home", Toast.LENGTH_SHORT).show();
    }
}