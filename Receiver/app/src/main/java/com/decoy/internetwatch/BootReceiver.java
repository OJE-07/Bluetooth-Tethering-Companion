package com.decoy.internetwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            // This S4/XOS build behaves like the successful Phone Master path:
            // targetSdk 25 + ordinary startService, with the service promoting
            // itself to foreground immediately in onCreate().
            context.startService(new Intent(context, InternetWatchService.class));
        } catch (Throwable t) {
            android.util.Log.e("Tethering", "Unable to start InternetWatchService", t);
        }
    }
}
