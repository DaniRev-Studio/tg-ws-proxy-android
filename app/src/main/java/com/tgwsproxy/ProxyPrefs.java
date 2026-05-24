package com.tgwsproxy;

import android.content.Context;
import android.content.SharedPreferences;

public class ProxyPrefs {
    private static final String PREFS_NAME       = "tgproxy";
    private static final String KEY_HOST         = "host";
    private static final String KEY_PORT         = "port";
    private static final String KEY_SECRET       = "secret";
    private static final String KEY_DC_IPS       = "dc_ips";
    private static final String KEY_AUTOSTART    = "autostart";
    private static final String KEY_CF_DOMAIN    = "cf_domain";
    private static final String KEY_CF_WORKER    = "cf_worker_domain";
    private static final String KEY_FALLBACK_CF  = "fallback_cf";

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String  getHost(Context ctx)       { return get(ctx).getString(KEY_HOST, "127.0.0.1"); }
    public static int     getPort(Context ctx)       { return get(ctx).getInt(KEY_PORT, 1443); }
    public static String  getSecret(Context ctx)     { return get(ctx).getString(KEY_SECRET, ""); }
    public static String  getDcIps(Context ctx)      { return get(ctx).getString(KEY_DC_IPS, "2:149.154.167.220\n4:149.154.167.220"); }
    public static boolean getAutostart(Context ctx)  { return get(ctx).getBoolean(KEY_AUTOSTART, false); }
    public static String  getCfDomain(Context ctx)   { return get(ctx).getString(KEY_CF_DOMAIN, ""); }
    public static String  getCfWorker(Context ctx)   { return get(ctx).getString(KEY_CF_WORKER, ""); }
    public static boolean getFallbackCf(Context ctx) { return get(ctx).getBoolean(KEY_FALLBACK_CF, true); }

    public static void saveSecret(Context ctx, String v) { get(ctx).edit().putString(KEY_SECRET, v).commit(); }

    public static void saveAll(Context ctx, String host, int port, String secret,
                               String dcIps, boolean autostart,
                               String cfDomain, String cfWorker, boolean fallbackCf) {
        get(ctx).edit()
                .putString(KEY_HOST,        host)
                .putInt(KEY_PORT,           port)
                .putString(KEY_SECRET,      secret)
                .putString(KEY_DC_IPS,      dcIps)
                .putBoolean(KEY_AUTOSTART,  autostart)
                .putString(KEY_CF_DOMAIN,   cfDomain)
                .putString(KEY_CF_WORKER,   cfWorker)
                .putBoolean(KEY_FALLBACK_CF, fallbackCf)
                .commit();
    }
}
