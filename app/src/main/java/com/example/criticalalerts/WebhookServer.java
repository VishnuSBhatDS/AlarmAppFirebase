package com.example.criticalalerts;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import fi.iki.elonen.NanoHTTPD;

public class WebhookServer extends NanoHTTPD {

    private final Context context;

    public WebhookServer(int port, Context context) {
        super("0.0.0.0", port);
        this.context = context;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Log.i("WebhookServer", "Incoming request: " + session.getUri());
            String uri = session.getUri();

            if ("/trigger".equalsIgnoreCase(uri)) {
                Intent alarmIntent = new Intent(context, AlarmService.class);
                alarmIntent.setAction("PLAY_ALARM");
                context.startService(alarmIntent);
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Alarm Triggered!");
            }

            if ("/stop".equalsIgnoreCase(uri)) {
                Intent intent = new Intent(context, AlarmService.class);
                intent.setAction("STOP_ALARM");
                context.startService(intent);
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Alarm Stopped");
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");

        } catch (Exception e) {
            Log.e("WebhookServer", "Error serving request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error");
        }
    }

}
