package com.tgwsproxy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * BroadcastReceiver который перезапускает ProxyService.
 * Планируется через AlarmManager когда сервис был убит системой или
 * пользователь смахнул приложение.
 */
public class RestartReceiver extends BroadcastReceiver {

    private static final String TAG         = "RestartReceiver";
    private static final int    REQUEST_CODE = 9001;
    private static final long   DELAY_MS     = 3000; // 3 сек задержка перед перезапуском

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Restarting ProxyService...");
        if (!ProxyPrefs.getAutostart(context)) return;

        Intent svc = new Intent(context, ProxyService.class);
        svc.setAction(ProxyService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart service", e);
        }
    }

    /** Планирует перезапуск сервиса через DELAY_MS миллисекунд. */
    public static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(context, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        long triggerAt = SystemClock.elapsedRealtime() + DELAY_MS;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
            Log.i(TAG, "Restart scheduled in " + DELAY_MS + "ms");
        } catch (Exception e) {
            Log.w(TAG, "Could not schedule exact alarm, using inexact: " + e);
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
        }
    }

    /** Отменяет запланированный перезапуск (при явной остановке через СТОП). */
    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, RestartReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            am.cancel(pi);
            Log.i(TAG, "Restart cancelled");
        }
    }
}
