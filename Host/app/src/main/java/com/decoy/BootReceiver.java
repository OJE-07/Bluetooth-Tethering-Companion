package com.decoy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DecoyController.isAutomationEnabled(context)) return;

        // Start immediately at boot, then once more after Android/XOS has had
        // time to bring telephony and Bluetooth services online.
        start(context);
        final PendingResult pending = goAsync();
        new Handler(context.getMainLooper()).postDelayed(() -> {
            start(context);
            pending.finish();
        }, 5000L);
    }

    private void start(Context context) {
        try {
            context.startService(new Intent(context, DecoyService.class));
        } catch (Throwable ignored) {
        }
    }
}
