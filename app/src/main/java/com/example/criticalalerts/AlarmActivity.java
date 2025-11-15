package com.example.criticalalerts;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;

public class AlarmActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force screen wake + show above lock screen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        setContentView(R.layout.activity_alarm);

        Button stop = findViewById(R.id.alarmStopBtn);
        stop.setOnClickListener(v -> {
            startService(AlarmService.stopAlarmIntent(this));
            finish();
        });
    }
}
