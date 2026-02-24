/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.os.SystemProperties
import androidx.preference.Preference
import com.android.settings.R
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceIconProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.preference.PreferenceBinding

class MaintainerPreference :
    PreferenceMetadata,
    PreferenceSummaryProvider,
    PreferenceAvailabilityProvider,
    PreferenceIconProvider,
    PreferenceBinding {

    override val key: String
        get() = "rom_maintainer"

    override val title: Int
        get() = R.string.maintainer_title

    override fun getIcon(context: Context) = R.drawable.ic_maintainer

    override fun getSummary(context: Context): CharSequence =
        SystemProperties.get("ro.alpha.maintainer", context.getString(R.string.unknown))

    override fun isAvailable(context: Context): Boolean =
        SystemProperties.get("ro.alpha.maintainer", "").isNotEmpty()

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }
}