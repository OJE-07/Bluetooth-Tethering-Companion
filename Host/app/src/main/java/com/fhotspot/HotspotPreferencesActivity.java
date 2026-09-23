package com.fhotspot;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/**
 * Destination for the Quick Settings tile long-press action.
 * Android routes ACTION_QS_TILE_PREFERENCES here; we immediately hand the
 * user to XOS's real Hotspot & Tethering page.
 */
public class HotspotPreferencesActivity extends Activity {
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SETTINGS_CLASS =
            "com.android.settings.Settings$TetherSettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openHotspotAndTethering();
        finish();
    }

    private void openHotspotAndTethering() {
        try {
            Intent explicit = new Intent();
            explicit.setComponent(new ComponentName(SETTINGS_PACKAGE, SETTINGS_CLASS));
            startActivity(explicit);
            return;
        } catch (Throwable ignored) {
            // Fall back to Android's standard tethering Settings action.
        }

        try {
            startActivity(new Intent("android.settings.TETHER_SETTINGS"));
        } catch (Throwable ignored) {
            // Nothing else to display; the activity simply closes.
        }
    }
}
