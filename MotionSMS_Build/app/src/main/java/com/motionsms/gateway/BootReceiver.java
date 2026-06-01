package com.motionsms.gateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
            || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.i("MotionSMS", "Boot detected, starting GatewayService...");

            // Config আছে কিনা check করো
            SharedPreferences prefs = context.getSharedPreferences("motionsms_prefs", Context.MODE_PRIVATE);
            String token  = prefs.getString("bot_token", "");
            String chatId = prefs.getString("chat_id", "");

            if (!token.isEmpty() && !chatId.isEmpty()) {
                Intent serviceIntent = new Intent(context, GatewayService.class);
                serviceIntent.setAction("START");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                Log.i("MotionSMS", "✅ GatewayService started after boot");
            } else {
                Log.w("MotionSMS", "⚠️ Bot token/chat ID not configured, skipping auto-start");
            }
        }
    }
}
