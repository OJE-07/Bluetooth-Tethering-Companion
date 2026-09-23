package com.decoy;

import android.app.Service;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Build;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.os.SystemClock;

public class DecoyService extends Service {
    private static final String CHANNEL_ID = "phone_master_service_v2";
    private static final int FOREGROUND_ID = 7106;
    private static final long RECONCILE_INTERVAL_MS = 1000L;
    // Starting tethering is asynchronous. Do not hammer the hidden API every
    // few seconds while Android is still bringing bt-pan up.
    private static final long TETHER_START_RETRY_MS = 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;
    private boolean observerRegistered;
    private long lastTetherStartAttemptAt;

    private final Runnable periodicCheck = new Runnable() {
        @Override
        public void run() {
            reconcile();
            handler.postDelayed(this, RECONCILE_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    DecoyController.ensureBluetoothPanProxy(DecoyService.this);
                    handler.post(DecoyService.this::reconcile);
                } else if (state == BluetoothAdapter.STATE_OFF
                        && DecoyController.isAutomationEnabled(DecoyService.this)
                        && DecoyController.isMobileDataEnabled(DecoyService.this)) {
                    handler.post(DecoyService.this::reconcile);
                }
            } else {
                handler.post(DecoyService.this::reconcile);
            }
        }
    };

    private final ContentObserver mobileDataObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    handler.postDelayed(DecoyService.this::reconcile, 250L);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceNotification();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(stateReceiver, filter);
        receiverRegistered = true;

        try {
            getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor("mobile_data"),
                    false,
                    mobileDataObserver);
            observerRegistered = true;
        } catch (Throwable ignored) {
        }

        DecoyController.ensureBluetoothPanProxy(this);
        handler.removeCallbacks(periodicCheck);
        handler.post(periodicCheck);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!DecoyController.isAutomationEnabled(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundServiceNotification();
        reconcile();
        return START_STICKY;
    }

    private void startForegroundServiceNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Phone Master", NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Notification service");
            channel.enableVibration(false);
            channel.enableLights(false);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("Notification service")
                .setContentText("")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_LOW)
                .build();

        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        startForeground(FOREGROUND_ID, notification);
    }

    private void reconcile() {
        if (!DecoyController.isAutomationEnabled(this)) {
            DecoyController.setLastStatus(this, "Automation is off");
            return;
        }

        boolean mobileData = DecoyController.isMobileDataEnabled(this);

        if (!mobileData) {
            // Data OFF is deliberately passive. Decoy does not change Bluetooth
            // or Bluetooth tethering. This preserves any state the user chose
            // manually, including Bluetooth+tethering already being ON.
            DecoyController.setLastStatus(
                    this, "Mobile data off — Decoy passive; Bluetooth/tethering left unchanged");
            return;
        }

        if (!DecoyController.isBluetoothEnabled()) {
            boolean accepted = DecoyController.enableBluetooth();
            DecoyController.setLastStatus(
                    this,
                    accepted
                            ? "Mobile data on — restoring Bluetooth"
                            : "Mobile data on — Bluetooth could not be enabled");
            // No fixed wait/poll loop. Android broadcasts STATE_ON when the
            // adapter is actually ready; stateReceiver immediately continues
            // the chain from there.
            return;
        }

        if (!DecoyController.isBluetoothTetheringActive(this)) {
            // This also handles the manual-tethering-off edge case: if mobile
            // data is still ON and Bluetooth remains ON, Decoy restores PAN.
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastTetherStartAttemptAt >= TETHER_START_RETRY_MS) {
                lastTetherStartAttemptAt = now;
                boolean accepted = DecoyController.startBluetoothTethering(this);
                DecoyController.setLastStatus(
                        this,
                        accepted
                                ? "Mobile data on — tethering enable requested"
                                : "Mobile data on — tethering enable request failed");
            } else {
                DecoyController.setLastStatus(
                        this, "Mobile data on — waiting for Bluetooth tethering to settle");
            }
        } else {
            lastTetherStartAttemptAt = 0L;
            DecoyController.setLastStatus(
                    this, "Mobile data on — Bluetooth tethering active");
        }

        if (DecoyController.shouldHideBluetoothIcon(this)
                && DecoyController.hasSecureSettingsPermission(this)
                && !DecoyController.isBluetoothIconHidden(this)) {
            DecoyController.setBluetoothIconHidden(this, true);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            try { unregisterReceiver(stateReceiver); } catch (Throwable ignored) {}
        }
        if (observerRegistered) {
            try { getContentResolver().unregisterContentObserver(mobileDataObserver); }
            catch (Throwable ignored) {}
        }
        if (DecoyController.isAutomationEnabled(this)) {
            scheduleRestart();
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    private void scheduleRestart() {
        if (!DecoyController.isAutomationEnabled(this)) return;
        try {
            Intent restart = new Intent(this, DecoyService.class);
            PendingIntent pending = PendingIntent.getService(
                    this, 7105, restart, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm != null) {
                alarm.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + 1000L,
                        pending);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
