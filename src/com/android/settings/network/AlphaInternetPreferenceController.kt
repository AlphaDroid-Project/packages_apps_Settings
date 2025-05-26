/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.network

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.android.settings.R
import com.android.settings.network.InternetPreferenceControllerV2
import com.android.settingslib.Utils
import com.android.settingslib.core.AbstractPreferenceController
import com.android.settingslib.spa.framework.util.collectLatestWithLifecycle

private val KEY = "nad_top_menu_key"

class AlphaInternetPreferenceController(context: Context) :
    AbstractPreferenceController(context) {

    private val repository by lazy { InternetPreferenceRepository(mContext) }
    private var preference: Preference? = null

    override fun isAvailable(): Boolean {
        return true
    }

    override fun displayPreference(screen: PreferenceScreen) {
        super.displayPreference(screen)
        preference = screen.findPreference(getPreferenceKey())
    }

    override fun getPreferenceKey(): String {
        return KEY
    }

    override fun onViewCreated(viewLifecycleOwner: LifecycleOwner) {
        repository.displayInfoFlow().collectLatestWithLifecycle(viewLifecycleOwner) { displayInfo ->
            preference?.apply {
                summary = displayInfo.summary
                icon = mContext.getDrawable(displayInfo.iconResId)?.apply {
                    setTint(Color.WHITE)
                }
            }
        }
    }
}
