package de.locked.cellmapper.model;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Service;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

public class Utils {
    private static final String LOG_TAG = Utils.class.getName();

    private Utils() {
    };

    /**
     * checks if the service is running
     * 
     * @param context
     * @param serviceClass
     * @return true if the service is running
     */
    public static boolean isServiceRunning(Context context, Class<? extends Service> serviceClass) {
        String name  = serviceClass.getName();
        ActivityManager am = (ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            Log.i(LOG_TAG, service.service.getClassName());
            if (service.service.getClassName().equals(name)) {
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
