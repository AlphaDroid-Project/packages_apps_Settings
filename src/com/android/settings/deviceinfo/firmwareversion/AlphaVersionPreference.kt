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

class AlphaVersionPreference :
    PreferenceMetadata,
    PreferenceSummaryProvider,
    PreferenceIconProvider,
    PreferenceBinding {

    override val key: String
        get() = "alpha_version"

    override val title: Int
        get() = R.string.alpha_build_version

    override fun getIcon(context: Context) = R.drawable.ic_settings_alpha

    override fun getSummary(context: Context): CharSequence {
        val version = SystemProperties.get("ro.alpha.build.version", "")
        if (version.isEmpty()) return ""
        val variant = SystemProperties.get("ro.alpha.build.variant", "")
        return if (variant.isEmpty()) version else "$version ($variant)"
    }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }
}