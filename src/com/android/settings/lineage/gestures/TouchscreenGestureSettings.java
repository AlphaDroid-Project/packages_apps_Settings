/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lineage.gestures;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.lineage.utils.ResourceUtils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import lineageos.hardware.LineageHardwareManager;
import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.hardware.TouchscreenGesture;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable
public class TouchscreenGestureSettings extends SettingsPreferenceFragment {

    private static final String TAG = "TouchscreenGestureSettings";

    private static final String KEY_TOUCHSCREEN_GESTURE = "touchscreen_gesture";
    private static final String KEY_TOUCHSCREEN_GESTURE_SETTINGS =
            "touchscreen_gesture_settings";
    private static final String KEY_TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK =
            "touchscreen_gesture_haptic_feedback";
    private static final String TOUCHSCREEN_GESTURE_TITLE = KEY_TOUCHSCREEN_GESTURE + "_%s_title";

    private TouchscreenGesture[] mTouchscreenGestures;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Actions live in Settings.System (see TouchscreenGestureStore), so no SharedPreferences
        // plumbing is needed — and none of it would work anyway: SettingsActivity replaces the
        // default prefs name with SharedPreferencesLogger, and system_server could not see writes
        // to a SharedPreferences file from this process.
        addPreferencesFromResource(R.xml.touchscreen_gesture_settings);

        if (isTouchscreenGesturesSupported(getContext())) {
            initTouchscreenGestures();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The app picker writes the action and the chosen app straight to Settings.System, so
        // re-read every row on the way back instead of trusting what the dialog left behind.
        final PreferenceScreen screen = getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            final Preference preference = screen.getPreference(i);
            if (preference instanceof TouchscreenGesturePreference) {
                ((TouchscreenGesturePreference) preference).refresh();
            }
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LINEAGE;
    }

    private void launchAppPicker(final int keycode) {
        final Bundle args = new Bundle();
        args.putInt(TouchscreenGestureAppPicker.EXTRA_KEYCODE, keycode);
        new SubSettingLauncher(getContext())
                .setDestination(TouchscreenGestureAppPicker.class.getName())
                .setSourceMetricsCategory(getMetricsCategory())
                .setTitleRes(R.string.touchscreen_gesture_app_picker_title)
                .setArguments(args)
                .launch();
    }

    private void initTouchscreenGestures() {
        final LineageHardwareManager manager = LineageHardwareManager.getInstance(getContext());
        mTouchscreenGestures = manager.getTouchscreenGestures();
        final int[] defaults = getDefaultGestureActions(requireContext(), mTouchscreenGestures);
        for (final TouchscreenGesture gesture : mTouchscreenGestures) {
            getPreferenceScreen().addPreference(new TouchscreenGesturePreference(
                    getContext(), gesture, defaults[gesture.id]));
        }
    }

    /**
     * Per-device default actions from {@code config_defaultTouchscreenGestureActions}, indexed by
     * the HAL's sequential gesture id. Applied only until the user picks something, after which
     * the stored Settings.System value wins.
     */
    private static int[] getDefaultGestureActions(final Context context,
            final TouchscreenGesture[] gestures) {
        final int[] configured = context.getResources().getIntArray(
                R.array.config_defaultTouchscreenGestureActions);
        if (configured.length >= gestures.length) {
            return configured;
        }
        final int[] filled = new int[gestures.length];
        System.arraycopy(configured, 0, filled, 0, configured.length);
        return filled;
    }

    private class TouchscreenGesturePreference extends LineageSystemSettingListPreference {
        private final Context mContext;
        private final TouchscreenGesture mGesture;
        private final int mDefaultAction;

        public TouchscreenGesturePreference(final Context context,
                                            final TouchscreenGesture gesture,
                                            final int defaultAction) {
            // Persists to Settings.System under getKey(); null attrs is fine (see
            // SelfRemovingListPreference's Context-only constructor).
            super(context, null);
            mContext = context;
            mGesture = gesture;
            mDefaultAction = defaultAction;

            setKey(TouchscreenGestureStore.buildPreferenceKey(gesture));
            setEntries(R.array.touchscreen_gesture_action_entries);
            setEntryValues(R.array.touchscreen_gesture_action_values);
            setDefaultValue(String.valueOf(defaultAction));
            setIcon(getIconDrawableResourceForAction(defaultAction));

            setSummary("%s");
            setDialogTitle(R.string.touchscreen_gesture_action_dialog_title);
            setTitle(ResourceUtils.getLocalizedString(
                    context.getResources(), gesture.name, TOUCHSCREEN_GESTURE_TITLE));
        }

        @Override
        public boolean callChangeListener(final Object newValue) {
            final int action = Integer.parseInt(String.valueOf(newValue));
            if (action == TouchscreenGestureConstants.ACTION_LAUNCH_APP) {
                // Nothing to persist yet — "Open app" without an app would be a dead gesture. The
                // picker writes the action and the component together and arms the gesture, and
                // onResume() picks the result up. Returning false leaves the old value in place if
                // the user backs out.
                launchAppPicker(mGesture.keycode);
                return false;
            }
            final LineageHardwareManager manager = LineageHardwareManager.getInstance(mContext);
            if (!manager.setTouchscreenGestureEnabled(mGesture, action > 0)) {
                Log.e(TAG, "HAL rejected enable for " + mGesture.name
                        + " keycode=" + mGesture.keycode + " action=" + action);
                return false;
            }
            return super.callChangeListener(newValue);
        }

        @Override
        public void setValue(String value) {
            super.setValue(value);
            // Keep the icon in step with the stored value (set on load and on change).
            final int action = value == null ? 0 : Integer.parseInt(value);
            if (action == TouchscreenGestureConstants.ACTION_LAUNCH_APP) {
                showChosenApp();
            } else {
                setIcon(getIconDrawableResourceForAction(action));
                setSummary("%s");
            }
        }

        void refresh() {
            setValue(String.valueOf(TouchscreenGestureStore.getAction(
                    mContext, mGesture.keycode, mDefaultAction)));
        }

        /** Show the picked app's own icon and name rather than a generic "Open app". */
        private void showChosenApp() {
            final String flattened = TouchscreenGestureStore.getApp(mContext, mGesture.keycode);
            final ComponentName component = flattened == null
                    ? null : ComponentName.unflattenFromString(flattened);
            if (component != null) {
                final PackageManager pm = mContext.getPackageManager();
                try {
                    final ActivityInfo info = pm.getActivityInfo(component, 0 /* flags */);
                    setIcon(info.loadIcon(pm));
                    // ListPreference runs the summary through String.format(), so a label such as
                    // "100% Battery" would blow up unescaped.
                    setSummary(info.loadLabel(pm).toString().replace("%", "%%"));
                    return;
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Gesture " + mGesture.name + " points at a missing app: " + flattened);
                }
            }
            setIcon(R.drawable.ic_gesture_action_none);
            setSummary("%s");
        }

        private int getIconDrawableResourceForAction(final int action) {
            switch (action) {
                case TouchscreenGestureConstants.ACTION_CAMERA:
                    return R.drawable.ic_gesture_action_camera;
                case TouchscreenGestureConstants.ACTION_FLASHLIGHT:
                    return R.drawable.ic_gesture_action_flashlight;
                case TouchscreenGestureConstants.ACTION_BROWSER:
                    return R.drawable.ic_gesture_action_browser;
                case TouchscreenGestureConstants.ACTION_DIALER:
                    return R.drawable.ic_gesture_action_dialer;
                case TouchscreenGestureConstants.ACTION_EMAIL:
                    return R.drawable.ic_gesture_action_email;
                case TouchscreenGestureConstants.ACTION_MESSAGES:
                    return R.drawable.ic_gesture_action_messages;
                case TouchscreenGestureConstants.ACTION_PLAY_PAUSE_MUSIC:
                    return R.drawable.ic_gesture_action_play_pause;
                case TouchscreenGestureConstants.ACTION_PREVIOUS_TRACK:
                    return R.drawable.ic_gesture_action_previous_track;
                case TouchscreenGestureConstants.ACTION_NEXT_TRACK:
                    return R.drawable.ic_gesture_action_next_track;
                case TouchscreenGestureConstants.ACTION_VOLUME_DOWN:
                    return R.drawable.ic_gesture_action_volume_down;
                case TouchscreenGestureConstants.ACTION_VOLUME_UP:
                    return R.drawable.ic_gesture_action_volume_up;
                case TouchscreenGestureConstants.ACTION_AMBIENT_DISPLAY:
                    return R.drawable.ic_gesture_action_ambient_display;
                default:
                    // No gesture action
                    return R.drawable.ic_gesture_action_none;
            }
        }
    }

    public static void restoreTouchscreenGestureStates(final Context context) {
        if (!isTouchscreenGesturesSupported(context)) {
            return;
        }

        final LineageHardwareManager manager = LineageHardwareManager.getInstance(context);
        final TouchscreenGesture[] gestures = manager.getTouchscreenGestures();
        final int[] actionList = TouchscreenGestureStore.buildActionList(context, gestures);
        for (final TouchscreenGesture gesture : gestures) {
            manager.setTouchscreenGestureEnabled(gesture, actionList[gesture.id] > 0);
        }
    }

    private static boolean isTouchscreenGesturesSupported(final Context context) {
        final LineageHardwareManager manager = LineageHardwareManager.getInstance(context);
        return manager.isSupported(LineageHardwareManager.FEATURE_TOUCHSCREEN_GESTURES);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        @Override
        public List<String> getNonIndexableKeys(Context context) {
            final List<String> result = new ArrayList<String>();

            if (!isTouchscreenGesturesSupported(context)) {
                result.add(KEY_TOUCHSCREEN_GESTURE_SETTINGS);
                result.add(KEY_TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK);
            }
            return result;
        }
    };
}
