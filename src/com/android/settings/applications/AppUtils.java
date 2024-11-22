package com.android.settings.applications;

import android.os.SystemProperties;

public class AppUtils {

    private static final String NEXUS_PROP = "persist.sys.nexuslauncher";
    private static final String LAWN_PROP = "persist.sys.lawnchair";

    public static boolean isQuickSwitchAvailable() {
        return isNexusLauncherAvailable() || isLawnchairAvailable();
    }

    public static boolean isNexusLauncherAvailable() {
        return SystemProperties.getInt(NEXUS_PROP, 0) == 1;
    }

    public static boolean isLawnchairAvailable() {
        return SystemProperties.getInt(LAWN_PROP, 0) == 1;
    }
}
