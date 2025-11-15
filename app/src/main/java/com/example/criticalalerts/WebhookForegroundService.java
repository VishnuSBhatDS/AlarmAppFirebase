package com.example.criticalalerts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import fi.iki.elonen.NanoHTTPD;

public class WebhookForegroundService extends Service {
    private static final String TAG = "WebhookService";
    private static final String CHANNEL_ID = "webhook_alarm_channel";
    private static final int NOTIF_ID = 1001;

    private WebhookServer server;
    private boolean serverRunning = false;
    private boolean foregroundStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        server = new WebhookServer(8080, this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundStarted) {
            startForegroundStickyNotification();
            foregroundStarted = true;
        }

        if (!serverRunning) {
            try {
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                serverRunning = true;
                Log.i(TAG, "Webhook server started on port 8080");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start Webhook server: " + e.getMessage(), e);
            }
        }

        return START_STICKY;
    }

    private void startForegroundStickyNotification() {
        // STOP action -> sends STOP_ALARM to AlarmService
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction("STOP_ALARM");
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1,
                stopIntent,
                getPendingFlags()
        );

        // Clicking notification opens MainActivity
        Intent uiIntent = new Intent(this, MainActivity.class);
        PendingIntent uiPending = PendingIntent.getActivity(
                this,
                2,
                uiIntent,
                getPendingFlags()
        );

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Critical Alerts")
                .setContentText("Webhook server active on port 8080")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "STOP ALARM", stopPending)
                .setContentIntent(uiPending)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        startForeground(NOTIF_ID, nb.build());
        Log.i(TAG, "Webhook foreground notification posted");
    }

    private int getPendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Critical Alerts",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        serverRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
