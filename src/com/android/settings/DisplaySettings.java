/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.view.SurfaceControl;

import com.android.internal.util.alpha.SystemRestartUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.display.BrightnessLevelPreferenceController;
import com.android.settings.display.CameraGesturePreferenceController;
import com.android.settings.display.DisplayScreen;
import com.android.settings.display.LiftToWakePreferenceController;
import com.android.settings.display.ShowOperatorNamePreferenceController;
import com.android.settings.display.TapToWakePreferenceController;
import com.android.settings.display.EnableBlursPreferenceController;
import com.android.settings.display.ThemePreferenceController;
import com.android.settings.display.VrDisplayPreferenceController;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;

import lineageos.hardware.LineageHardwareManager;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class DisplaySettings extends DashboardFragment {
    private static final String TAG = "DisplaySettings";

    private static final String KEY_BLUR_ALGORITHM = "blur_algorithm";
    private static final String PROP_PERSIST_RENDERENGINE_BLUR_ALGORITHM =
            "persist.sys.renderengine.blur_algorithm";
    private static final String KEY_HIGH_TOUCH_POLLING_RATE = "high_touch_polling_rate_enable";
    private static final String KEY_HIGH_TOUCH_SENSITIVITY = "high_touch_sensitivity_enable";
    private static final String KEY_PROXIMITY_ON_WAKE = "proximity_on_wake";

    @Nullable
    private ListPreference mBlurAlgorithmPref;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.display_settings;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mBlurAlgorithmPref = findPreference(KEY_BLUR_ALGORITHM);
        refreshBlurAlgorithmPreference();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshBlurAlgorithmPreference();
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getSettingsLifecycle());
    }

    @Override
    public int getHelpResource() {
        return R.string.help_uri_display;
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new CameraGesturePreferenceController(context));
        controllers.add(new LiftToWakePreferenceController(context));
        controllers.add(new TapToWakePreferenceController(context));
        controllers.add(new EnableBlursPreferenceController(context));
        controllers.add(new VrDisplayPreferenceController(context));
        controllers.add(new ShowOperatorNamePreferenceController(context));
        controllers.add(new ThemePreferenceController(context));
        controllers.add(new BrightnessLevelPreferenceController(context, lifecycle));
        return controllers;
    }

    /**
     * Show blur algorithm list only when native has written {@code persist.sys.renderengine
     * .blur_algorithm}. If empty or unknown, hide (native seeds default when both props unset).
     */
    private void refreshBlurAlgorithmPreference() {
        if (mBlurAlgorithmPref == null) {
            return;
        }

        final SurfaceControl.SupportedBlurAlgorithms algorithms =
                SurfaceControl.getSupportedBlurAlgorithms();
        if (algorithms == null || algorithms.propertyTokens.length == 0) {
            mBlurAlgorithmPref.setVisible(false);
            return;
        }

        final String persisted =
                SystemProperties.get(PROP_PERSIST_RENDERENGINE_BLUR_ALGORITHM, "");
        if (TextUtils.isEmpty(persisted)) {
            mBlurAlgorithmPref.setVisible(false);
            return;
        }

        String selected = null;
        for (String token : algorithms.propertyTokens) {
            if (TextUtils.equals(token, persisted)) {
                selected = persisted;
                break;
            }
        }
        if (selected == null) {
            mBlurAlgorithmPref.setVisible(false);
            return;
        }

        mBlurAlgorithmPref.setEntries(algorithms.enumLabels);
        mBlurAlgorithmPref.setEntryValues(algorithms.propertyTokens);
        mBlurAlgorithmPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mBlurAlgorithmPref.setValue(selected);
        mBlurAlgorithmPref.setVisible(true);
        mBlurAlgorithmPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SystemProperties.set(
                    PROP_PERSIST_RENDERENGINE_BLUR_ALGORITHM, String.valueOf(newValue));
            SystemRestartUtils.showSystemRestartDialog(requireContext());
            return true;
        });
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.display_settings) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    LineageHardwareManager hardware = LineageHardwareManager.getInstance(context);
                    if (!hardware.isSupported(
                            LineageHardwareManager.FEATURE_HIGH_TOUCH_POLLING_RATE)) {
                        keys.add(KEY_HIGH_TOUCH_POLLING_RATE);
                    }
                    if (!hardware.isSupported(
                            LineageHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY)) {
                        keys.add(KEY_HIGH_TOUCH_SENSITIVITY);
                    }
                    if (!context.getResources().getBoolean(
                            com.android.internal.R.bool.config_proximityCheckOnWake)) {
                        keys.add(KEY_PROXIMITY_ON_WAKE);
                    }
                    final SurfaceControl.SupportedBlurAlgorithms blurAlgorithms =
                            SurfaceControl.getSupportedBlurAlgorithms();
                    if (blurAlgorithms == null
                            || blurAlgorithms.propertyTokens.length == 0
                            || TextUtils.isEmpty(
                                    SystemProperties.get(
                                            PROP_PERSIST_RENDERENGINE_BLUR_ALGORITHM, ""))) {
                        keys.add(KEY_BLUR_ALGORITHM);
                    }
                    return keys;
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null);
                }
            };

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return DisplayScreen.KEY;
    }
}
