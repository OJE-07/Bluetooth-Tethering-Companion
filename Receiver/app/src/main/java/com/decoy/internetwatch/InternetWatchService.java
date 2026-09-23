package com.decoy.internetwatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InternetWatchService extends Service {
    private static final String MONITOR_CHANNEL = "tethering_monitor_v3";
    private static final String ALERT_CHANNEL = "tethering_state_alert_v3";
    private static final int NOTIFICATION_ID = 9201;
    private static final long CHECK_MS = 2000L;

    private static final int STATE_BT_OFF = 0;
    private static final int STATE_BT_ON_NOT_TETHERED = 1;
    private static final int STATE_TETHERED_NO_INTERNET = 2;
    private static final int STATE_TETHERED_INTERNET = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean checkRunning = false;
    private Integer lastState = null;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            checkState();
            handler.postDelayed(this, CHECK_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(
                NOTIFICATION_ID,
                buildNotification(MONITOR_CHANNEL, "Checking connection", false));
        handler.post(loop);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel monitor = new NotificationChannel(
                MONITOR_CHANNEL,
                "Tethering monitor",
                NotificationManager.IMPORTANCE_MIN);
        monitor.setSound(null, null);
        monitor.enableVibration(false);
        monitor.setShowBadge(false);
        nm.createNotificationChannel(monitor);

        NotificationChannel alerts = new NotificationChannel(
                ALERT_CHANNEL,
                "Tethering state changes",
                NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Alerts when Bluetooth, tethering, or internet state changes");
        alerts.setShowBadge(false);
        alerts.enableVibration(true);
        nm.createNotificationChannel(alerts);
    }

    private Notification buildNotification(
            String channelId,
            String stateText,
            boolean intrusive) {

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_wifi)
                .setContentTitle("Monitoring Internet Access")
                .setContentText(stateText)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setOnlyAlertOnce(!intrusive)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(intrusive
                        ? Notification.PRIORITY_MAX
                        : Notification.PRIORITY_MIN);

        if (intrusive) {
            b.setDefaults(Notification.DEFAULT_ALL);
        }

        return b.build();
    }

    private void checkState() {
        if (checkRunning) return;
        checkRunning = true;

        executor.execute(() -> {
            int state = determineState();

            handler.post(() -> {
                checkRunning = false;

                if (lastState == null || state != lastState) {
                    lastState = state;
                    publishState(state, true);
                }
            });
        });
    }

    private int determineState() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            if (adapter == null || !adapter.isEnabled()) {
                return STATE_BT_OFF;
            }

            if (!isBluetoothTetheringConnected()) {
                return STATE_BT_ON_NOT_TETHERED;
            }

            return probeInternet()
                    ? STATE_TETHERED_INTERNET
                    : STATE_TETHERED_NO_INTERNET;
        } catch (Throwable t) {
            android.util.Log.e("Tethering", "State detection failed", t);
            return STATE_BT_ON_NOT_TETHERED;
        }
    }

    private boolean isBluetoothTetheringConnected() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = cm.getActiveNetwork();
                if (active != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                    if (caps != null &&
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // MediaTek/XOS commonly exposes an active Bluetooth PAN link as bnep*.
        try {
            for (NetworkInterface nif :
                    Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif == null || !nif.isUp()) continue;
                String name = nif.getName();
                if (name == null) continue;

                String lower = name.toLowerCase();
                if (lower.startsWith("bnep") ||
                        lower.contains("bt-pan") ||
                        lower.contains("btpan")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private String stateText(int state) {
        switch (state) {
            case STATE_BT_OFF:
                return "Turn on Bluetooth and Connect to Tethering";
            case STATE_BT_ON_NOT_TETHERED:
                return "Connect to Tethering";
            case STATE_TETHERED_NO_INTERNET:
                return "No Internet connection";
            case STATE_TETHERED_INTERNET:
                return "Internet access";
            default:
                return "Checking connection";
        }
    }

    private void publishState(int state, boolean intrusive) {
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification n = buildNotification(
                intrusive ? ALERT_CHANNEL : MONITOR_CHANNEL,
                stateText(state),
                intrusive);

        // Same notification ID every time: only the current state is visible.
        nm.notify(NOTIFICATION_ID, n);
    }

    private boolean probeInternet() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(
                    "http://connectivitycheck.gstatic.com/generate_204")
                    .openConnection();

            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(1800);
            c.setReadTimeout(1800);
            c.setUseCaches(false);
            c.setRequestProperty("Connection", "close");

            return c.getResponseCode() == 204;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
