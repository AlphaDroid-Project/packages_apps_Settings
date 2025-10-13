/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settings.display;

import static android.os.UserManager.DISALLOW_SET_WALLPAPER;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.alpha.Utils;

import com.android.settings.R;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedTopLevelPreference;

import java.util.List;

/** This controller manages the wallpaper preference of the top level page. */
public class TopLevelWallpaperPreferenceController extends BasePreferenceController {
    private static final String TAG = "TopLevelWallpaperPreferenceController";
    private static final String LAUNCHED_SETTINGS = "app_launched_settings";

    /* 0 - Launcher3, 1 - Nexus Launcher, 2 - Lawnchair */
    private static final int NEXUS_LAUNCHER = 1;
    private static final String DEFAULT_LAUNCHER_PROP = "persist.sys.default_launcher";
    private static final String NEXUS_LAUNCHER_PKG_NAME = "com.google.android.apps.nexuslauncher";
    private static final String LAWNCHAIR_PKG_NAME = "app.lawnchair";

    private final String mWallpaperPackage;
    private final String mWallpaperClass;
    private final String mWallpaperAction;
    private final String mGoogleWallpaperPackage;
    private final String mWallpaperLaunchExtra;

    private int getDefaultLauncher() {
        return SystemProperties.getInt(DEFAULT_LAUNCHER_PROP, 0);
    }

    private boolean isPackageAvailable(Context context, String pkgName) {
        return !TextUtils.isEmpty(pkgName) && Utils.isPackageInstalled(context, pkgName);
    }

    public TopLevelWallpaperPreferenceController(Context context, String key) {
        super(context, key);
        mGoogleWallpaperPackage = mContext.getString(R.string.config_google_wallpaper_picker_package);
        if (getDefaultLauncher() == NEXUS_LAUNCHER && isPackageAvailable(context, mGoogleWallpaperPackage)) {
            mWallpaperPackage = mGoogleWallpaperPackage;
            mWallpaperClass = mContext.getString(R.string.config_google_wallpaper_picker_class);
            mWallpaperAction = mContext.getString(R.string.config_google_wallpaper_picker_action);
        }
        else {
            mWallpaperPackage = mContext.getString(R.string.config_wallpaper_picker_package);
            mWallpaperClass = mContext.getString(R.string.config_wallpaper_picker_class);
            mWallpaperAction = mContext.getString(R.string.config_wallpaper_picker_action);
        }
        mWallpaperLaunchExtra = mContext.getString(R.string.config_wallpaper_picker_launch_extra);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        Preference preference = screen.findPreference(getPreferenceKey());
        preference.setTitle(getTitle());
        ActivityEmbeddingRulesController.registerTwoPanePairRuleForSettingsHome(
                mContext,
                getComponentName(),
                null /* secondaryIntentAction */,
                true /* clearTop */);
    }

    public String getTitle() {
        return mContext.getString(R.string.style_and_wallpaper_settings_title);
    }

    public ComponentName getComponentName() {
        return new ComponentName(mWallpaperPackage, mWallpaperClass);
    }

    public String getComponentClassString() {
        return mWallpaperClass;
    }

    public String getComponentActionName() {
        return mWallpaperAction;
    }

    public String getKeywords() {
        StringBuilder sb = new StringBuilder(mContext.getString(R.string.keywords_wallpaper));
        if (areStylesAvailable()) {
            sb.append(", ").append(mContext.getString(R.string.keywords_styles));
        }
        return sb.toString();
    }

    @Override
    public int getAvailabilityStatus() {
        if (TextUtils.isEmpty(mWallpaperClass) || TextUtils.isEmpty(mWallpaperPackage)) {
            Log.e(TAG, "No Wallpaper picker specified!");
            return UNSUPPORTED_ON_DEVICE;
        }
        return AVAILABLE_UNSEARCHABLE;
    }

    @Override
    public void updateState(Preference preference) {
        disablePreferenceIfManaged((RestrictedTopLevelPreference) preference);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (getPreferenceKey().equals(preference.getKey())) {
            final Intent intent = new Intent().setComponent(getComponentName());
            if (!ActivityEmbeddingUtils.isEmbeddingActivityEnabled(mContext)) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
            preference.getContext().startActivity(intent);
            return true;
        }
        return super.handlePreferenceTreeClick(preference);
    }

    public boolean areStylesAvailable() {
        return true;
    }

    private void disablePreferenceIfManaged(RestrictedTopLevelPreference pref) {
        final String restriction = DISALLOW_SET_WALLPAPER;
        if (pref != null) {
            pref.setDisabledByAdmin(null);
            if (RestrictedLockUtilsInternal.hasBaseUserRestriction(mContext,
                    restriction, UserHandle.myUserId())) {
                // Do not show the admin dialog for system restriction.
                pref.setEnabled(false);
            } else {
                pref.checkRestrictionAndSetDisabled(restriction);
            }
        }
    }
}
