package com.example.criticalalerts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

public class AlarmService extends Service {

    private static final String CHANNEL_ID = "alarm_channel";
    private static final int NOTIF_ID = 5001;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    public static Intent stopAlarmIntent(Context ctx) {
        Intent i = new Intent(ctx, AlarmService.class);
        i.setAction("STOP_ALARM");
        return i;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && "PLAY_ALARM".equals(intent.getAction())) {
            startAlarm();
        } else if (intent != null && "STOP_ALARM".equals(intent.getAction())) {
            stopAlarm();
        }

        return START_STICKY;
    }

    private void startAlarm() {

        // 1. Build popup intent first
        Intent fullscreen = new Intent(this, AlarmActivity.class);
        fullscreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                1,
                fullscreen,
                PendingIntent.FLAG_IMMUTABLE
        );

        // 2. STOP action
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                2,
                stopAlarmIntent(this),
                PendingIntent.FLAG_IMMUTABLE
        );

        // 3. Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Critical Alarm")
                .setContentText("Alarm is ringing")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true);

        Notification notification = builder.build();

        // 4. THIS MUST BE FIRST
        startForeground(NOTIF_ID, notification);

        // 5. NOW play sound + vibration
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.alarm);
            mediaPlayer.setLooping(true);
        }
        mediaPlayer.start();

        long[] pattern = {0, 500, 500};
        vibrator.vibrate(pattern, 0);
    }

    private void stopAlarm() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        vibrator.cancel();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Alarm Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
