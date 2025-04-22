/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.getUriFor;

import android.content.Context;
import android.net.Uri;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.view.RotationPolicy;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;

import lineageos.health.HealthInterface;
import lineageos.preference.SettingsHelper;

public class AdvancedRotationPreferenceController extends BasePreferenceController
        implements SettingsHelper.OnSettingsChangeListener, OnPause, OnResume {

    private static final String TAG = "AdvancedRotationPreferenceController";

    private final Uri ACCELEROMETER_ROTATION_URI = getUriFor(ACCELEROMETER_ROTATION);

    private static final String KEY_ADVANCED_ROTATION = "rotation";
    private static final String FEATURE_ROTATION = "android.hardware.sensor.accelerometer";

    private Preference mAdvancedRotation;
    private boolean mAvailable;

    public AdvancedRotationPreferenceController(Context context) {
        super(context, KEY_ADVANCED_ROTATION);
        mAvailable =
                context.getPackageManager().hasSystemFeature(FEATURE_ROTATION)
                && context.getResources().getBoolean(
                        com.android.internal.R.bool.config_enableAdvancedRotationSettings);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mAdvancedRotation = screen.findPreference(KEY_ADVANCED_ROTATION);
    }

    @Override
    public void onResume() {
        SettingsHelper.get(mContext).startWatching(this, ACCELEROMETER_ROTATION_URI);
    }

    @Override
    public void onPause() {
        SettingsHelper.get(mContext).stopWatching(this);
    }

    @Override
    public void onSettingsChanged(Uri uri) {
        updateSummary();
    }

    @Override
    public int getAvailabilityStatus() {
        return mAvailable ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LINEAGE;
    }

    @Override
    public CharSequence getSummary() {
        if (mContext == null) return "";
        if (RotationPolicy.isRotationLocked(mContext)) {
            return mContext.getString(R.string.display_rotation_disabled);
        }
        return mContext.getString(R.string.display_rotation_enabled);
    }

    private void updateSummary() {
        if (mAdvancedRotation != null) {
            mAdvancedRotation.setSummary(getSummary());
        }
    }
}
