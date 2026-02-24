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

class BuildStatusPreference :
    PreferenceMetadata,
    PreferenceSummaryProvider,
    PreferenceIconProvider,
    PreferenceBinding {

    override val key: String
        get() = "rom_build_status"

    override val title: Int
        get() = R.string.build_status_title

    override fun getIcon(context: Context) = R.drawable.ic_build_status

    override fun getSummary(context: Context): CharSequence {
        val releaseType = SystemProperties.get("ro.alpha.release.type", "")
        return if (releaseType.equals("official", ignoreCase = true)) {
            context.getString(R.string.build_status_official)
        } else {
            context.getString(R.string.build_status_unofficial)
        }
    }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }
}