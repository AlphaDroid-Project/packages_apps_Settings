/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.deviceinfo.firmwareversion;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;


import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.appbar.CollapsingToolbarLayout;

@SearchIndexable
public class FirmwareVersionSettings extends DashboardFragment {

    protected CollapsingToolbarLayout mCollapsingToolbarLayout;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        hideToolbar();
        setDashboardStyle();
    }

    private void hideToolbar() {
        if (mCollapsingToolbarLayout == null) {
            mCollapsingToolbarLayout = getActivity().findViewById(R.id.collapsing_toolbar);
        }
        if (mCollapsingToolbarLayout != null) {
            mCollapsingToolbarLayout.setVisibility(View.GONE);
        }
    }

    public void onResume() {
        super.onResume();
        hideToolbar();
        setDashboardStyle();
    }

    private void setDashboardStyle() {
        int mDashBoardStyle = geSettingstDashboardStyle();
        final PreferenceScreen mScreen = getPreferenceScreen();
        final int mCount = mScreen.getPreferenceCount();

        for (int i = 0; i < mCount; i++) {
            final Preference mPreference = mScreen.getPreference(i);
            if (mPreference == null) continue;

            String mKey = mPreference.getKey();
            if (mKey == null) continue;

            if (mKey.equals("alpha_logo")) {
                mPreference.setLayoutResource(R.layout.alpha_logo);
            } else if (mKey.equals("rom_build_status")) {
                mPreference.setLayoutResource(R.layout.card_build_status);
            } else if (mKey.startsWith("category")) {
                mPreference.setLayoutResource(R.layout.alpha_preference_category);
            } else if (mDashBoardStyle == 1) { // 0=stock aosp, 1=dot, 2=nad
                switch (mKey) {
                    case "lineage_version":
                        mPreference.setLayoutResource(R.layout.dot_dashboard_preference_top);
                        break;
                    case "os_firmware_version":
                    case "about_device_name":
                        mPreference.setLayoutResource(R.layout.dot_top_no_chevron);
                        break;
                    case "security_key":
                        mPreference.setLayoutResource(R.layout.dot_dashboard_preference_middle);
                        break;
                    case "os_build_date":
                    case "base_band":
                        mPreference.setLayoutResource(R.layout.dot_middle_no_chevron);
                        break;
                    case "kernel_version":
                        mPreference.setLayoutResource(R.layout.dot_dashboard_preference_bottom);
                        break;
                    case "selinux_status":
                    case "os_build_number":
                        mPreference.setLayoutResource(R.layout.dot_bottom_no_chevron);
                        break;
                    default:
                        break;
                }
            } else if (mDashBoardStyle == 2) {
                if (mKey.equals("lineage_version") || mKey.equals("security_key") || mKey.equals("kernel_version")) {
                    mPreference.setLayoutResource(R.layout.nad_dashboard_preference_full);
                }
                else {
                    mPreference.setLayoutResource(R.layout.nad_full_no_chevron);
                }
            }
        }
    }

    private int geSettingstDashboardStyle() {
        return Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.SETTINGS_DASHBOARD_STYLE, 2, UserHandle.USER_CURRENT);
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this /* fragment */, getSettingsLifecycle());
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, FirmwareVersionSettings fragment, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new BuildStatusPreferenceController(context));
        controllers.add(new SELinuxStatusPreferenceController(context));
        return controllers;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.firmware_version;
    }

    @Override
    protected String getLogTag() {
        return "FirmwareVersionSettings";
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_FIRMWARE_VERSION;
    }

    /**
     * For Search.
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.firmware_version) {

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null /* fragment */,
                            null /* lifecycle */);
                }
            };
}
