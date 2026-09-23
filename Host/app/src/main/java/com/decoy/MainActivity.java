package com.decoy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Switch automationSwitch;
    private Switch hideIconSwitch;
    private TextView writeSettingsStatus;
    private TextView mobileDataStatus;
    private TextView bluetoothStatus;
    private TextView tetheringStatus;
    private TextView lastAction;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 1200L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        automationSwitch = findViewById(R.id.automation_switch);
        hideIconSwitch = findViewById(R.id.hide_icon_switch);
        writeSettingsStatus = findViewById(R.id.write_settings_status);
        mobileDataStatus = findViewById(R.id.mobile_data_status);
        bluetoothStatus = findViewById(R.id.bluetooth_status);
        tetheringStatus = findViewById(R.id.tethering_status);
        lastAction = findViewById(R.id.last_action);

        automationSwitch.setChecked(DecoyController.isAutomationEnabled(this));
        hideIconSwitch.setChecked(DecoyController.shouldHideBluetoothIcon(this));

        automationSwitch.setOnCheckedChangeListener((button, checked) -> {
            DecoyController.setAutomationEnabled(this, checked);
            if (checked) {
                startDecoyService();
                DecoyController.setLastStatus(this, "Automation enabled");
            } else {
                stopService(new Intent(this, DecoyService.class));
                DecoyController.setLastStatus(this, "Automation off — current connection state unchanged");
            }
            refreshStatus();
        });

        hideIconSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!DecoyController.hasSecureSettingsPermission(this)) {
                button.setChecked(!checked);
                Toast.makeText(this, "Bluetooth icon hiding needs the one-time ADB grant shown below", Toast.LENGTH_LONG).show();
                return;
            }
            if (DecoyController.setBluetoothIconHidden(this, checked)) {
                DecoyController.setHideBluetoothIconPreference(this, checked);
            } else {
                button.setChecked(!checked);
                Toast.makeText(this, "Could not change Bluetooth icon", Toast.LENGTH_LONG).show();
            }
            refreshStatus();
        });

        ((Button) findViewById(R.id.write_settings_button)).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Throwable t) {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS));
            }
        });

        if (DecoyController.isAutomationEnabled(this)) startDecoyService();
    }

    private void startDecoyService() {
        try {
            startService(new Intent(this, DecoyService.class));
        } catch (Throwable t) {
            DecoyController.setLastStatus(this, "Could not start Decoy service");
        }
    }

    private void refreshStatus() {
        writeSettingsStatus.setText("System settings access: "
                + (DecoyController.hasWriteSettingsAccess(this) ? "GRANTED" : "REQUIRED"));
        mobileDataStatus.setText("Mobile data  " + (DecoyController.isMobileDataEnabled(this) ? "ON" : "OFF"));
        bluetoothStatus.setText("Bluetooth  " + (DecoyController.isBluetoothEnabled() ? "ON" : "OFF"));
        tetheringStatus.setText("Bluetooth tethering  "
                + (DecoyController.isBluetoothTetheringActive(this) ? "ON" : "OFF"));
        lastAction.setText(DecoyController.getLastStatus(this));
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }
}
