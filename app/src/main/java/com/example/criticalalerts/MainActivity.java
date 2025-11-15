package com.example.criticalalerts;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.content.Intent;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start webhook server foreground service
        Intent serviceIntent = new Intent(this, WebhookForegroundService.class);
        startForegroundService(serviceIntent);

        // Setup UI button
        Button stopButton = findViewById(R.id.stopButton);

        stopButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AlarmService.class);
            intent.setAction("STOP_ALARM");
            startService(intent);
        });
    }
}
