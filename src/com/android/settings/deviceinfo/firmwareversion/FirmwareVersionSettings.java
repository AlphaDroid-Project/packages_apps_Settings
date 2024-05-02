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
import android.view.View;

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
    }


    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this /* fragment */, getSettingsLifecycle());
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, FirmwareVersionSettings fragment, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new BuildStatusPreferenceController(context));
        controllers.add(new MaintainerPreferenceController(context));
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
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.firmware_version);
}
