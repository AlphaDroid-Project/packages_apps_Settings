/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod project
 * SPDX-FileCopyrightText: 2017-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.lineage.gestures

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.Preference.OnPreferenceChangeListener
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.core.SubSettingLauncher
import com.android.settings.lineage.utils.ResourceUtils
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.search.SearchIndexable
import lineageos.hardware.LineageHardwareManager
import lineageos.hardware.TouchscreenGesture
import lineageos.preference.LineageSystemSettingListPreference
import lineageos.preference.LineageSystemSettingMainSwitchPreference

@SearchIndexable
class TouchscreenGestureSettings : SettingsPreferenceFragment(), OnPreferenceChangeListener {

    private var touchscreenGestures: Array<TouchscreenGesture>? = null
    private var gestureCategory: PreferenceCategory? = null
    private var optionsCategory: PreferenceCategory? = null
    private var masterSwitch: LineageSystemSettingMainSwitchPreference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.touchscreen_gesture_settings)

        masterSwitch = findPreference(TouchscreenGestureStore.KEY_MASTER_ENABLE)
        // MainSwitchPreference routes bar clicks through callChangeListener first.
        masterSwitch?.onPreferenceChangeListener = this
        optionsCategory = findPreference(KEY_OPTIONS_CATEGORY)
        gestureCategory = findPreference(KEY_LIST_CATEGORY)

        if (isTouchscreenGesturesSupported(requireContext())) {
            initTouchscreenGestures()
            applyMasterEnabled(TouchscreenGestureStore.isMasterEnabled(requireContext()))
        }
    }

    override fun onResume() {
        super.onResume()
        val category = gestureCategory ?: return
        for (i in 0 until category.preferenceCount) {
            val pref = category.getPreference(i)
            if (pref is TouchscreenGesturePreference) {
                pref.refresh()
            }
        }
    }

    override fun getMetricsCategory(): Int = MetricsEvent.LINEAGE

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference.key == TouchscreenGestureStore.KEY_MASTER_ENABLE) {
            // Persist via MainSwitch setChecked → PreferenceDataStore after we return true.
            applyMasterEnabled(newValue as Boolean)
            return true
        }
        return true
    }

    private fun applyMasterEnabled(enabled: Boolean) {
        optionsCategory?.isEnabled = enabled
        gestureCategory?.isEnabled = enabled
        val context = requireContext()
        // Write master immediately — onPreferenceChange runs before MainSwitch persists.
        TouchscreenGestureStore.setMasterEnabled(context, enabled)
        if (enabled) {
            restoreTouchscreenGestureStates(context)
        } else {
            disableAllGestures(context)
        }
    }

    private fun launchAppPicker(keycode: Int) {
        val args = Bundle().apply {
            putInt(TouchscreenGestureAppPickerFragment.EXTRA_KEYCODE, keycode)
        }
        SubSettingLauncher(requireContext())
            .setDestination(TouchscreenGestureAppPickerFragment::class.java.name)
            .setSourceMetricsCategory(metricsCategory)
            .setTitleRes(R.string.touchscreen_gesture_app_picker_title)
            .setArguments(args)
            .launch()
    }

    private fun initTouchscreenGestures() {
        val manager = LineageHardwareManager.getInstance(requireContext())
        val gestures = manager.touchscreenGestures ?: return
        touchscreenGestures = gestures
        val defaults = getDefaultGestureActions(requireContext(), gestures)
        val category = gestureCategory ?: preferenceScreen
        for (gesture in gestures) {
            category.addPreference(
                TouchscreenGesturePreference(requireContext(), gesture, defaults[gesture.id])
            )
        }
    }

    private fun getDefaultGestureActions(
        context: Context,
        gestures: Array<TouchscreenGesture>,
    ): IntArray {
        val configured = context.resources.getIntArray(R.array.config_defaultTouchscreenGestureActions)
        if (configured.size >= gestures.size) {
            return configured
        }
        val filled = IntArray(gestures.size)
        System.arraycopy(configured, 0, filled, 0, configured.size)
        return filled
    }

    private inner class TouchscreenGesturePreference(
        private val appContext: Context,
        private val gesture: TouchscreenGesture,
        private val defaultAction: Int,
    ) : LineageSystemSettingListPreference(appContext, null) {

        init {
            key = TouchscreenGestureStore.buildPreferenceKey(gesture)
            setEntries(R.array.touchscreen_gesture_action_entries)
            setEntryValues(R.array.touchscreen_gesture_action_values)
            setDefaultValue(defaultAction.toString())
            setIcon(getIconDrawableResourceForAction(defaultAction))
            summary = "%s"
            setDialogTitle(R.string.touchscreen_gesture_action_dialog_title)
            title = ResourceUtils.getLocalizedString(
                appContext.resources,
                gesture.name,
                TOUCHSCREEN_GESTURE_TITLE,
            )
        }

        override fun callChangeListener(newValue: Any?): Boolean {
            val action = newValue.toString().toInt()
            if (action == TouchscreenGestureConstants.ACTION_LAUNCH_APP) {
                // Picker writes action + component (+ optional shortcut) together.
                launchAppPicker(gesture.keycode)
                return false
            }
            val manager = LineageHardwareManager.getInstance(appContext)
            if (!manager.setTouchscreenGestureEnabled(gesture, action > 0)) {
                Log.e(
                    TAG,
                    "HAL rejected enable for ${gesture.name} keycode=${gesture.keycode} action=$action",
                )
                return false
            }
            return super.callChangeListener(newValue)
        }

        override fun setValue(value: String?) {
            super.setValue(value)
            val action = value?.toIntOrNull() ?: 0
            if (action == TouchscreenGestureConstants.ACTION_LAUNCH_APP) {
                showChosenApp()
            } else {
                setIcon(getIconDrawableResourceForAction(action))
                summary = "%s"
            }
        }

        fun refresh() {
            value = TouchscreenGestureStore.getAction(
                appContext,
                gesture.keycode,
                defaultAction,
            ).toString()
        }

        private fun showChosenApp() {
            val flattened = TouchscreenGestureStore.getApp(appContext, gesture.keycode)
            val component = flattened?.let { ComponentName.unflattenFromString(it) }
            if (component != null) {
                val pm = appContext.packageManager
                try {
                    val info = pm.getActivityInfo(component, 0)
                    val shortcutId = TouchscreenGestureStore.getShortcut(appContext, gesture.keycode)
                    var label = info.loadLabel(pm).toString()
                    if (!shortcutId.isNullOrEmpty() && shortcutId != flattened) {
                        label = resolveShortcutLabel(component.packageName, shortcutId) ?: label
                    }
                    setIcon(info.loadIcon(pm))
                    // ListPreference formats the summary; escape % in app labels.
                    summary = label.replace("%", "%%")
                    return
                } catch (_: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Gesture ${gesture.name} points at a missing app: $flattened")
                }
            }
            setIcon(R.drawable.ic_gesture_action_none)
            summary = "%s"
        }

        private fun resolveShortcutLabel(packageName: String, shortcutId: String): String? {
            val launcherApps = appContext.getSystemService(LauncherApps::class.java) ?: return null
            return try {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(packageName)
                    .setShortcutIds(listOf(shortcutId))
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
                val shortcuts = launcherApps.getShortcuts(query, UserHandle.of(UserHandle.myUserId()))
                shortcuts?.firstOrNull()?.shortLabel?.toString()
                    ?: shortcuts?.firstOrNull()?.longLabel?.toString()
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve shortcut $shortcutId for $packageName", e)
                null
            }
        }

        private fun getIconDrawableResourceForAction(action: Int): Int = when (action) {
            TouchscreenGestureConstants.ACTION_CAMERA -> R.drawable.ic_gesture_action_camera
            TouchscreenGestureConstants.ACTION_FLASHLIGHT -> R.drawable.ic_gesture_action_flashlight
            TouchscreenGestureConstants.ACTION_BROWSER -> R.drawable.ic_gesture_action_browser
            TouchscreenGestureConstants.ACTION_DIALER -> R.drawable.ic_gesture_action_dialer
            TouchscreenGestureConstants.ACTION_EMAIL -> R.drawable.ic_gesture_action_email
            TouchscreenGestureConstants.ACTION_MESSAGES -> R.drawable.ic_gesture_action_messages
            TouchscreenGestureConstants.ACTION_PLAY_PAUSE_MUSIC ->
                R.drawable.ic_gesture_action_play_pause
            TouchscreenGestureConstants.ACTION_PREVIOUS_TRACK ->
                R.drawable.ic_gesture_action_previous_track
            TouchscreenGestureConstants.ACTION_NEXT_TRACK -> R.drawable.ic_gesture_action_next_track
            TouchscreenGestureConstants.ACTION_VOLUME_DOWN ->
                R.drawable.ic_gesture_action_volume_down
            TouchscreenGestureConstants.ACTION_VOLUME_UP -> R.drawable.ic_gesture_action_volume_up
            TouchscreenGestureConstants.ACTION_AMBIENT_DISPLAY ->
                R.drawable.ic_gesture_action_ambient_display
            else -> R.drawable.ic_gesture_action_none
        }
    }

    companion object {
        private const val TAG = "TouchscreenGestureSettings"
        private const val KEY_OPTIONS_CATEGORY = "touchscreen_gesture_options_category"
        private const val KEY_LIST_CATEGORY = "touchscreen_gesture_list_category"
        private const val KEY_TOUCHSCREEN_GESTURE_SETTINGS = "touchscreen_gesture_settings"
        private const val KEY_TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK =
            "touchscreen_gesture_haptic_feedback"
        private const val TOUCHSCREEN_GESTURE_TITLE = "touchscreen_gesture_%s_title"

        @JvmStatic
        fun restoreTouchscreenGestureStates(context: Context) {
            if (!isTouchscreenGesturesSupported(context)) {
                return
            }
            if (!TouchscreenGestureStore.isMasterEnabled(context)) {
                disableAllGestures(context)
                return
            }
            val manager = LineageHardwareManager.getInstance(context)
            val gestures = manager.touchscreenGestures ?: return
            val actionList = TouchscreenGestureStore.buildActionList(context, gestures)
            for (gesture in gestures) {
                manager.setTouchscreenGestureEnabled(gesture, actionList[gesture.id] > 0)
            }
        }

        @JvmStatic
        fun disableAllGestures(context: Context) {
            if (!isTouchscreenGesturesSupported(context)) {
                return
            }
            val manager = LineageHardwareManager.getInstance(context)
            val gestures = manager.touchscreenGestures ?: return
            for (gesture in gestures) {
                manager.setTouchscreenGestureEnabled(gesture, false)
            }
        }

        @JvmStatic
        fun isTouchscreenGesturesSupported(context: Context): Boolean {
            val manager = LineageHardwareManager.getInstance(context)
            return manager.isSupported(LineageHardwareManager.FEATURE_TOUCHSCREEN_GESTURES)
        }

        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER = object : BaseSearchIndexProvider() {
            override fun getNonIndexableKeys(context: Context): List<String> {
                val result = ArrayList<String>()
                if (!isTouchscreenGesturesSupported(context)) {
                    result.add(KEY_TOUCHSCREEN_GESTURE_SETTINGS)
                    result.add(KEY_TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK)
                    result.add(TouchscreenGestureStore.KEY_MASTER_ENABLE)
                }
                return result
            }
        }
    }
}
