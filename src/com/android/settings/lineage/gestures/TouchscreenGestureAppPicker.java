/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lineage.gestures;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settingslib.widget.CandidateInfo;

import lineageos.hardware.LineageHardwareManager;
import lineageos.hardware.TouchscreenGesture;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/**
 * App picker for the {@link TouchscreenGestureConstants#ACTION_LAUNCH_APP} action, reached from
 * {@link TouchscreenGestureSettings} when the user assigns "Open app" to a gesture.
 *
 * <p>Candidates are launcher entries, so the stored component always has something to launch —
 * {@link KeyHandler} starts it with a plain MAIN/LAUNCHER intent and never needs LauncherApps.
 * Picking also writes the action and arms the gesture in the HAL, because the list preference does
 * not persist "Open app" until an app is actually chosen.
 */
public class TouchscreenGestureAppPicker extends RadioButtonPickerFragment {

    static final String EXTRA_KEYCODE = "keycode";

    private int mKeycode = -1;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        final Bundle args = getArguments();
        if (args != null) {
            mKeycode = args.getInt(EXTRA_KEYCODE, -1);
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.touchscreen_gesture_app_picker;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.LINEAGE;
    }

    @Override
    protected List<? extends CandidateInfo> getCandidates() {
        final PackageManager pm = requireContext().getPackageManager();
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> resolved =
                pm.queryIntentActivitiesAsUser(launcherIntent, 0 /* flags */, mUserId);

        final List<AppCandidateInfo> candidates = new ArrayList<>(resolved.size());
        for (final ResolveInfo info : resolved) {
            candidates.add(new AppCandidateInfo(pm, info));
        }
        final Collator collator = Collator.getInstance();
        candidates.sort((a, b) -> collator.compare(a.mLabel.toString(), b.mLabel.toString()));
        return candidates;
    }

    @Override
    protected String getDefaultKey() {
        return TouchscreenGestureStore.getApp(requireContext(), mKeycode);
    }

    @Override
    protected boolean setDefaultKey(final String key) {
        if (mKeycode < 0 || key == null) {
            return false;
        }
        final Context context = requireContext();
        TouchscreenGestureStore.setApp(context, mKeycode, key);
        TouchscreenGestureStore.setAction(context, mKeycode,
                TouchscreenGestureConstants.ACTION_LAUNCH_APP);
        setGestureEnabled(context);
        return true;
    }

    @Override
    protected void onSelectionPerformed(final boolean success) {
        super.onSelectionPerformed(success);
        if (success) {
            getActivity().finish();
        }
    }

    private void setGestureEnabled(final Context context) {
        final LineageHardwareManager manager = LineageHardwareManager.getInstance(context);
        for (final TouchscreenGesture gesture : manager.getTouchscreenGestures()) {
            if (gesture.keycode == mKeycode) {
                manager.setTouchscreenGestureEnabled(gesture, true);
                return;
            }
        }
    }

    private static class AppCandidateInfo extends CandidateInfo {

        private final PackageManager mPm;
        private final ResolveInfo mInfo;
        private final CharSequence mLabel;
        private final String mKey;

        AppCandidateInfo(final PackageManager pm, final ResolveInfo info) {
            super(true /* enabled */);
            mPm = pm;
            mInfo = info;
            mLabel = info.loadLabel(pm);
            mKey = new ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                    .flattenToString();
        }

        @Override
        public CharSequence loadLabel() {
            return mLabel;
        }

        @Override
        public Drawable loadIcon() {
            return mInfo.loadIcon(mPm);
        }

        @Override
        public String getKey() {
            return mKey;
        }
    }
}
