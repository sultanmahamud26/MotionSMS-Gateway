package com.motionsms.gateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GatewayService extends Service {

    private static final String TAG       = "MotionSMSGateway";
    private static final String CHANNEL   = "motionsms_channel";
    private static final int    NOTIF_ID  = 1001;

    public static volatile boolean isRunning = false;
    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            isRunning = false;
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        isRunning = true;
        startForeground(NOTIF_ID, buildNotification("✅ SMS Gateway চলছে..."));
        Log.i(TAG, "GatewayService started");
        return START_STICKY; // System kill করলে auto-restart
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (executor != null) executor.shutdown();
        super.onDestroy();
    }

    // ─────────────────────────────────────────────
    //  SMS টা Telegram Bot এ পাঠাও
    // ─────────────────────────────────────────────
    public static void sendSmsToBot(Context ctx, String sender, String message) {
        SharedPreferences prefs = ctx.getSharedPreferences("motionsms_prefs", Context.MODE_PRIVATE);
        String botToken = prefs.getString("bot_token", "");
        String chatId   = prefs.getString("chat_id", "");

        if (botToken.isEmpty() || chatId.isEmpty()) {
            Log.w(TAG, "Bot token or chat ID not set, skipping SMS forward");
            return;
        }

        // Background thread এ network call করো
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            try {
                // Telegram Bot API format
                String text = "[SMS]\nSender: " + sender + "\nMessage: " + message;
                sendTelegramMessage(botToken, chatId, text);
                Log.i(TAG, "SMS forwarded to bot | Sender: " + sender);
            } catch (Exception e) {
                Log.e(TAG, "Failed to forward SMS: " + e.getMessage());
            }
        });
        exec.shutdown();
    }

    private static void sendTelegramMessage(String token, String chatId, String text) throws Exception {
        String urlStr = "https://api.telegram.org/bot" + token + "/sendMessage";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String params = "chat_id=" + URLEncoder.encode(chatId, "UTF-8")
                      + "&text=" + URLEncoder.encode(text, "UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Log.i(TAG, "✅ Message sent successfully");
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            Log.e(TAG, "❌ Telegram API error " + responseCode + ": " + sb);
        }
        conn.disconnect();
    }

    // ─────────────────────────────────────────────
    //  Notification
    // ─────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL,
                "MotionSMS Gateway",
                NotificationManager.IMPORTANCE_LOW // শব্দ/ভাইব্রেশন ছাড়া
            );
            channel.setDescription("SMS Gateway background service");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("MotionSMS Gateway")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)      // swipe করে সরানো যাবে না
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
}
