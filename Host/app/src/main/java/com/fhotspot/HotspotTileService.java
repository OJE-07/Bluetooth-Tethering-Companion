package com.fhotspot;

import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * F-Hotspot replica baseline.
 *
 * The tile owns its displayed state, but deliberately does NOT stop the
 * underlying Android hotspot when the user visually switches this tile off.
 * Real hotspot/SSID/password control will be added in a later stage.
 */
public class HotspotTileService extends TileService {
    private static final String PREFS = "f_hotspot";
    private static final String KEY_STATE = "state";

    private static final int OFF = 0;
    private static final int TURNING_ON = 1;
    private static final int ON = 2;
    private static final int TURNING_OFF = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        render(loadState());
    }

    @Override
    public void onClick() {
        super.onClick();

        int current = loadState();
        if (current == ON || current == TURNING_ON) {
            // Visual OFF only. Never call stopTethering() here.
            setState(TURNING_OFF);
            handler.postDelayed(() -> setState(OFF), 900);
        } else {
            // Visual ON only. Never call startTethering() here.
            setState(TURNING_ON);
            handler.postDelayed(() -> setState(ON), 1100);
        }
    }

    private void setState(int state) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_STATE, state)
                .apply();
        render(state);
    }

    private int loadState() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_STATE, OFF);
    }

    private void render(int state) {
        Tile tile = getQsTile();
        if (tile == null) return;

        switch (state) {
            case TURNING_ON:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Hotspot Turning on...");
                tile.setContentDescription("Hotspot Turning on");
                break;
            case ON:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Hotspot");
                tile.setContentDescription("Hotspot on");
                break;
            case TURNING_OFF:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Hotspot Turning off...");
                tile.setContentDescription("Hotspot Turning off");
                break;
            default:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel("Hotspot");
                tile.setContentDescription("Hotspot off");
                break;
        }
        tile.updateTile();
    }
}
