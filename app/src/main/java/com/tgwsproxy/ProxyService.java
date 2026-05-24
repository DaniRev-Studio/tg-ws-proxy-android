package com.tgwsproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class ProxyService extends Service {

    private static final String TAG         = "ProxyService";
    public  static final String CHANNEL_ID  = "tg_proxy_channel";
    private static final int    NOTIF_ID    = 1;
    public  static final String ACTION_START = "com.tgwsproxy.START";
    public  static final String ACTION_STOP  = "com.tgwsproxy.STOP";

    // Volatile shared state
    public static volatile boolean running    = false;
    public static volatile String  secret     = "";
    public static volatile int     port       = 0;
    public static volatile String  proxyLink  = "";
    public static volatile String  trafficUp  = "0.0B";
    public static volatile String  trafficDown= "0.0B";

    private static final StringBuilder logBuf = new StringBuilder();
    public  static volatile String lastLog = "";

    private Thread proxyThread;
    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public class LocalBinder extends Binder {
        ProxyService getService() { return ProxyService.this; }
    }

    @Override public IBinder onBind(Intent i) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null) ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopProxy();
            return START_NOT_STICKY;
        }

        // ACTION_START или перезапуск системой (intent == null / action == null)
        // Запускаем только если autostart включён или явный старт
        if (ACTION_START.equals(action)) {
            startProxy();
        } else if (intent == null || action == null) {
            // Перезапущен системой после kill — проверяем настройку autostart
            if (ProxyPrefs.getAutostart(this)) {
                startProxy();
            }
        }

        // START_STICKY — система перезапустит сервис после kill с intent=null
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Вызывается когда пользователь смахнул приложение из списка задач.
        // Планируем перезапуск через AlarmManager если autostart включён.
        if (ProxyPrefs.getAutostart(this) && running) {
            RestartReceiver.schedule(this);
        }
        super.onTaskRemoved(rootIntent);
    }

    // ── Start ─────────────────────────────────────────────────────────────────

    private void startProxy() {
        if (running) return;

        synchronized (logBuf) { logBuf.setLength(0); }
        lastLog    = "";
        proxyLink  = "";
        secret     = "";
        port       = 0;
        trafficUp  = "0.0B";
        trafficDown= "0.0B";

        startForeground(NOTIF_ID, buildNotification("TG WS Proxy", "Запускается…"));

        String  host       = ProxyPrefs.getHost(this);
        int     pport      = ProxyPrefs.getPort(this);
        String  sec        = ProxyPrefs.getSecret(this);
        String  dcIps      = ProxyPrefs.getDcIps(this);
        String  cfDomain   = ProxyPrefs.getCfDomain(this);
        String  cfWorker   = ProxyPrefs.getCfWorker(this);
        boolean fallbackCf = ProxyPrefs.getFallbackCf(this);

        proxyThread = new Thread(() -> {
            try {
                Python.getInstance().getModule("proxy_bridge").callAttr("set_service", this);
                Python.getInstance().getModule("proxy_bridge")
                      .callAttr("run_and_report", host, pport, sec, dcIps,
                                cfDomain, cfWorker, fallbackCf);
            } catch (Exception e) {
                Log.e(TAG, "proxy error", e);
                appendLog("ОШИБКА: " + e.getMessage());
            } finally {
                running = false;
                // Перезапуск если autostart включён и сервис упал сам (не через STOP)
                if (ProxyPrefs.getAutostart(getApplicationContext())) {
                    RestartReceiver.schedule(getApplicationContext());
                }
                mainHandler.post(this::stopSelf);
            }
        }, "proxy-thread");
        proxyThread.setDaemon(true);
        proxyThread.start();
    }

    // ── Stop ──────────────────────────────────────────────────────────────────

    public void stopProxy() {
        // Отменяем запланированный перезапуск при явной остановке
        RestartReceiver.cancel(this);

        try {
            Python.getInstance().getModule("proxy_bridge").callAttr("stop_proxy");
        } catch (Exception e) {
            Log.w(TAG, "stop_proxy: " + e);
        }
        if (proxyThread != null) { proxyThread.interrupt(); proxyThread = null; }
        running    = false;
        secret     = "";
        port       = 0;
        proxyLink  = "";
        trafficUp  = "0.0B";
        trafficDown= "0.0B";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
        stopSelf();
    }

    // ── Called from Python (Chaquopy JNI) ────────────────────────────────────

    public static void appendLog(String line) {
        synchronized (logBuf) {
            logBuf.append(line).append('\n');
            if (logBuf.length() > 5500) logBuf.delete(0, logBuf.length() - 5000);
            lastLog = logBuf.toString();
        }
    }

    public static void updateTraffic(String up, String down) {
        trafficUp   = up;
        trafficDown = down;
    }

    public void saveGeneratedSecret(String sec) {
        ProxyPrefs.saveSecret(this, sec);
    }

    public void updateNotifFields(String sec, int p, String link) {
        secret    = sec;
        port      = p;
        proxyLink = link;
        running   = true;
        mainHandler.post(() -> updateNotification(
                "TG WS Proxy  •  :" + p + "  ↑" + trafficUp + "  ↓" + trafficDown,
                link));
    }

    public void refreshTrafficNotification() {
        if (!running) return;
        mainHandler.post(() -> updateNotification(
                "TG WS Proxy  •  :" + port + "  ↑" + trafficUp + "  ↓" + trafficDown,
                proxyLink));
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void updateNotification(String title, String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(title, text));
    }

    private Notification buildNotification(String title, String text) {
        PendingIntent openPi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent tgPi = null;
        if (!proxyLink.isEmpty()) {
            Intent tgI = new Intent(Intent.ACTION_VIEW, Uri.parse(proxyLink));
            tgPi = PendingIntent.getActivity(this, 1, tgI,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Intent stopI = new Intent(this, ProxyService.class);
        stopI.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stopI,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openPi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (tgPi != null) b.addAction(android.R.drawable.ic_menu_send, "Подключить TG", tgPi);
        b.addAction(android.R.drawable.ic_delete, "Стоп", stopPi);

        return b.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "TG WS Proxy", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Прокси-сервис Telegram");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
