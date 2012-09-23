package de.locked.cellmapper.model;

import android.app.Activity;
import android.content.Context;
import android.provider.Settings;
import android.view.WindowManager;

public class Utilities {
    /**
     * Gets the state of Airplane Mode.
     * 
     * @param context
     * @return true if enabled.
     */
    public static boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;

    }

    public static String rpad(String s, int l, String str) {
        while (s.length() < l) {
            s += str;
        }
        return s;
    }

    public static void setKeepScreenOn(Activity activity, boolean keepScreenOn) {
        if (keepScreenOn) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
