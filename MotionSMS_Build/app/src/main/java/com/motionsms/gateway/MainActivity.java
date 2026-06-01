package com.motionsms.gateway;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 100;
    private EditText etBotToken, etChatId;
    private TextView tvStatus, tvLog;
    private Button btnSave, btnStart, btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etBotToken = findViewById(R.id.etBotToken);
        etChatId   = findViewById(R.id.etChatId);
        tvStatus   = findViewById(R.id.tvStatus);
        tvLog      = findViewById(R.id.tvLog);
        btnSave    = findViewById(R.id.btnSave);
        btnStart   = findViewById(R.id.btnStart);
        btnStop    = findViewById(R.id.btnStop);

        // Saved config load করো
        SharedPreferences prefs = getPrefs();
        etBotToken.setText(prefs.getString("bot_token", ""));
        etChatId.setText(prefs.getString("chat_id", ""));

        btnSave.setOnClickListener(v -> saveConfig());
        btnStart.setOnClickListener(v -> startService());
        btnStop.setOnClickListener(v -> stopService());

        // Permissions check
        checkPermissions();

        // Battery optimization bypass
        requestBatteryOptimizationExempt();

        // Service চলছে কিনা দেখাও
        updateStatus();
    }

    private void saveConfig() {
        String token  = etBotToken.getText().toString().trim();
        String chatId = etChatId.getText().toString().trim();

        if (token.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "Bot Token ও Chat ID দিন!", Toast.LENGTH_SHORT).show();
            return;
        }

        getPrefs().edit()
            .putString("bot_token", token)
            .putString("chat_id", chatId)
            .apply();

        Toast.makeText(this, "✅ Config saved!", Toast.LENGTH_SHORT).show();
    }

    private void startService() {
        String token  = getPrefs().getString("bot_token", "");
        String chatId = getPrefs().getString("chat_id", "");

        if (token.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "আগে Config save করুন!", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, GatewayService.class);
        intent.setAction("START");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateStatus();
    }

    private void stopService() {
        Intent intent = new Intent(this, GatewayService.class);
        intent.setAction("STOP");
        startService(intent);
        updateStatus();
    }

    private void updateStatus() {
        boolean running = GatewayService.isRunning;
        tvStatus.setText(running ? "🟢 Service চলছে" : "🔴 Service বন্ধ");
        tvStatus.setTextColor(running
            ? getResources().getColor(android.R.color.holo_green_dark)
            : getResources().getColor(android.R.color.holo_red_dark));
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("motionsms_prefs", MODE_PRIVATE);
    }

    private void checkPermissions() {
        String[] perms = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        };

        boolean allGranted = true;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, perms, PERM_REQUEST);
        }

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_REQUEST + 1);
            }
        }
    }

    private void requestBatteryOptimizationExempt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQUEST) {
            boolean allOk = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { allOk = false; break; }
            }
            if (!allOk) {
                Toast.makeText(this, "⚠️ SMS permission দিন, নাহলে কাজ করবে না!", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
