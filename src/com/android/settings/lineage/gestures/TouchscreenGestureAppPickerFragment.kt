/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 *
 * App list UI adapted from Columbus (Quick Tap) LaunchSettingsFragment
 * (Apache-2.0, TheParasiteProject): radio rows + extra-widget for shortcuts.
 */

package com.android.settings.lineage.gestures

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.core.SubSettingLauncher
import com.android.settingslib.widget.SelectorWithWidgetPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lineageos.hardware.LineageHardwareManager
import java.text.Collator

/**
 * Columbus-style app picker for [TouchscreenGestureConstants.ACTION_LAUNCH_APP].
 *
 * Lists launcher activities as radio buttons. When an app exposes launcher shortcuts, the
 * extra widget opens [TouchscreenGestureShortcutPickerFragment].
 *
 * Population mirrors Columbus: bind the preference category in [onViewCreated], then load on
 * [Dispatchers.IO]. App discovery prefers [LauncherApps.getActivityList] and falls back to
 * [PackageManager.queryIntentActivities] (the path the old flat picker used successfully).
 */
class TouchscreenGestureAppPickerFragment :
    SettingsPreferenceFragment(),
    SelectorWithWidgetPreference.OnClickListener {

    private var keycode: Int = -1
    private var appListCategory: PreferenceCategory? = null
    private var launcherApps: LauncherApps? = null
    private var userHandle: UserHandle = UserHandle.of(UserHandle.myUserId())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keycode = arguments?.getInt(EXTRA_KEYCODE, -1) ?: -1
        addPreferencesFromResource(R.xml.touchscreen_gesture_app_picker)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Same timing as Columbus LaunchSettingsFragment: hierarchy is ready here.
        appListCategory = preferenceScreen.findPreference(KEY_APP_LIST)
        launcherApps = requireContext().getSystemService(LauncherApps::class.java)
        userHandle = UserHandle.of(ActivityManager.getCurrentUser())

        if (appListCategory == null) {
            Log.e(TAG, "App list PreferenceCategory missing — check touchscreen_gesture_app_picker.xml")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch { populateApps() }
    }

    override fun getMetricsCategory(): Int = MetricsEvent.LINEAGE

    override fun onRadioButtonClicked(emitter: SelectorWithWidgetPreference) {
        val key = emitter.key ?: return
        if (keycode < 0) return
        // Main activity: store component as both app and shortcut id (Columbus convention).
        TouchscreenGestureStore.setLaunchTarget(requireContext(), keycode, key, key)
        armGesture(requireContext(), keycode)
        updateCheckedState()
        activity?.finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SHORTCUT && resultCode == Activity.RESULT_OK) {
            activity?.finish()
        }
    }

    private suspend fun populateApps() {
        val category = appListCategory
        if (category == null) {
            Log.e(TAG, "populateApps: category null")
            return
        }
        val context = requireContext()

        val apps = withContext(Dispatchers.IO) { loadLauncherApps(context) }

        if (!isAdded) {
            return
        }

        withContext(Dispatchers.Main) {
            category.removeAll()
            val current = TouchscreenGestureStore.getApp(context, keycode)
            for (app in apps) {
                val pref = SelectorWithWidgetPreference(context).apply {
                    key = app.key
                    title = app.label
                    icon = app.icon
                    isPersistent = false
                    setOnClickListener(this@TouchscreenGestureAppPickerFragment)
                    isChecked = app.key == current
                    if (app.shortcuts.isNotEmpty()) {
                        setExtraWidgetOnClickListener { openShortcutPicker(app) }
                    } else {
                        setExtraWidgetOnClickListener(null)
                    }
                }
                category.addPreference(pref)
            }
        }
    }

    /**
     * Prefer LauncherApps (Columbus path); if that yields nothing, fall back to
     * queryIntentActivities which Settings has always used successfully for this feature.
     */
    private fun loadLauncherApps(context: Context): List<AppRow> {
        val launcherApps = this.launcherApps
        val shortcutsByPackage = if (launcherApps != null) {
            queryShortcutsByPackage(launcherApps)
        } else {
            emptyMap()
        }

        // --- Primary: LauncherApps.getActivityList (Columbus) ---
        if (launcherApps != null) {
            try {
                val activities = launcherApps.getActivityList(null, userHandle)
                if (activities.isNotEmpty()) {
                    return activities
                        .sortedBy { it.label?.toString().orEmpty().lowercase() }
                        .map { activity ->
                            val pkg = activity.componentName.packageName
                            AppRow(
                                key = activity.componentName.flattenToString(),
                                component = activity.componentName,
                                label = activity.label ?: pkg,
                                icon = try {
                                    activity.getIcon(context.resources.displayMetrics.densityDpi)
                                } catch (_: Exception) {
                                    null
                                },
                                shortcuts = ArrayList(shortcutsByPackage[pkg] ?: emptyList()),
                            )
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getActivityList failed", e)
            }
        }

        // --- Fallback: PackageManager MAIN/LAUNCHER (old TouchscreenGestureAppPicker) ---
        Log.w(TAG, "Falling back to queryIntentActivities")
        return loadViaPackageManager(context, shortcutsByPackage)
    }

    private fun loadViaPackageManager(
        context: Context,
        shortcutsByPackage: Map<String, List<ShortcutInfo>>,
    ): List<AppRow> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = try {
            pm.queryIntentActivitiesAsUser(intent, PackageManager.MATCH_ALL, userHandle.identifier)
        } catch (e: Exception) {
            Log.e(TAG, "queryIntentActivitiesAsUser failed", e)
            try {
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            } catch (e2: Exception) {
                Log.e(TAG, "queryIntentActivities failed", e2)
                emptyList()
            }
        }

        val collator = Collator.getInstance()
        return resolved
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                val label = info.loadLabel(pm) ?: activityInfo.packageName
                val icon = try {
                    info.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                AppRow(
                    key = component.flattenToString(),
                    component = component,
                    label = label,
                    icon = icon,
                    shortcuts = ArrayList(
                        shortcutsByPackage[activityInfo.packageName] ?: emptyList(),
                    ),
                )
            }
            .sortedWith { a, b -> collator.compare(a.label.toString(), b.label.toString()) }
    }

    private fun openShortcutPicker(app: AppRow) {
        val args = Bundle().apply {
            putInt(TouchscreenGestureShortcutPickerFragment.EXTRA_KEYCODE, keycode)
            putParcelable(TouchscreenGestureShortcutPickerFragment.EXTRA_COMPONENT, app.component)
            putParcelableArrayList(
                TouchscreenGestureShortcutPickerFragment.EXTRA_SHORTCUTS,
                app.shortcuts,
            )
        }
        SubSettingLauncher(requireContext())
            .setDestination(TouchscreenGestureShortcutPickerFragment::class.java.name)
            .setSourceMetricsCategory(metricsCategory)
            .setTitleText(app.label)
            .setArguments(args)
            .setResultListener(this, REQUEST_SHORTCUT)
            .launch()
    }

    private fun updateCheckedState() {
        val category = appListCategory ?: return
        val current = TouchscreenGestureStore.getApp(requireContext(), keycode)
        for (i in 0 until category.preferenceCount) {
            val pref = category.getPreference(i)
            if (pref is SelectorWithWidgetPreference) {
                pref.isChecked = pref.key == current
            }
        }
    }

    private fun queryShortcutsByPackage(
        launcherApps: LauncherApps,
    ): Map<String, List<ShortcutInfo>> {
        return try {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
            val all = launcherApps.getShortcuts(query, userHandle) ?: emptyList()
            all.filterNotNull().groupBy { it.`package` }
        } catch (e: Exception) {
            Log.w(TAG, "getShortcuts failed", e)
            emptyMap()
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

    private data class AppRow(
        val key: String,
        val component: ComponentName,
        val label: CharSequence,
        val icon: Drawable?,
        val shortcuts: ArrayList<ShortcutInfo>,
    )

    companion object {
        private const val TAG = "TouchscreenGestureAppPicker"
        const val EXTRA_KEYCODE = "keycode"
        private const val KEY_APP_LIST = "touchscreen_gesture_app_list"
        private const val REQUEST_SHORTCUT = 1
    }
}
