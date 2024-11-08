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

public class MaintainerPreferenceController extends AbstractPreferenceController {

    private static final String TAG = "MaintainerPreferenceController";
    private static final String KEY_MAINTAINER = "rom_maintainer";
    private static final String ALPHA_MAINTAINER = "ro.alpha.maintainer";

    public MaintainerPreferenceController(Context context) {
        super(context);
    }

    private String getMaintainer() {
        return SystemProperties.get(ALPHA_MAINTAINER, "");
     }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference mMaintainerPref = screen.findPreference(KEY_MAINTAINER);
        String mMaintainer = getMaintainer();
        if (mMaintainerPref != null && !mMaintainer.isEmpty()) {
            mMaintainerPref.setSummary(mMaintainer);
        }

    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_MAINTAINER;
    }
}
