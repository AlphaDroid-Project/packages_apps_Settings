/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lineage.gestures;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import lineageos.hardware.TouchscreenGesture;

/**
 * Gesture action storage, backed by {@link Settings.System}.
 *
 * <p>Actions used to live in a SharedPreferences file, which cannot work here: the UI runs in
 * the Settings process while {@link KeyHandler} runs in system_server, and SharedPreferences
 * caches its backing file per process with no cross-process invalidation (MODE_MULTI_PROCESS was
 * deprecated precisely because it never worked reliably). system_server therefore kept whatever
 * it read at construction and only picked up changes after a reboot.
 *
 * <p>{@link Settings.System} goes through SettingsProvider over binder, so it is genuinely
 * cross-process and always current. This mirrors the haptic-feedback toggle on the same screen,
 * which always applied live for exactly this reason.
 *
 * <p>Keys are named by <b>keycode</b> ({@code touchscreen_gesture_252}), not by the HAL's
 * sequential list id. The id is an index into the device's supported-gesture list, so changing
 * {@code kSupportedGestures} would silently re-map every saved action; the keycode is stable, and
 * it is also what {@link KeyHandler} dispatches on.
 */
final class TouchscreenGestureStore {

    private static final String KEY_PREFIX = "touchscreen_gesture_";

    /**
     * Gesture keycodes are {@code KEY_GESTURE_START + type}, with type 2 (UP_VEE) through
     * 18 (S_GESTURE) — see the kernel's synaptics_hbp driver.
     */
    static final int KEYCODE_MIN = 248;
    static final int KEYCODE_MAX = 264;

    /** KEY_GESTURE_START + SINGLE_TAP (16). Needs special handling, see {@link KeyHandler}. */
    static final int KEYCODE_SINGLE_TAP = 262;

    private TouchscreenGestureStore() {}

    static boolean isGestureKeycode(final int keycode) {
        return keycode >= KEYCODE_MIN && keycode <= KEYCODE_MAX;
    }

    static String buildPreferenceKey(final TouchscreenGesture gesture) {
        return buildPreferenceKey(gesture.keycode);
    }

    static String buildPreferenceKey(final int keycode) {
        return KEY_PREFIX + keycode;
    }

    /**
     * Companion key holding the flattened {@link android.content.ComponentName} to open when the
     * gesture's action is {@link TouchscreenGestureConstants#ACTION_LAUNCH_APP}. Kept separate from
     * the action so switching away from "Open app" and back remembers the previous choice.
     */
    static String buildAppPreferenceKey(final int keycode) {
        return buildPreferenceKey(keycode) + "_app";
    }

    /**
     * Read the action for a gesture keycode. Always hits SettingsProvider, so callers in
     * system_server see UI changes immediately and no cache has to be invalidated.
     */
    static int getAction(final Context context, final int keycode, final int defaultAction) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                buildPreferenceKey(keycode), defaultAction, UserHandle.USER_CURRENT);
    }

    static void setAction(final Context context, final int keycode, final int action) {
        Settings.System.putIntForUser(context.getContentResolver(),
                buildPreferenceKey(keycode), action, UserHandle.USER_CURRENT);
    }

    /** Flattened component to open, or {@code null} if the user has not picked one yet. */
    static String getApp(final Context context, final int keycode) {
        return Settings.System.getStringForUser(context.getContentResolver(),
                buildAppPreferenceKey(keycode), UserHandle.USER_CURRENT);
    }

    static void setApp(final Context context, final int keycode, final String flattenedComponent) {
        Settings.System.putStringForUser(context.getContentResolver(),
                buildAppPreferenceKey(keycode), flattenedComponent, UserHandle.USER_CURRENT);
    }

    static int[] buildActionList(final Context context, final TouchscreenGesture[] gestures) {
        final int[] result = new int[gestures.length];
        for (final TouchscreenGesture gesture : gestures) {
            if (gesture.id >= 0 && gesture.id < result.length) {
                result[gesture.id] = getAction(context, gesture.keycode, 0);
            }
        }
        return result;
    }
}
