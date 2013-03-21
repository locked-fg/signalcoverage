package de.locked.cellmapper.model;

import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.provider.Settings;

public class Utils {
    private Utils() {
    };

    /**
     * checks if the service is running
     * 
     * @param context
     * @param serviceClass
     * @return true if the service is running
     */
    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> l = am.getRunningServices(100);
        Iterator<ActivityManager.RunningServiceInfo> i = l.iterator();
        while (i.hasNext()) {
            ActivityManager.RunningServiceInfo runningServiceInfo = i.next();
            Class<?> sClass = runningServiceInfo.service.getClass();

            if (sClass.equals(serviceClass)) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Gets the state of Airplane Mode.
     * 
     * @return true if enabled.
     */
    public static boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
}
