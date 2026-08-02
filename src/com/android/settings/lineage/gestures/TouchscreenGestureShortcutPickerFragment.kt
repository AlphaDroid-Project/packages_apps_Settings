/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 *
 * Shortcut sub-screen adapted from Columbus LaunchAppShortcutSettingsFragment
 * (Apache-2.0, TheParasiteProject).
 */

package com.android.settings.lineage.gestures

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.widget.SelectorWithWidgetPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lineageos.hardware.LineageHardwareManager

/**
 * Second-level picker: open the app itself or one of its launcher shortcuts.
 */
class TouchscreenGestureShortcutPickerFragment :
    SettingsPreferenceFragment(),
    SelectorWithWidgetPreference.OnClickListener {

    private var keycode: Int = -1
    private var component: ComponentName? = null
    private var shortcuts: ArrayList<ShortcutInfo>? = null
    private var shortcutCategory: PreferenceCategory? = null
    private var launcherApps: LauncherApps? = null
    private var userHandle: UserHandle = UserHandle.of(UserHandle.myUserId())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        keycode = args?.getInt(EXTRA_KEYCODE, -1) ?: -1
        component = args?.getParcelable(EXTRA_COMPONENT, ComponentName::class.java)
        shortcuts = args?.getParcelableArrayList(EXTRA_SHORTCUTS, ShortcutInfo::class.java)
        addPreferencesFromResource(R.xml.touchscreen_gesture_shortcut_picker)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shortcutCategory = preferenceScreen.findPreference(KEY_SHORTCUT_LIST)
        launcherApps = requireContext().getSystemService(LauncherApps::class.java)
        userHandle = UserHandle.of(ActivityManager.getCurrentUser())
        if (shortcutCategory == null) {
            Log.e(TAG, "Shortcut list category missing")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch { populateShortcuts() }
    }

    override fun getMetricsCategory(): Int = MetricsEvent.LINEAGE

    override fun onRadioButtonClicked(emitter: SelectorWithWidgetPreference) {
        val component = component ?: return
        val key = emitter.key ?: return
        if (keycode < 0) return
        val flattened = component.flattenToString()
        TouchscreenGestureStore.setLaunchTarget(requireContext(), keycode, flattened, key)
        armGesture(requireContext(), keycode)
        updateCheckedState()
        // RESULT_OK so the app picker closes too (see setResultListener there).
        activity?.setResult(Activity.RESULT_OK)
        activity?.finish()
    }

    private suspend fun populateShortcuts() {
        val category = shortcutCategory ?: return
        val component = component ?: return
        val launcherApps = launcherApps ?: return
        val context = requireContext()
        val shortcutInfos = shortcuts ?: arrayListOf()

        val rows = withContext(Dispatchers.IO) {
            val data = mutableListOf<ShortcutRow>()
            val appIcon = try {
                launcherApps.getActivityList(component.packageName, userHandle)
                    .firstOrNull { it.componentName == component }
                    ?.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE)
            } catch (_: Exception) {
                null
            }
            // First entry: open the app itself (key = flattened component).
            data.add(
                ShortcutRow(
                    key = component.flattenToString(),
                    title = context.getString(R.string.touchscreen_gesture_launch_app_itself),
                    icon = appIcon,
                ),
            )
            for (info in shortcutInfos) {
                val icon = try {
                    launcherApps.getShortcutIconDrawable(
                        info,
                        DisplayMetrics.DENSITY_DEVICE_STABLE,
                    )
                } catch (_: Exception) {
                    appIcon
                }
                data.add(
                    ShortcutRow(
                        key = info.id,
                        title = info.shortLabel ?: info.longLabel ?: info.id,
                        icon = icon as Drawable?,
                    ),
                )
            }
            data
        }

        withContext(Dispatchers.Main) {
            category.removeAll()
            val currentShortcut = TouchscreenGestureStore.getShortcut(context, keycode)
                ?: TouchscreenGestureStore.getApp(context, keycode)
            for (row in rows) {
                val pref = SelectorWithWidgetPreference(context).apply {
                    key = row.key
                    title = row.title
                    icon = row.icon
                    isPersistent = false
                    setOnClickListener(this@TouchscreenGestureShortcutPickerFragment)
                    isChecked = row.key == currentShortcut
                    setExtraWidgetOnClickListener(null)
                }
                category.addPreference(pref)
            }
        }
    }

    private fun updateCheckedState() {
        val category = shortcutCategory ?: return
        val current = TouchscreenGestureStore.getShortcut(requireContext(), keycode)
            ?: TouchscreenGestureStore.getApp(requireContext(), keycode)
        for (i in 0 until category.preferenceCount) {
            val pref = category.getPreference(i)
            if (pref is SelectorWithWidgetPreference) {
                pref.isChecked = pref.key == current
            }
        }
    }

    private fun armGesture(context: Context, keycode: Int) {
        val manager = LineageHardwareManager.getInstance(context)
        val gestures = manager.touchscreenGestures ?: return
        for (gesture in gestures) {
            if (gesture.keycode == keycode) {
                manager.setTouchscreenGestureEnabled(gesture, true)
                return
            }
        }
    }

    private data class ShortcutRow(
        val key: String,
        val title: CharSequence?,
        val icon: Drawable?,
    )

    companion object {
        private const val TAG = "TouchscreenGestureShortcutPicker"
        const val EXTRA_KEYCODE = "keycode"
        const val EXTRA_COMPONENT = "component"
        const val EXTRA_SHORTCUTS = "shortcuts"
        private const val KEY_SHORTCUT_LIST = "touchscreen_gesture_shortcut_list"
    }
}
