package de.locked.cellmapper;

import android.content.Context;
import android.provider.Settings;

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
    
    public static  String rpad(String s, int l, String str) {
        while (s.length() < l) {
            s += str;
        }
        return s;
    }
    

}
