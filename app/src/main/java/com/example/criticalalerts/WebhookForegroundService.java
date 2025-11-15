package com.example.criticalalerts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

public class WebhookForegroundService extends Service {

    private static final String TAG = "WebhookService";
    private boolean isForegroundStarted = false;
    private WebhookServer server;
    private boolean serverRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isForegroundStarted) {
            createNotification();   // move here
            isForegroundStarted = true;
        }
        if (!serverRunning) {
            try {
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                serverRunning = true;
                Log.i(TAG, "Webhook server started on 8080");
            } catch (Exception e) {
                Log.e(TAG, "Server failed", e);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service created, starting WebhookServer...");

        server = new WebhookServer(8080, this);
    }

    private void createNotification() {
        String channelId = "webhook_alarm_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Critical Alerts",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        // Intent for STOP button
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction("STOP_ALARM");

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Critical Alerts Running")
                .setContentText("Webhook server active on port 8080")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .addAction(
                        android.R.drawable.ic_media_pause,
                        "STOP ALARM",
                        stopPendingIntent
                )
                .build();

        // ⚠ Foreground must be started in onStartCommand() — NOT here
        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed — stopping server");
        if (server != null) {
            server.stop();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
