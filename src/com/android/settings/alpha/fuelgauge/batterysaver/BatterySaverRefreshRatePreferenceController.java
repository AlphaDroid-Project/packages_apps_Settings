/*
 * Copyright (C) 2021 WaveOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.alpha.fuelgauge.batterysaver;

import static android.provider.Settings.System.LOW_POWER_REFRESH_RATE;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

/**
 * Gates DisplayModeDirector's low-power 60 Hz vote via
 * {@link Settings.System#LOW_POWER_REFRESH_RATE} ("low_power_rr_switch").
 * Default is off (opt-in); the cap only applies while Battery Saver is also on.
 */
public class BatterySaverRefreshRatePreferenceController extends TogglePreferenceController {

    public BatterySaverRefreshRatePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(mContext.getContentResolver(),
                LOW_POWER_REFRESH_RATE, 0 /*default off: opt-in*/) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.System.putInt(mContext.getContentResolver(),
                LOW_POWER_REFRESH_RATE, isChecked ? 1 : 0);
    }

    @Override
    public int getAvailabilityStatus() {
        // Prefer multi-rate panels over config_show_smooth_display (false on some
        // OnePlus targets that still support 90/120 Hz).
        return hasMultipleRefreshRates() ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_battery;
    }

    private boolean hasMultipleRefreshRates() {
        final Display display = mContext.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return false;
        }
        float max = 0f;
        for (Display.Mode mode : display.getSupportedModes()) {
            max = Math.max(max, mode.getRefreshRate());
        }
        return max > 60.1f;
    }
}
