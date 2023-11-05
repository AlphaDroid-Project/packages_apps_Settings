/*
 * Copyright (C) 2017 The Android Open Source Project
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
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

import lineageos.trust.TrustInterface;

public class SELinuxStatusPreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin {

    private static final String KEY_SELINUX_STATUS = "selinux_status";
    private TrustInterface mTrustInterface;

    public SELinuxStatusPreferenceController(Context context) {
        super(context);
        mTrustInterface = TrustInterface.getInstance(context);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_SELINUX_STATUS;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        final Preference mSELinuxPref = screen.findPreference(KEY_SELINUX_STATUS);
        if (mSELinuxPref == null) {
            return;
        }

        int seLinuxLevel = mTrustInterface.getLevelForFeature(TrustInterface.TRUST_FEATURE_SELINUX);
        int summary;
        if (seLinuxLevel == TrustInterface.TRUST_FEATURE_LEVEL_GOOD) {
            summary = R.string.selinux_status_enforcing;
        } else {
            summary = R.string.selinux_status_disabled;
        }
        mSELinuxPref.setSummary(mContext.getString(summary));
    }
}

