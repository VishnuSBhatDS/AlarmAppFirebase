package com.example.criticalalerts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

public class AlarmService extends Service {

    private static final String CHANNEL_ID = "alarm_channel";
    private static final int NOTIF_ID = 5001;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && "PLAY_ALARM".equals(intent.getAction())) {
            playAlarmSafe();
        } else if (intent != null && "STOP_ALARM".equals(intent.getAction())) {
            stopAlarm();
        }

        return START_STICKY;
    }

    private void playAlarmSafe() {

        forceMaxVolume();

        Notification notification = buildNotification();
        startForeground(NOTIF_ID, notification);

        startMediaPlayerLoop();
        startVibration();
    }

    private Notification buildNotification() {

        Intent full = new Intent(this, AlarmActivity.class);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent fullIntent = PendingIntent.getActivity(
                this, 1, full, PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent stopIntent = PendingIntent.getService(
                this, 2, new Intent(this, AlarmService.class).setAction("STOP_ALARM"),
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Critical Alarm")
                .setContentText("Alarm ringing")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_delete, "STOP", stopIntent)
                .setFullScreenIntent(fullIntent, true)
                .build();
    }

    private void startMediaPlayerLoop() {

        try {
            Uri sound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alarm);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, sound);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                );
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();   // Synchronous prepare = no silent failures
            mediaPlayer.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startVibration() {
        long[] pattern = {0, 500, 500};
        vibrator.vibrate(pattern, 0);
    }

    private void forceMaxVolume() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);

        am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                max,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
        );
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    private void stopAlarm() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        vibrator.cancel();
        stopForeground(true);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alarm);

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Alarm Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );

//            ch.setSound(soundUri, attrs);
            ch.setSound(null, null);
            ch.enableVibration(true);

            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    public static Intent stopAlarmIntent(Context ctx) {
        Intent i = new Intent(ctx, AlarmService.class);
        i.setAction("STOP_ALARM");
        return i;
    }


    @Override
    public IBinder onBind(Intent intent) { return null; }
}
