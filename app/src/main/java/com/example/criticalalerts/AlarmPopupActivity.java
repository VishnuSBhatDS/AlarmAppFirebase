package com.example.criticalalerts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class AlarmPopupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_alarm_popup);

        Button stopButton = findViewById(R.id.stopPopupButton);
        stopButton.setOnClickListener(v -> {
            Intent i = new Intent(this, AlarmService.class);
            i.setAction("STOP_ALARM");
            startService(i);
            finish();
        });
    }
}
