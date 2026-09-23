package com.decoy;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class TetherNotificationListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (!DecoyController.shouldHideTetherNotification(this)) return;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return;
            for (StatusBarNotification sbn : active) {
                maybeCancel(sbn);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        maybeCancel(sbn);
    }

    private void maybeCancel(StatusBarNotification sbn) {
        if (sbn == null || !DecoyController.shouldHideTetherNotification(this)) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;

        String title = stringValue(n.extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = stringValue(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        String combined = (title + " " + text).toLowerCase();

        // Match the Android/XOS tethering notification by its user-visible
        // tether/hotspot wording rather than canceling unrelated notifications.
        boolean tethering =
                combined.contains("tethering")
                || combined.contains("tethered")
                || (combined.contains("hotspot") && combined.contains("active"));

        boolean setupText =
                combined.contains("tap to set up")
                || combined.contains("tap to setup")
                || combined.contains("click to setup")
                || combined.contains("click to set up");

        if (tethering || setupText) {
            try {
                cancelNotification(sbn.getKey());
                DecoyController.setLastStatus(
                        this,
                        "Tether notification experiment: dismissal requested");
            } catch (Throwable t) {
                DecoyController.setLastStatus(
                        this,
                        "Tether notification experiment: dismissal blocked");
            }
        }
    }

    private static String stringValue(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
