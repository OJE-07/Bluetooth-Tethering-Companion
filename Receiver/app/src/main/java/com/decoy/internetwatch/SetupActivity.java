package com.decoy.internetwatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startService(new Intent(this, InternetWatchService.class));
        } catch (Throwable t) {
            android.util.Log.e("Tethering", "Could not start InternetWatchService", t);
        }

        TextView title = new TextView(this);
        title.setText("Tethering");
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(24, 24, 24, 24);
        setContentView(title);
    }
}
