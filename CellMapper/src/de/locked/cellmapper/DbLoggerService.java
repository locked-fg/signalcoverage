package de.locked.cellmapper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import de.locked.cellmapper.model.DataListener;
import de.locked.cellmapper.model.Preferences;

public class DbLoggerService extends Service {
    private static final String LOG_TAG = DbLoggerService.class.getName();

    // parameter passed to location listener to get an update every this many
    // meters
    private final float LOCATION_DISTANCE_INTERVAL = 50; // m
    // parameter passed to location listener to get an update every this many
    // seconds
    private final long LOCATION_TIME_INTERVAL = 3000; // ms

    // keep the location lister that long active before unregistering again
    // thanx htc Desire + cyanogen mod
    private final long MEASURE_DURATION = 30000; // ms

    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    boolean useGPS = true;

    private SharedPreferences preferences;
    private DataListener dataListener;
    private Thread runner;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start service");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        dataListener = new DataListener(getApplicationContext());

        // restart on preference change
        preferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {

            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.i(LOG_TAG, "preferences changed - restarting");
                useGPS = preferences.getBoolean(Preferences.use_gps, true);
                stop();
                start();
            }

        });

        start();
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
        stop();
    }

    private void stop() {
        runner.interrupt();
        runner = null;
        removeListener();
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

    private void start() {
        // Register the listener with the Location Manager
        final long updateInterval = getUpdateInterval();
        Log.i(LOG_TAG, "update interval: " + updateInterval + "ms");

        // sanity check
        if (updateInterval - MEASURE_DURATION <= 0) {
            throw new IllegalArgumentException("update interval too small " + updateInterval + " vs "
                    + MEASURE_DURATION);
        }

        runner = new Thread() {

            @Override
            public void run() {
                try {
                    Looper.prepare();
                    while (!isInterrupted()) {
                        Log.i(LOG_TAG, "start location iteration");
                        addListener();

                        // set asleep and get some location information
                        Log.i(LOG_TAG, "wait " + MEASURE_DURATION + "ms for location updates");
                        sleep(MEASURE_DURATION);
                        updateLocation();
                        removeListener();

                        // set asleep and wait for the next iteration
                        Log.i(LOG_TAG, "wait for next iteration in " + (updateInterval - MEASURE_DURATION) + "ms");
                        sleep(updateInterval - MEASURE_DURATION);
                    }
                    Looper.loop();
                } catch (InterruptedException e) {
                    Log.i(LOG_TAG, "interrupting location thread");
                }
            }
        };
        runner.start();

        // LocationManager locationManager = (LocationManager)
        // getSystemService(Context.LOCATION_SERVICE);
        // Log.i(LOG_TAG, "request updates every " + updateInterval + "ms / " +
        // MIN_LOCATION_DISTANCE + "m");
        //
        // if (useGPS) {
        // locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
        // updateInterval, MIN_LOCATION_DISTANCE,
        // dataListener);
        // }
        // locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
        // updateInterval, MIN_LOCATION_DISTANCE,
        // dataListener);
    }

    private void updateLocation() {
        // bypass the listener
        Location location;
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        dataListener.onLocationChanged(location);

        if (useGPS) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            dataListener.onLocationChanged(location);
        }
    }

    private void addListener() {
        Log.i(LOG_TAG, "add listeners");

        // initPhoneState listener
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                | PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                | PhoneStateListener.LISTEN_SERVICE_STATE);

        // init location listeners
        boolean useGPS = preferences.getBoolean(Preferences.use_gps, true);
        if (useGPS) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_TIME_INTERVAL,
                    LOCATION_DISTANCE_INTERVAL, dataListener);
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_TIME_INTERVAL,
                LOCATION_DISTANCE_INTERVAL, dataListener);
    }

    private void removeListener() {
        Log.i(LOG_TAG, "remove listeners");

        // unregister location
        locationManager.removeUpdates(dataListener);
        // unregister telephone
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
    }

}
