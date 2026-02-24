/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.os.SystemProperties
import androidx.preference.Preference
import com.android.settings.R
import com.android.settingslib.metadata.PreferenceIconProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.preference.PreferenceBinding

class BuildDatePreference :
    PreferenceMetadata,
    PreferenceSummaryProvider,
    PreferenceIconProvider,
    PreferenceBinding {

    override val key: String
        get() = "os_build_date"

    override val title: Int
        get() = R.string.alpha_build_date

    override fun getIcon(context: Context) = R.drawable.ic_date

    override fun getSummary(context: Context): CharSequence =
        SystemProperties.get("ro.build.date", context.getString(R.string.unknown))

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }
}