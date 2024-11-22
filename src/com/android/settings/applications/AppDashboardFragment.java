/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settings.applications;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.SearchIndexableResource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settings.R;
import com.android.settings.applications.appcompat.UserAspectRatioAppsPreferenceController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.PreferenceCategoryController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.internal.util.alpha.SystemRestartUtils;

/** Settings page for apps. */
// LINT.IfChange
@SearchIndexable
public class AppDashboardFragment extends DashboardFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AppDashboardFragment";
    private static final String ADVANCED_CATEGORY_KEY = "advanced_category";
    private static final String ASPECT_RATIO_PREF_KEY = "aspect_ratio_apps";
    private AppsPreferenceController mAppsPreferenceController;

    private static final String QUICK_SWITCH_KEY = "persist.sys.default_launcher";

    private ListPreference quickSwitchListPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int defaultLauncher = SystemProperties.getInt(QUICK_SWITCH_KEY, 0);
        quickSwitchListPref = findPreference(QUICK_SWITCH_KEY);
        if (quickSwitchListPref != null) {
            quickSwitchListPref.setOnPreferenceChangeListener(this);
            List<String> launcherEntries = new ArrayList<>();
            List<String> launcherValues = new ArrayList<>();
            // 0-Launcher3, 1-NexusLauncher, 2-Lawnchair
            launcherEntries.add(getContext().getResources().getString(R.string.quickswitch_launcher3));
            launcherValues.add("0");
            if (AppUtils.isNexusLauncherAvailable()) {
                launcherEntries.add(getContext().getResources().getString(R.string.quickswitch_nexuslauncher));
                launcherValues.add("1");
            }
            if (AppUtils.isLawnchairAvailable()) {
                launcherEntries.add(getContext().getResources().getString(R.string.quickswitch_lawnchair));
                launcherValues.add("2");
            }
            quickSwitchListPref.setEntries(launcherEntries.toArray(new CharSequence[launcherEntries.size()]));
            quickSwitchListPref.setEntryValues(launcherValues.toArray(new CharSequence[launcherValues.size()]));
            if (defaultLauncher >= 0 && defaultLauncher < launcherValues.size()) {
                quickSwitchListPref.setValue(String.valueOf(defaultLauncher));
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == quickSwitchListPref) {
            SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        return false;
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new AppsPreferenceController(context));

        final UserAspectRatioAppsPreferenceController aspectRatioAppsPreferenceController =
                new UserAspectRatioAppsPreferenceController(context, ASPECT_RATIO_PREF_KEY);
        final AdvancedAppsPreferenceCategoryController advancedCategoryController =
                new AdvancedAppsPreferenceCategoryController(context, ADVANCED_CATEGORY_KEY);
        advancedCategoryController.setChildren(List.of(aspectRatioAppsPreferenceController));
        controllers.add(advancedCategoryController);
        return controllers;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MANAGE_APPLICATIONS;
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return AppDashboardScreen.KEY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_apps_and_notifications;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.apps;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mAppsPreferenceController = use(AppsPreferenceController.class);
        mAppsPreferenceController.setFragment(this /* fragment */);
        getSettingsLifecycle().addObserver(mAppsPreferenceController);

        final HibernatedAppsPreferenceController hibernatedAppsPreferenceController =
                use(HibernatedAppsPreferenceController.class);
        getSettingsLifecycle().addObserver(hibernatedAppsPreferenceController);
    }

    @VisibleForTesting
    PreferenceCategoryController getAdvancedAppsPreferenceCategoryController() {
        return use(AdvancedAppsPreferenceCategoryController.class);
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.apps;
                    return Arrays.asList(sir);
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context);
                }
            };
}
// LINT.ThenChange(AppDashboardScreen.kt)
