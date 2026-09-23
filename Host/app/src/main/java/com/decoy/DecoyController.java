package com.decoy;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;

final class DecoyController {
    static final String PREFS = "decoy";
    static final String KEY_ENABLED = "automatic_tethering";
    static final String KEY_HIDE_BT_ICON = "hide_bluetooth_icon";
    static final String KEY_LAST_STATUS = "last_status";
    static final String KEY_TETHER_DIAGNOSTIC = "tether_diagnostic";
    static final String KEY_HIDE_TETHER_NOTIFICATION = "hide_tether_notification";

    private static final int TETHERING_BLUETOOTH = 2;
    private static final int PROFILE_PAN = 5;
    private static volatile BluetoothProfile bluetoothPanProxy;
    private static volatile boolean panProxyRequested;
    private static final String ICON_BLACKLIST = "icon_blacklist";
    private static final String BLUETOOTH_ICON_SLOT = "bluetooth";

    private DecoyController() {}

    static boolean isAutomationEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    static void setAutomationEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static boolean shouldHideBluetoothIcon(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_HIDE_BT_ICON, false);
    }

    static void setHideBluetoothIconPreference(Context context, boolean hidden) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_HIDE_BT_ICON, hidden).apply();
    }

    static boolean shouldHideTetherNotification(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_HIDE_TETHER_NOTIFICATION, false);
    }

    static void setHideTetherNotification(Context context, boolean hidden) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_HIDE_TETHER_NOTIFICATION, hidden).apply();
    }

    static boolean hasNotificationListenerAccess(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(), "enabled_notification_listeners");
            if (enabled == null) return false;
            String component = new ComponentName(
                    context, TetherNotificationListener.class).flattenToString();
            for (String item : enabled.split(":")) {
                if (component.equals(item)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static void setLastStatus(Context context, String status) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_STATUS, status).apply();
    }

    static String getLastStatus(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_STATUS, "Waiting for first check");
    }

    static void setTetherDiagnostic(Context context, String value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TETHER_DIAGNOSTIC, value).apply();
    }

    static String getTetherDiagnostic(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TETHER_DIAGNOSTIC, "No tethering request result yet");
    }

    static boolean isPanProxyAvailable() {
        return bluetoothPanProxy != null;
    }

    static boolean hasSecureSettingsPermission(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasWriteSettingsAccess(Context context) {
        try {
            return Settings.System.canWrite(context);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isMobileDataEnabled(Context context) {
        // On the target Android 9/XOS phone the raw Settings.Global "mobile_data"
        // value can remain 1 after the user turns mobile data off. Android's
        // TelephonyManager isDataEnabled() reports the actual user data setting
        // for the default data subscription, including dual-SIM devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                TelephonyManager tm =
                        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) return tm.isDataEnabled();
            } catch (Throwable ignored) {
                // Fall through for unusual OEM telephony implementations.
            }
        }

        try {
            return Settings.Global.getInt(
                    context.getContentResolver(), "mobile_data", 0) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    static boolean enableBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return false;
        if (adapter.isEnabled()) return true;
        try {
            return adapter.enable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void ensureBluetoothPanProxy(Context context) {
        if (bluetoothPanProxy != null || panProxyRequested) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;

        panProxyRequested = true;
        try {
            boolean accepted = adapter.getProfileProxy(
                    context.getApplicationContext(),
                    new BluetoothProfile.ServiceListener() {
                        @Override
                        public void onServiceConnected(int profile, BluetoothProfile proxy) {
                            if (profile == PROFILE_PAN) bluetoothPanProxy = proxy;
                            panProxyRequested = false;
                        }

                        @Override
                        public void onServiceDisconnected(int profile) {
                            if (profile == PROFILE_PAN) bluetoothPanProxy = null;
                            panProxyRequested = false;
                        }
                    },
                    PROFILE_PAN);
            if (!accepted) panProxyRequested = false;
        } catch (Throwable ignored) {
            panProxyRequested = false;
        }
    }

    static boolean isBluetoothTetheringActive(Context context) {
        // getTetheredIfaces() tells us whether a PAN interface is currently
        // serving a client. That is not the same thing as the user's Bluetooth
        // tethering toggle: on this XOS build the toggle can be ON while no
        // bt-pan interface exists yet. BluetoothPan.isTetheringOn() is the
        // state we actually need for Decoy's chain.
        ensureBluetoothPanProxy(context);
        BluetoothProfile proxy = bluetoothPanProxy;
        if (proxy != null) {
            try {
                Method m = proxy.getClass().getMethod("isTetheringOn");
                m.setAccessible(true);
                Object value = m.invoke(proxy);
                if (value instanceof Boolean) return (Boolean) value;
            } catch (Throwable ignored) {
            }
        }

        // Fallback retained from the proven probe. This still detects a live
        // bt-pan interface if the PAN profile proxy is not ready yet.
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Method m = ConnectivityManager.class.getDeclaredMethod("getTetheredIfaces");
            m.setAccessible(true);
            Object value = m.invoke(cm);
            if (value == null) return false;

            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (looksLikeBluetoothIface(String.valueOf(item))) return true;
                }
                return false;
            }

            if (value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    if (looksLikeBluetoothIface(
                            String.valueOf(java.lang.reflect.Array.get(value, i)))) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean looksLikeBluetoothIface(String iface) {
        String s = iface == null ? "" : iface.toLowerCase();
        return s.contains("bt-pan") || s.startsWith("bnep") || s.contains("bluetooth");
    }

    static boolean startBluetoothTethering(Context context) {
        // Proven by GitHub Actions build #52 on this Android 9/XOS device:
        // once Bluetooth is fully ON, request Bluetooth tethering through
        // IConnectivityManager.startTethering(TETHERING_BLUETOOTH).
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            Field f = ConnectivityManager.class.getDeclaredField("mService");
            f.setAccessible(true);
            Object svc = f.get(cm);

            Class<?> c = Class.forName("android.net.IConnectivityManager");
            ResultReceiver rr = new ResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override
                protected void onReceiveResult(int code, android.os.Bundle data) {
                    String detail = "startTethering result code=" + code
                            + ", PAN proxy=" + (isPanProxyAvailable() ? "YES" : "NO")
                            + ", tethering detected="
                            + (isBluetoothTetheringActive(context) ? "ON" : "OFF");
                    if (data != null && !data.isEmpty()) detail += ", data=" + data;
                    setTetherDiagnostic(context, detail);
                    setLastStatus(context, detail);
                }
            };

            String signature;
            try {
                Method m = c.getDeclaredMethod(
                        "startTethering", int.class, ResultReceiver.class, boolean.class);
                m.setAccessible(true);
                m.invoke(svc, TETHERING_BLUETOOTH, rr, false);
                signature = "3-arg";
            } catch (NoSuchMethodException e) {
                Method m = c.getDeclaredMethod(
                        "startTethering", int.class, ResultReceiver.class,
                        boolean.class, String.class);
                m.setAccessible(true);
                m.invoke(svc, TETHERING_BLUETOOTH, rr, false, context.getPackageName());
                signature = "4-arg";
            }
            setTetherDiagnostic(context,
                    "startTethering invoked=YES (" + signature + ")"
                            + ", PAN proxy=" + (isPanProxyAvailable() ? "YES" : "NO")
                            + " — waiting for Android result code");
            return true;
        } catch (Throwable t) {
            String detail = "startTethering invoked=NO, error=" + summarize(t)
                    + ", PAN proxy=" + (isPanProxyAvailable() ? "YES" : "NO");
            setTetherDiagnostic(context, detail);
            setLastStatus(context, detail);
            return false;
        }
    }

    static void stopBluetoothTethering(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Method m = ConnectivityManager.class.getDeclaredMethod(
                    "stopTethering", int.class);
            m.setAccessible(true);
            m.invoke(cm, TETHERING_BLUETOOTH);
        } catch (Throwable t) {
            setLastStatus(context, "Could not stop Bluetooth tethering: " + summarize(t));
        }
    }

    static boolean isBluetoothIconHidden(Context context) {
        try {
            String current = Settings.Secure.getString(
                    context.getContentResolver(), ICON_BLACKLIST);
            return containsToken(current, BLUETOOTH_ICON_SLOT);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean setBluetoothIconHidden(Context context, boolean hidden) {
        if (!hasSecureSettingsPermission(context)) return false;

        try {
            String current = Settings.Secure.getString(
                    context.getContentResolver(), ICON_BLACKLIST);
            LinkedHashSet<String> tokens = new LinkedHashSet<>();

            if (current != null && !current.trim().isEmpty()) {
                for (String part : current.split(",")) {
                    String token = part.trim();
                    if (!token.isEmpty()) tokens.add(token);
                }
            }

            if (hidden) tokens.add(BLUETOOTH_ICON_SLOT);
            else tokens.remove(BLUETOOTH_ICON_SLOT);

            StringBuilder out = new StringBuilder();
            for (String token : tokens) {
                if (out.length() > 0) out.append(",");
                out.append(token);
            }

            return Settings.Secure.putString(
                    context.getContentResolver(), ICON_BLACKLIST, out.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsToken(String current, String token) {
        if (current == null || current.trim().isEmpty()) return false;
        for (String part : current.split(",")) {
            if (token.equals(part.trim())) return true;
        }
        return false;
    }

    private static String summarize(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x != x.getCause()) x = x.getCause();
        String message = x.getMessage();
        return x.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
