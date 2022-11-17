
package com.android.settings.deviceinfo;

import android.os.SystemProperties;

public class VersionUtils {
    public static String getAlphaVersion(){
        return SystemProperties.get("org.alpha.version.display","");
    }
}
