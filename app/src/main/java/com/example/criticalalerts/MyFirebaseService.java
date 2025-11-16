package com.example.criticalalerts;

import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class MyFirebaseService extends FirebaseMessagingService {

    private void saveTokenToFirestore(String token) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("updated_at", FieldValue.serverTimestamp());
        data.put("device_model", Build.MODEL);
        data.put("platform", "android");

        db.collection("device_tokens")
                .document(token)  // token itself becomes the docId
                .set(data)
                .addOnSuccessListener(a -> Log.i("FCM", "Token saved to Firestore"))
                .addOnFailureListener(e -> Log.e("FCM", "Failed to save token", e));
    }


    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.i("FCM", "Received message: " + remoteMessage.getData());

        String action = remoteMessage.getData().get("action");
        if (action == null) return;

        if (action.equals("PLAY_ALARM")) {
            Intent i = new Intent(this, AlarmService.class);
            i.setAction("PLAY_ALARM");
//            startForegroundService(i);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }

        }

        if (action.equals("STOP_ALARM")) {
            Intent i = new Intent(this, AlarmService.class);
            i.setAction("STOP_ALARM");
            startService(i);
        }
    }

    @Override
    public void onNewToken(String token) {
        Log.i("FCM", "New token: " + token);
        saveTokenToFirestore(token);
        // TODO: send token to your server if needed
    }
}
