/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.settings.widget;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioGroup;

import com.android.settings.R;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class DisplayEnginePreference extends Preference {

    private int currentMode;

    public DisplayEnginePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_display_engine);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View view = holder.itemView;
        RadioGroup radioGroup = view.findViewById(R.id.radio_group);
        currentMode = Settings.Secure.getInt(
                getContext().getContentResolver(),
                "display_engine_mode",
                0
        );
        if (currentMode == 0) {
            radioGroup.check(R.id.radio_default);
        } else if (currentMode == 1) {
            radioGroup.check(R.id.radio_x_reality);
        } else if (currentMode == 2) {
            radioGroup.check(R.id.radio_vivid);
        } else if (currentMode == 3) {
            radioGroup.check(R.id.radio_triluminous);
        }
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int newMode = 0;
            if (checkedId == R.id.radio_x_reality) {
                newMode = 1;
            } else if (checkedId == R.id.radio_vivid) {
                newMode = 2;
            } else if (checkedId == R.id.radio_triluminous) {
                newMode = 3;
            }
            if (newMode != currentMode) {
                Settings.Secure.putInt(
                        getContext().getContentResolver(),
                        "display_engine_mode",
                        newMode
                );
                currentMode = newMode;
            }
        });
    }
}
