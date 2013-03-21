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
import de.locked.cellmapper.model.DbHandler;
import de.locked.cellmapper.model.Preferences;

public class DbLoggerService extends Service {
    private static final String LOG_TAG = DbLoggerService.class.getName();

    // get an update every this many meters
    private float minLocationDistance = 50; // m
    // get an update every this many milliseconds
    private long minLocationTime = 5000; // ms

    // keep the location lister that long active before unregistering again
    // thanx htc Desire + cyanogen mod
    private long sleepBetweenMeasures = 30000; // ms
    private long updateDuration = 30000; // ms

    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    boolean useGPS = true;

    private SharedPreferences preferences;
    private DataListener dataListener;
    private Thread runner;

    class PreferenceLoader implements SharedPreferences.OnSharedPreferenceChangeListener {

        /**
         * restart the process ehenever the preferences have changed
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(LOG_TAG, "preferences changed - restarting");
            useGPS = preferences.getBoolean(Preferences.use_gps, true);

            sleepBetweenMeasures = getAsLong(Preferences.sleep_between_measures, "30") * 1000l;
            updateDuration = getAsLong(Preferences.update_duration, "30") * 1000l;

            minLocationTime = getAsLong(Preferences.min_location_time, "60") * 1000l;
            minLocationDistance = getAsLong(Preferences.min_location_distance, "50");

            // ensure a minimum value
            if (minLocationTime <= 1000) {
                minLocationTime = 1000;
            }

            stop();
            start();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start service");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        dataListener = new DataListener(getApplicationContext());

        // restart on preference change
        PreferenceLoader loader = new PreferenceLoader();
        preferences.registerOnSharedPreferenceChangeListener(loader);
        loader.onSharedPreferenceChanged(null, null);
    }

    /**
     * We want this service to continue running until it is explicitly stopped,
     * so return sticky.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
    }

    private void stop() {
        if (runner != null) {
            runner.interrupt();
        }
        runner = null;
        removeListener();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private long getAsLong(String key, String def) {
        String value = preferences.getString(key, def); // s
        long interval = 0;
        try {
            interval = Long.parseLong(value);
        } catch (Exception e) {
            Log.e(LOG_TAG, "value could not be parsed to long: " + key + " > " + value);
        }
        return interval;
    }

    private void start() {
        // this is the main thread
        runner = new Thread() {

            @Override
            public void run() {
                try {
                    Looper.prepare();
                    while (!isInterrupted()) {
                        Log.i(LOG_TAG, "start location listening");
                        addListener();

                        // get initial location
                        dataListener.onLocationChanged(getLocation());

                        // now wait for location updates
                        Log.d(LOG_TAG, "wait " + updateDuration + "ms for updates");
                        sleep(updateDuration);

                        // set asleep and wait for the next iteration
                        Log.d(LOG_TAG, "wait for next iteration in " + sleepBetweenMeasures + "ms and disable updates");
                        removeListener();
                        sleep(sleepBetweenMeasures);
                    }
                    Looper.loop();
                } catch (InterruptedException e) {
                    Log.i(LOG_TAG, "location thread interrupted");
                    removeListener();
                }
            }
        };
        runner.start();
    }

    private Location getLocation() {
        Location locationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location locationGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        // too old locations are rejected later in the DbHandler
        final long limit = System.currentTimeMillis() - DbHandler.ALLOWED_TIME_DRIFT;
        if (locationNetwork != null && locationNetwork.getTime() < limit) {
            locationNetwork = null;
        }
        if (locationGps != null && locationGps.getTime() < limit) {
            locationGps = null;
        }

        // both can be null
        if (locationGps == null && locationNetwork == null) {
            return null;
        }

        // ONE is not null now
        float accNetwork = locationNetwork == null ? Float.MAX_VALUE : locationNetwork.getAccuracy();
        float accGps = locationGps == null ? Float.MAX_VALUE : locationGps.getAccuracy();

        return accNetwork < accGps ? locationNetwork : locationGps;
    }

    /**
     * Listen updates on location and signal provider
     */
    private void addListener() {
        removeListener();

        Log.i(LOG_TAG, "add listeners");

        // initPhoneState listener
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                | PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                | PhoneStateListener.LISTEN_SERVICE_STATE);

        // init location listeners
        if (getGpsEnabled()) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minLocationTime, minLocationDistance,
                    dataListener);
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minLocationTime, minLocationDistance,
                dataListener);
    }

    private void removeListener() {
        Log.i(LOG_TAG, "remove listeners");
        locationManager.removeUpdates(dataListener);
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
    }
    
    /**
     * check if GPS is enabled. 
     * 
     * @return true if GPS is available
     */
    private boolean getGpsEnabled() {
        boolean useGPS = preferences.getBoolean(Preferences.use_gps, true);
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        return gpsEnabled && useGPS;
    }
}
