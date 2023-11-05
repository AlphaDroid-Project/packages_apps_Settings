/*
 * Copyright (C) 2022 VoidUI Project
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

import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settingslib.core.AbstractPreferenceController;

public class BuildStatusPreferenceController extends AbstractPreferenceController {

    private static final String TAG = "BuildStatusPreferenceController";
    private static final String KEY_BUILD_STATUS = "rom_build_status";
    private static final String ALPHA_MAINTAINER = "ro.alpha.maintainer";
    private static final String ALPHA_RELEASE_TYPE = "ro.alpha.release.type";

    public BuildStatusPreferenceController(Context context) {
        super(context);
    }

    private boolean isOfficial() {
        final String releaseType = SystemProperties.get(ALPHA_RELEASE_TYPE, "");
        return releaseType.length() > 0 &&
                "official".equals(releaseType.toLowerCase());
    }

    private String getMaintainer() {
        String mMaintainer = SystemProperties.get(ALPHA_MAINTAINER, "");
        if (mMaintainer.isEmpty()) {
            return mContext.getString(R.string.build_status_oopsie);
        }
        return mMaintainer;
    }

    private String getBuildStatus() {
        if (isOfficial()) {
            return mContext.getString(R.string.build_status_official);
        }
        return "";
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference mBuildStatusPref = screen.findPreference(KEY_BUILD_STATUS);
        if (mBuildStatusPref != null) {
            if (isOfficial()) {
                mBuildStatusPref.setTitle(mContext.getString(R.string.build_status_title, getBuildStatus()));
                mBuildStatusPref.setSummary(mContext.getString(R.string.build_status_summary, getMaintainer()));
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return isOfficial();
    }

    @Override
    public String getPreferenceKey() {
        return KEY_BUILD_STATUS;
    }
}
