package com.tgwsproxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        boolean shouldStart = false;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":   // HTC/некоторые производители
            case "com.htc.intent.action.QUICKBOOT_POWERON":
            case Intent.ACTION_MY_PACKAGE_REPLACED:           // автозапуск после обновления APK
                shouldStart = true;
                break;
        }
        if (!shouldStart) return;
        if (!ProxyPrefs.getAutostart(context)) return;

        Log.i(TAG, "Autostart triggered by: " + action);

        Intent svc = new Intent(context, ProxyService.class);
        svc.setAction(ProxyService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service on boot", e);
        }
    }
}
