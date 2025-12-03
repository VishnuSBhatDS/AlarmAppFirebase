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
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class AlarmService extends Service {

    private static final String CHANNEL_ID = "alarm_channel";
    private static final int NOTIF_ID = 5001;
    private final Handler ttsHandler = new Handler(Looper.getMainLooper());
    private boolean isAlarmRunning = false;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private TextToSpeech tts;

    private boolean ttsReady = false;
    private boolean ttsLoop = false;

    private String lastMessage = null;
    private String pendingMessage = null;   // 🔥 fixes TTS race condition

    @Override
    public void onCreate() {
        super.onCreate();

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        createChannel();
        initTTS();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) return START_STICKY;

        String action = intent.getAction();

        if ("PLAY_ALARM".equals(action)) {

            // --- Prevent duplicate alarms ---
            if (isAlarmRunning) {
                Log.w("ALARM", "Alarm already running → ignoring PLAY_ALARM");

                // Optional: refresh TTS message
                String msg = intent.getStringExtra("message");
                if (msg != null) speakMessage(msg);

                return START_STICKY;
            }

            // First time alarm is ringing
            isAlarmRunning = true;

            playAlarmSafe();

            String msg = intent.getStringExtra("message");
            if (msg != null) speakMessage(msg);

        }

        else if ("STOP_ALARM".equals(action)) {

            Log.i("ALARM", "STOP_ALARM received");
            stopAlarm();

        }

        return START_STICKY;
    }

    // ---------------------------------------------------------------
    // ALARM CONTROL
    // ---------------------------------------------------------------

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
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setVolume(1.0f, 1.0f);

        } catch (Exception e) {
            Log.e("ALARM", "MediaPlayer failed: " + e);
        }
    }

    private void startVibration() {
        long[] pattern = {0, 500, 500};
        vibrator.vibrate(pattern, 0);
    }

    private void forceMaxVolume() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
    }

    // ---------------------------------------------------------------
    // TTS INITIALIZATION + VOICE SELECTION + PENDING QUEUE FIX
    // ---------------------------------------------------------------

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {

                ttsReady = true;

                try {
                    tts.setLanguage(Locale.forLanguageTag("en-IN"));
                } catch (Exception ignored) {}

                setTTSVoice("en-in-x-enm-network");

                Log.i("TTS", "TTS Ready");

                // 🔥 IMPORTANT FIX:
                // If PLAY_ALARM already arrived before TTS init finished,
                // speak the queued message now.
                if (pendingMessage != null) {
                    Log.i("TTS", "Speaking PENDING message: " + pendingMessage);
                    speakMessage(pendingMessage);
                    pendingMessage = null;
                }

            } else {
                Log.e("TTS", "Init failed");
            }
        });
    }

    private void setTTSVoice(String voiceName) {
        if (!ttsReady || tts == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (Voice v : tts.getVoices()) {
                    if (v.getName().equalsIgnoreCase(voiceName)) {
                        tts.setVoice(v);
                        Log.i("TTS", "Voice set to: " + v.getName());
                        return;
                    }
                }
            }

            Log.w("TTS", "Voice not found: " + voiceName);

        } catch (Exception e) {
            Log.e("TTS", "Voice selection error: " + e);
        }
    }

//    private void setTTSVoice(String gender, String localeCode) {
//        if (!ttsReady || tts == null) return;
//
//        try {
//            Locale loc = Locale.forLanguageTag(localeCode);
//            tts.setLanguage(loc);
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                for (Voice v : tts.getVoices()) {
//                    String name = v.getName().toLowerCase();
//
//                    boolean localeMatches =
//                            name.contains(localeCode.replace("-", "_").toLowerCase()) ||
//                                    (v.getLocale() != null &&
//                                            v.getLocale().toLanguageTag().toLowerCase().contains(localeCode.toLowerCase()));
//
//                    boolean genderMatches =
//                            (gender.equals("male") && name.contains("male")) ||
//                                    (gender.equals("female") && name.contains("female"));
//
//                    if (localeMatches && genderMatches) {
//                        tts.setVoice(v);
//                        Log.i("TTS", "Using voice: " + v.getName());
//                        return;
//                    }
//                }
//            }
//
//            Log.w("TTS", "No matching voice for " + gender + " / " + localeCode);
//
//        } catch (Exception e) {
//            Log.e("TTS", "setTTSVoice error: " + e);
//        }
//    }

    // ---------------------------------------------------------------
    // SPEAK MESSAGE + LOOP (WITH TTS INIT QUEUE)
    // ---------------------------------------------------------------

    private void speakMessage(String text) {

        // 🔥 Fix: if TTS init not finished, queue message and return.
        if (!ttsReady) {
            Log.e("TTS", "Not ready yet. Queued message: " + text);
            pendingMessage = text;
            return;
        }

        lastMessage = text;
        ttsLoop = true;

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.requestAudioFocus(fc -> {}, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        if (mediaPlayer != null) {
            mediaPlayer.setVolume(0.4f, 0.4f);
        }

        try {
            tts.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
            );
        } catch (Exception ignored) {}

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override public void onStart(String id) {}

            @Override
            public void onDone(String id) {

                if (!ttsLoop) return;

                if (mediaPlayer != null)
                    mediaPlayer.setVolume(0.4f, 0.4f);

                // Delay before repeating TTS (e.g., 2 seconds)
                ttsHandler.postDelayed(() -> {

                    if (!ttsLoop) return;

                    Bundle b = new Bundle();
                    b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);

                    Log.i("TTS", "Looping after delay…");

                    tts.speak(lastMessage, TextToSpeech.QUEUE_FLUSH, b, "ALARM_TTS");

                }, 2000); // 2000 ms = 2 second delay
            }


            @Override public void onError(String id) {
                Log.e("TTS", "Error in TTS loop");
            }
        });

        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);

        Log.i("TTS", "Speaking: " + text);

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, b, "ALARM_TTS");
    }

    // ---------------------------------------------------------------
    // STOP ALARM
    // ---------------------------------------------------------------

    private void stopAlarm() {

        ttsLoop = false;
        pendingMessage = null;

        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
            ttsReady = false;
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

    // ---------------------------------------------------------------
    // CHANNEL
    // ---------------------------------------------------------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Alarm Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
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
