/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lineage.gestures;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.session.MediaSessionLegacyHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import androidx.annotation.Keep;

import com.android.internal.os.DeviceKeyHandler;

import java.util.List;

/**
 * Loaded into system_server by PhoneWindowManager via PathClassLoader reflection:
 * {@code getConstructor(Context.class)}. Must keep a public Context constructor
 * (see Settings proguard.flags) — R8 otherwise strips it and boot fails with
 * {@code NoSuchMethodException: KeyHandler.<init> [class android.content.Context]}.
 */
@Keep
public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();

    private static final String GESTURE_WAKEUP_REASON = "lineage-gesture-wakeup";
    private static final String PULSE_ACTION = "com.android.systemui.doze.pulse";
    private static final int GESTURE_REQUEST = 0;
    private static final int GESTURE_WAKELOCK_DURATION = 3000;
    private static final int EVENT_PROCESS_WAKELOCK_DURATION = 500;
    private static final int DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private final WakeLock mGestureWakeLock;
    private final EventHandler mEventHandler;
    private final CameraManager mCameraManager;
    private final Vibrator mVibrator;

    private final boolean mProximityWakeSupported;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private WakeLock mProximityWakeLock;
    private boolean mDefaultProximity;
    private int mProximityTimeOut;

    private String mRearCameraId;
    private boolean mTorchEnabled;

    private final Object mSingleTapLock = new Object();
    private boolean mSingleTapPending;
    private int mPendingSingleTapAction;
    private int mPendingSingleTapKeycode;

    @Keep
    public KeyHandler(final Context context) {
        mContext = context;

        mAudioManager = mContext.getSystemService(AudioManager.class);

        mPowerManager = context.getSystemService(PowerManager.class);
        mGestureWakeLock = mPowerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Lineage:GestureWakeLock");

        mEventHandler = new EventHandler(Looper.getMainLooper());

        mCameraManager = mContext.getSystemService(CameraManager.class);
        mCameraManager.registerTorchCallback(new TorchModeCallback(), mEventHandler);

        mVibrator = context.getSystemService(Vibrator.class);

        final Resources resources = mContext.getResources();
        mProximityWakeSupported = resources.getBoolean(
                com.android.internal.R.bool.config_proximityCheckOnWake);

        if (mProximityWakeSupported) {
            mProximityTimeOut = resources.getInteger(
                    com.android.internal.R.integer.config_proximityCheckTimeout);
            mDefaultProximity = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.
                            config_proximityCheckOnWakeEnabledByDefault);

            mSensorManager = context.getSystemService(SensorManager.class);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            mProximityWakeLock = mPowerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "Lineage:ProximityWakeLock");
        }
        // Nothing to cache or register: actions are read from Settings.System at dispatch time.
        Log.i(TAG, "KeyHandler ready");
    }

    private class TorchModeCallback extends CameraManager.TorchCallback {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mRearCameraId)) return;
            mTorchEnabled = enabled;
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (!cameraId.equals(mRearCameraId)) return;
            mTorchEnabled = false;
        }
    }

    public KeyEvent handleKeyEvent(final KeyEvent event) {
        final int scanCode = event.getScanCode();

        // Only gesture scancodes are ours; everything else must pass straight through.
        if (!TouchscreenGestureStore.isGestureKeycode(scanCode)) {
            return event;
        }

        // Read the action at dispatch time. Settings.System is provider-backed, so a change made
        // in the UI is visible here immediately — no cached map, no update broadcast to keep in
        // sync. (The old SharedPreferences map only refreshed when system_server restarted, which
        // is why gesture actions used to apply only after a reboot.)
        final int action = TouchscreenGestureStore.getAction(mContext, scanCode, 0);
        if (event.getAction() != KeyEvent.ACTION_UP || !hasSetupCompleted()) {
            return event;
        }

        Log.e(TAG, "gesture scancode=" + scanCode
                + " keyCode=" + event.getKeyCode()
                + " action=" + action);

        if (scanCode == TouchscreenGestureStore.KEYCODE_SINGLE_TAP && isDoubleTapToWakeEnabled()) {
            handleSingleTap(action, scanCode);
        } else {
            dispatchAction(action, scanCode);
        }

        return null;
    }

    private void dispatchAction(final int action, final int keycode) {
        if (action == 0 || mEventHandler.hasMessages(GESTURE_REQUEST)) {
            return;
        }

        final Message msg = getMessageForAction(action, keycode);
        final boolean proxWakeEnabled = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.PROXIMITY_ON_WAKE,
                mDefaultProximity ? 1 : 0, UserHandle.USER_CURRENT) == 1;
        if (mProximityWakeSupported && proxWakeEnabled && mProximitySensor != null) {
            mGestureWakeLock.acquire(2L * mProximityTimeOut);
            mEventHandler.sendMessageDelayed(msg, mProximityTimeOut);
            processEvent(action, keycode);
        } else {
            mGestureWakeLock.acquire(EVENT_PROCESS_WAKELOCK_DURATION);
            mEventHandler.sendMessage(msg);
        }
    }

    /**
     * Single tap and double tap to wake share one firmware mask: the touch HAL arms gestures on
     * DOUBLE_TAP_INDEP_NODE and the power HAL sets the double-tap bit of that same node (see
     * hardware/oplus/power/power-mode.cpp). While single tap is armed the panel reports one
     * SINGLE_TAP per tap and never reports DTAP_DETECT, so the kernel's KEY_WAKEUP for double tap
     * is never emitted and the first tap of a double tap already runs the single-tap action.
     *
     * <p>Stock hits the same wall and disambiguates in software — OplusBlackScreenGestureControll
     * synthesizes gesture=1 when a second gesture=16 arrives inside the double-tap window. We do
     * the same, except stock dispatches the single tap immediately <em>and</em> the double tap
     * afterwards, which only works because its pair composes (AOD pulse, then full wake). Our
     * actions are arbitrary, so the single tap is held back for the window instead and cancelled
     * if a second tap lands. Cost: single tap is delayed by the double-tap timeout, and only while
     * double tap to wake is enabled.
     */
    private void handleSingleTap(final int action, final int keycode) {
        synchronized (mSingleTapLock) {
            if (mSingleTapPending) {
                // Second tap inside the window: this is the double tap the firmware can no longer
                // report, so drop the pending single-tap action and wake instead.
                mSingleTapPending = false;
                mEventHandler.removeCallbacks(mSingleTapTimeout);
                performWakeUp();
                return;
            }
            if (action == 0) {
                return;
            }
            mPendingSingleTapAction = action;
            mPendingSingleTapKeycode = keycode;
            mSingleTapPending = true;
        }
        // uptimeMillis() — and with it the pending callback — does not advance while suspended,
        // so hold the AP up for at least the window.
        mGestureWakeLock.acquire(DOUBLE_TAP_TIMEOUT + EVENT_PROCESS_WAKELOCK_DURATION);
        mEventHandler.postDelayed(mSingleTapTimeout, DOUBLE_TAP_TIMEOUT);
    }

    private final Runnable mSingleTapTimeout = new Runnable() {
        @Override
        public void run() {
            final int action;
            final int keycode;
            synchronized (mSingleTapLock) {
                if (!mSingleTapPending) {
                    return;
                }
                mSingleTapPending = false;
                action = mPendingSingleTapAction;
                keycode = mPendingSingleTapKeycode;
            }
            dispatchAction(action, keycode);
        }
    };

    private boolean isDoubleTapToWakeEnabled() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.DOUBLE_TAP_TO_WAKE, 0, UserHandle.USER_CURRENT) != 0;
    }

    private boolean hasSetupCompleted() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    private void processEvent(final int action, final int keycode) {
        mSensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (mProximityWakeLock.isHeld()) {
                    mProximityWakeLock.release();
                }
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took too long; ignoring
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] >= mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForAction(action, keycode);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // Ignore
            }

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private Message getMessageForAction(final int action, final int keycode) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.arg1 = action;
        // ACTION_LAUNCH_APP stores its app per gesture, so the handler needs to know which one.
        msg.arg2 = keycode;
        return msg;
    }

    private class EventHandler extends Handler {

        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.arg1) {
                case TouchscreenGestureConstants.ACTION_CAMERA:
                    launchCamera();
                    break;
                case TouchscreenGestureConstants.ACTION_FLASHLIGHT:
                    toggleFlashlight();
                    break;
                case TouchscreenGestureConstants.ACTION_BROWSER:
                    launchBrowser();
                    break;
                case TouchscreenGestureConstants.ACTION_DIALER:
                    launchDialer();
                    break;
                case TouchscreenGestureConstants.ACTION_EMAIL:
                    launchEmail();
                    break;
                case TouchscreenGestureConstants.ACTION_MESSAGES:
                    launchMessages();
                    break;
                case TouchscreenGestureConstants.ACTION_PLAY_PAUSE_MUSIC:
                    playPauseMusic();
                    break;
                case TouchscreenGestureConstants.ACTION_PREVIOUS_TRACK:
                    previousTrack();
                    break;
                case TouchscreenGestureConstants.ACTION_NEXT_TRACK:
                    nextTrack();
                    break;
                case TouchscreenGestureConstants.ACTION_VOLUME_DOWN:
                    volumeDown();
                    break;
                case TouchscreenGestureConstants.ACTION_VOLUME_UP:
                    volumeUp();
                    break;
                case TouchscreenGestureConstants.ACTION_AMBIENT_DISPLAY:
                    launchDozePulse();
                    break;
                case TouchscreenGestureConstants.ACTION_LAUNCH_APP:
                    launchApp(msg.arg2);
                    break;
            }
        }
    }

    private void launchCamera() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        final Intent intent = new Intent(lineageos.content.Intent.ACTION_SCREEN_CAMERA_GESTURE);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT,
                Manifest.permission.STATUS_BAR_SERVICE);
        doHapticFeedback();
    }

    private void launchBrowser() {
        performWakeUp();
        final Intent intent = getLaunchableIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("http:")));
        startActivitySafely(intent);
        doHapticFeedback();
    }

    private void launchDialer() {
        performWakeUp();
        final Intent intent = new Intent(Intent.ACTION_DIAL, null);
        startActivitySafely(intent);
        doHapticFeedback();
    }

    private void launchEmail() {
        performWakeUp();
        final Intent intent = getLaunchableIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:")));
        startActivitySafely(intent);
        doHapticFeedback();
    }

    private void launchMessages() {
        performWakeUp();
        final Intent intent = getLaunchableIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse("sms:")));
        startActivitySafely(intent);
        doHapticFeedback();
    }

    /**
     * Opens the launcher entry the user picked for this gesture. The picker only offers
     * MAIN/LAUNCHER activities, so a plain launch intent is enough — no LauncherApps, and no
     * shortcut plumbing, in system_server.
     */
    private void launchApp(final int keycode) {
        final String flattened = TouchscreenGestureStore.getApp(mContext, keycode);
        final ComponentName component = flattened == null
                ? null : ComponentName.unflattenFromString(flattened);
        if (component == null) {
            Log.w(TAG, "No app stored for gesture keycode " + keycode);
            return;
        }

        performWakeUp();
        final Intent intent = Intent.makeMainActivity(component);
        // Deliberately not CLEAR_TOP like the other launches here: reopening an app should return
        // to where the user left it, the way a launcher icon does.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mContext.startActivityAsUser(intent, null, new UserHandle(UserHandle.USER_CURRENT));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Could not open " + flattened, e);
            return;
        }
        doHapticFeedback();
    }

    private void performWakeUp() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_GESTURE,
                GESTURE_WAKEUP_REASON);
    }

    private void toggleFlashlight() {
        String rearCameraId = getRearCameraId();
        if (rearCameraId != null) {
            mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            try {
                mCameraManager.setTorchMode(rearCameraId, !mTorchEnabled);
                mTorchEnabled = !mTorchEnabled;
            } catch (CameraAccessException e) {
                // Ignore
            }
            doHapticFeedback();
        }
    }

    private void playPauseMusic() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        doHapticFeedback();
    }

    private void previousTrack() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        doHapticFeedback();
    }

    private void nextTrack() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT);
        doHapticFeedback();
    }

    private void volumeDown() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
        doHapticFeedback();
    }

    private void volumeUp() {
        mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
        doHapticFeedback();
    }

    private void launchDozePulse() {
        final boolean dozeEnabled = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ENABLED, 1) != 0;
        if (dozeEnabled) {
            mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            final Intent intent = new Intent(PULSE_ACTION);
            mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
            doHapticFeedback();
        }
    }

    private void dispatchMediaKeyWithWakeLockToMediaSession(final int keycode) {
        final MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(mContext);
        if (helper == null) {
            Log.w(TAG, "Unable to send media key event");
            return;
        }
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
        helper.sendMediaButtonEvent(event, true);
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        helper.sendMediaButtonEvent(event, true);
    }

    private void startActivitySafely(final Intent intent) {
        if (intent == null) {
            Log.w(TAG, "No intent passed to startActivitySafely");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            final UserHandle user = new UserHandle(UserHandle.USER_CURRENT);
            mContext.startActivityAsUser(intent, null, user);
        } catch (ActivityNotFoundException e) {
            // Ignore
        }
    }

    private void doHapticFeedback() {
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            return;
        }

        if (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
            final boolean enabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, 1,
                    UserHandle.USER_CURRENT) != 0;
            if (enabled) {
                mVibrator.vibrate(VibrationEffect.createOneShot(50,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    private String getRearCameraId() {
        if (mRearCameraId == null) {
            try {
                for (final String cameraId : mCameraManager.getCameraIdList()) {
                    final CameraCharacteristics characteristics =
                            mCameraManager.getCameraCharacteristics(cameraId);
                    final int orientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (orientation == CameraCharacteristics.LENS_FACING_BACK) {
                        mRearCameraId = cameraId;
                        break;
                    }
                }
            } catch (CameraAccessException e) {
                // Ignore
            }
        }
        return mRearCameraId;
    }

    private Intent getLaunchableIntent(Intent intent) {
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> resInfo = pm.queryIntentActivities(intent,
                PackageManager.ResolveInfoFlags.of(0));
        if (resInfo.isEmpty()) {
            return null;
        }
        return pm.getLaunchIntentForPackage(resInfo.get(0).activityInfo.packageName);
    }
}
