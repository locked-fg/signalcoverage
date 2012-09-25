package de.locked.cellmapper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import de.locked.cellmapper.model.DataListener;
import de.locked.cellmapper.model.Preferences;

public class DbLoggerService extends Service {
    private static final String LOG_TAG = DbLoggerService.class.getName();
    private final float MIN_LOCATION_DISTANCE = 300; // m

    private SharedPreferences preferences;
    private DataListener dataListener;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start service");

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        dataListener = new DataListener(getApplicationContext());

        initListener();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        removeLocationListener();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * return interval in ms
     * 
     * @return
     */
    private long getUpdateInterval() {
        String updateIntervalString = preferences.getString(Preferences.update_interval, "300"); // s
        int interval = 300;
        try {
            interval = Integer.parseInt(updateIntervalString);
        } catch (Exception e) {
        }
        return interval * 1000l; // s -> ms!
    }

    private void initListener() {
        // initPhoneState listener
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_CELL_LOCATION
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | PhoneStateListener.LISTEN_SERVICE_STATE);

        // Register the listener with the Location Manager
        long updateInterval = getUpdateInterval();
        boolean useGPS = preferences.getBoolean(Preferences.use_gps, true);
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Log.i(LOG_TAG, "request updates every " + updateInterval + "ms / " + MIN_LOCATION_DISTANCE + "m");

        if (useGPS) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, updateInterval, MIN_LOCATION_DISTANCE,
                    dataListener);
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, updateInterval, MIN_LOCATION_DISTANCE,
                dataListener);
    }

    private void removeLocationListener() {
        // unregister location
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(dataListener);

        // unregister telephone
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(dataListener, PhoneStateListener.LISTEN_NONE);
    }

}
