/*
 * SPDX-FileCopyrightText: 2017 The Android Open Source Project
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.display;

import static android.hardware.display.ColorDisplayManager.COLOR_MODE_AUTOMATIC;
import static android.hardware.display.ColorDisplayManager.COLOR_MODE_BOOSTED;
import static android.hardware.display.ColorDisplayManager.COLOR_MODE_NATURAL;
import static android.hardware.display.ColorDisplayManager.COLOR_MODE_SATURATED;
import static android.hardware.display.ColorDisplayManager.VENDOR_COLOR_MODE_RANGE_MAX;
import static android.hardware.display.ColorDisplayManager.VENDOR_COLOR_MODE_RANGE_MIN;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settings.R;
import com.android.settings.alpha.AlphaPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
@SearchIndexable
public class ColorModePreferenceFragment extends AlphaPreferenceFragment {

    private static final String KEY_COLOR_MODE_PREFIX = "color_mode_";
    private static final int COLOR_MODE_FALLBACK = COLOR_MODE_NATURAL;

    private static final String PREF_COLOR_MODE_PREVIEW = "color_mode_preview";
    private static final String PREF_AOSP_CATEGORY = "aosp_color_mode_category";
    private static final String PREF_DISPLAY_ENGINE = "display_engine_mode";

    private ContentObserver mAccessibilityObserver;
    private ColorDisplayManager mColorDisplayManager;

    private PreferenceCategory mAospCategory;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mColorDisplayManager = context.getSystemService(ColorDisplayManager.class);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.color_mode_settings);

        LayoutPreference preview = findPreference(PREF_COLOR_MODE_PREVIEW);
        configurePreviewPager(preview, savedInstanceState);

        mAospCategory = findPreference(PREF_AOSP_CATEGORY);
        PreferenceCategory displayEngineCategory = findPreference("display_engine_category");
        Preference footerPref = findPreference("display_engine_mode_footer");

        // Hide Display Engine category (margin + list) and footer if hardware doesn't support it
        if (!ColorDisplayManager.isColorTransformAccelerated(getContext())) {
            if (displayEngineCategory != null) getPreferenceScreen().removePreference(displayEngineCategory);
            if (footerPref != null) getPreferenceScreen().removePreference(footerPref);
        }

        setupAospColorModes();
        registerAccessibilityObserver();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUiState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAccessibilityObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mAccessibilityObserver);
            mAccessibilityObserver = null;
        }
    }

    private void registerAccessibilityObserver() {
        final ContentResolver cr = getContext().getContentResolver();
        mAccessibilityObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                if (ColorDisplayManager.areAccessibilityTransformsEnabled(getContext())) {
                    getActivity().finish();
                }
            }
        };
        cr.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED),
                false, mAccessibilityObserver, UserHandle.USER_CURRENT);
        cr.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED),
                false, mAccessibilityObserver, UserHandle.USER_CURRENT);
    }

    private void setupAospColorModes() {
        if (mAospCategory == null) return;
        mAospCategory.removeAll();

        int[] availableModes = ColorModeUtils.getAvailableColorModes(getContext());
        if (availableModes == null || availableModes.length == 0) {
            getPreferenceScreen().removePreference(mAospCategory);
            return;
        }

        final Map<Integer, String> colorModesToSummaries =
                ColorModeUtils.getColorModeMapping(getResources());

        for (int colorMode : availableModes) {
            SelectorWithWidgetPreference pref = new SelectorWithWidgetPreference(getPrefContext());
            pref.setKey(getKeyForColorMode(colorMode));
            pref.setTitle(colorModesToSummaries.get(colorMode));

            pref.setOnPreferenceClickListener(p -> {
                mColorDisplayManager.setColorMode(colorMode);
                updateUiState();
                return true;
            });

            mAospCategory.addPreference(pref);
        }
    }

    private void updateUiState() {
        if (mAospCategory == null) return;

        int currentAospMode = mColorDisplayManager.getColorMode();
        if (!isValidColorMode(currentAospMode)) currentAospMode = COLOR_MODE_FALLBACK;

        String selectedAospKey = getKeyForColorMode(currentAospMode);
        for (int i = 0; i < mAospCategory.getPreferenceCount(); i++) {
            Preference pref = mAospCategory.getPreference(i);
            if (pref instanceof SelectorWithWidgetPreference) {
                ((SelectorWithWidgetPreference) pref).setChecked(
                        TextUtils.equals(pref.getKey(), selectedAospKey));
            }
        }
    }

    private String getKeyForColorMode(int colorMode) {
        return KEY_COLOR_MODE_PREFIX + colorMode;
    }

    private boolean isValidColorMode(int colorMode) {
        return colorMode == COLOR_MODE_NATURAL
                || colorMode == COLOR_MODE_BOOSTED
                || colorMode == COLOR_MODE_SATURATED
                || colorMode == COLOR_MODE_AUTOMATIC
                || (colorMode >= VENDOR_COLOR_MODE_RANGE_MIN
                && colorMode <= VENDOR_COLOR_MODE_RANGE_MAX);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.COLOR_MODE_SETTINGS;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.color_mode_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    final int[] availableColorModes =
                            ColorModeUtils.getAvailableColorModes(context);
                    boolean hasAosp = availableColorModes != null && availableColorModes.length > 0;
                    return hasAosp && !ColorDisplayManager.areAccessibilityTransformsEnabled(context);
                }
            };
}