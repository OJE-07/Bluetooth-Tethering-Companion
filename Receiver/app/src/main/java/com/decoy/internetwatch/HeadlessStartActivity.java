package com.decoy.internetwatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class HeadlessStartActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startService(new Intent(this, InternetWatchService.class));
        } catch (Throwable t) {
            android.util.Log.e("Tethering", "Headless boot start failed", t);
        }
        finish();
    }
}
