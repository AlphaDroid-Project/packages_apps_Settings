/*
 * SPDX-FileCopyrightText: 2012 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lineage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.util.Log;

import com.android.settings.lineage.gestures.TouchscreenGestureSettings;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "LineageBootReceiver";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Gesture actions are stored per-user but the hardware state is global, so only the
        // owner's preferences get pushed to the HAL. (Upstream uses the now-deprecated
        // isPrimaryUser(); isSystemUser() is the same thing on a non-headless device.)
        if (!ctx.getSystemService(UserManager.class).isSystemUser()) {
            Log.d(TAG, "Not the system user, skipping hardware restoration.");
            return;
        }

        TouchscreenGestureSettings.restoreTouchscreenGestureStates(ctx);
    }
}
