package com.tgwsproxy;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private Button   btnStart, btnStop, btnConnectTg, btnCopyLink, btnSettings;
    private TextView statusText, hostText, portText, secretText, linkText, logText;
    private TextView autostartBadge, trafficUp, trafficDown;
    private View     statusDot, linkCard, trafficRow;
    private ScrollView logScroll;

    private ProxyService boundService;
    private boolean      serviceBound = false;

    private final Handler  uiHandler       = new Handler(Looper.getMainLooper());
    private       Runnable pollRunnable;
    private       String   lastLogSnapshot = "";

    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> showSavedSettings());

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            boundService = ((ProxyService.LocalBinder) b).getService();
            serviceBound = true;
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            boundService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart      = findViewById(R.id.btnStart);
        btnStop       = findViewById(R.id.btnStop);
        btnConnectTg  = findViewById(R.id.btnConnectTg);
        btnCopyLink   = findViewById(R.id.btnCopyLink);
        btnSettings   = findViewById(R.id.btnSettings);
        statusText    = findViewById(R.id.statusText);
        hostText      = findViewById(R.id.hostText);
        portText      = findViewById(R.id.portText);
        secretText    = findViewById(R.id.secretText);
        linkText      = findViewById(R.id.linkText);
        logText       = findViewById(R.id.logText);
        statusDot     = findViewById(R.id.statusDot);
        linkCard      = findViewById(R.id.linkCard);
        logScroll     = findViewById(R.id.logScroll);
        autostartBadge = findViewById(R.id.autostartBadge);
        trafficUp     = findViewById(R.id.trafficUp);
        trafficDown   = findViewById(R.id.trafficDown);
        trafficRow    = findViewById(R.id.trafficRow);

        btnStart.setOnClickListener(v     -> startProxy());
        btnStop.setOnClickListener(v      -> stopProxy());
        btnConnectTg.setOnClickListener(v -> openInTelegram());
        btnCopyLink.setOnClickListener(v  -> copyLink());
        btnSettings.setOnClickListener(v  ->
                settingsLauncher.launch(new Intent(this, SettingsActivity.class)));

        requestNotificationPermission();
        tryBindService();

        pollRunnable = () -> {
            refreshUI();
            uiHandler.postDelayed(pollRunnable, 800);
        };
        uiHandler.post(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(pollRunnable);
        safeUnbind();
    }

    // ── Proxy control ─────────────────────────────────────────────────────────

    private void startProxy() {
        if (ProxyService.running) return;
        btnStart.setEnabled(false);
        Intent i = new Intent(this, ProxyService.class);
        i.setAction(ProxyService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        tryBindService();
    }

    private void stopProxy() {
        if (!ProxyService.running) return;
        if (serviceBound && boundService != null) {
            boundService.stopProxy();
        } else {
            Intent i = new Intent(this, ProxyService.class);
            i.setAction(ProxyService.ACTION_STOP);
            startService(i);
        }
    }

    // ── UI refresh ────────────────────────────────────────────────────────────

    private void refreshUI() {
        boolean running = ProxyService.running;

        if (running) {
            setDotColor("#4caf50");
            statusText.setText("Работает ✓");

            btnStart.setEnabled(false);
            btnStart.setBackgroundTintList(colorList("#37474f"));
            btnStart.setTextColor(Color.parseColor("#607d8b"));

            btnStop.setEnabled(true);
            btnStop.setBackgroundTintList(colorList("#f44336"));
            btnStop.setTextColor(Color.WHITE);

            btnSettings.setEnabled(false);
            btnSettings.setAlpha(0.35f);

            hostText.setText(ProxyPrefs.getHost(this));
            portText.setText(String.valueOf(ProxyService.port));
            secretText.setText(ProxyService.secret);

            // Traffic
            trafficRow.setVisibility(View.VISIBLE);
            trafficUp.setText("↑ " + ProxyService.trafficUp);
            trafficDown.setText("↓ " + ProxyService.trafficDown);

            String link = ProxyService.proxyLink;
            if (!link.isEmpty()) {
                linkText.setText(link);
                linkCard.setVisibility(View.VISIBLE);
            }

        } else {
            setDotColor("#f44336");
            statusText.setText("Остановлен");

            btnStart.setEnabled(true);
            btnStart.setBackgroundTintList(colorList("#2196F3"));
            btnStart.setTextColor(Color.WHITE);

            btnStop.setEnabled(false);
            btnStop.setBackgroundTintList(colorList("#37474f"));
            btnStop.setTextColor(Color.parseColor("#90a4ae"));

            btnSettings.setEnabled(true);
            btnSettings.setAlpha(1f);

            trafficRow.setVisibility(View.GONE);
            linkCard.setVisibility(View.GONE);
            showSavedSettings();
        }

        String log = ProxyService.lastLog;
        if (!log.equals(lastLogSnapshot)) {
            lastLogSnapshot = log;
            logText.setText(log);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void showSavedSettings() {
        hostText.setText(ProxyPrefs.getHost(this));
        portText.setText(String.valueOf(ProxyPrefs.getPort(this)));
        String s = ProxyPrefs.getSecret(this);
        secretText.setText(s.isEmpty() ? "(авто)" : s);
        if (autostartBadge != null) {
            autostartBadge.setVisibility(
                    ProxyPrefs.getAutostart(this) ? View.VISIBLE : View.GONE);
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void openInTelegram() {
        String link = ProxyService.proxyLink;
        if (link.isEmpty()) {
            Toast.makeText(this, "Прокси ещё не запущен", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        } catch (Exception e) {
            Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLink() {
        String link = ProxyService.proxyLink;
        if (link.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("proxy_link", link));
            Toast.makeText(this, "Скопировано!", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void tryBindService() {
        if (!serviceBound) {
            bindService(new Intent(this, ProxyService.class),
                        serviceConn, Context.BIND_AUTO_CREATE);
        }
    }

    private void safeUnbind() {
        if (serviceBound) {
            try { unbindService(serviceConn); } catch (Exception ignored) {}
            serviceBound = false;
        }
    }

    private void setDotColor(String hex) {
        statusDot.setBackgroundTintList(colorList(hex));
    }

    private android.content.res.ColorStateList colorList(String hex) {
        return android.content.res.ColorStateList.valueOf(Color.parseColor(hex));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }
}
