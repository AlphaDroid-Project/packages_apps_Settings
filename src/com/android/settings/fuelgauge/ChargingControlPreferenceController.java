/*
 * Copyright (C) 2021 The LineageOS Project
 * Copyright (C) 2023-2025 AlphaDroid
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

package com.android.settings.fuelgauge;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import lineageos.health.HealthInterface;

public class ChargingControlPreferenceController extends BasePreferenceController
        implements com.android.settingslib.core.lifecycle.events.OnResume {

    private static final String KEY_CHARGING_CONTROL = "charging_control";
    private static final String TAG = "ChargingControlPreferenceController";

    private HealthInterface mHealthInterface = null;
    private Preference mChargingControl;

    public ChargingControlPreferenceController(Context context) {
        super(context, KEY_CHARGING_CONTROL);
        boolean supported = context.getResources().getBoolean(
                com.android.internal.R.bool.config_supportsChargingControl);
        if (supported) {
            mHealthInterface = HealthInterface.getInstance(context);
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mChargingControl = screen.findPreference(KEY_CHARGING_CONTROL);
    }

    @Override
    public void onResume() {
        updateSummary();
    }

    @Override
    public int getAvailabilityStatus() {
        return mHealthInterface != null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        if (mContext != null && mHealthInterface != null) {
            if (mHealthInterface.getEnabled()) {
                return mContext.getString(R.string.enabled);
            }
            return mContext.getString(R.string.disabled);
        }
        return "";
    }

    private void updateSummary() {
        if (mChargingControl != null) {
            mChargingControl.setSummary(getSummary());
        }
    }
}
