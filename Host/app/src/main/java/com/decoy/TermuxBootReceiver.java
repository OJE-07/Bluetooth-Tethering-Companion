package com.decoy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TermuxBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.decoy.START_AUTOMATION".equals(intent.getAction())) return;

        // Termux:Boot is only a wake-up fallback. It does not override the
        // user's Automation switch.
        if (!DecoyController.isAutomationEnabled(context)) return;

        try {
            context.startService(new Intent(context, DecoyService.class));
        } catch (Throwable ignored) {
        }
    }
}
