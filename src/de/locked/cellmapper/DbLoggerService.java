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

    // max location age
    private final long maxLocationAge = DbHandler.ALLOWED_TIME_DRIFT;
    // get an update every this many meters (min distance)
    private long minLocationDistance = 50; // m
    // get an update every this many milliseconds
    private long minLocationTime = 5000; // ms

    // keep the location lister that long active before unregistering again
    // thanx htc Desire + cyanogen mod
    private long sleepBetweenMeasures = 30000; // ms
    private long updateDuration = 30000; // ms

    private LocationManager locationManager;
    private TelephonyManager telephonyManager;

    private SharedPreferences preferences;
    private DataListener dataListener;
    private Thread runner;

    private Location lastLocation = null;

    class PreferenceLoader implements SharedPreferences.OnSharedPreferenceChangeListener {

        /**
         * restart the process whenever the preferences have changed
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(LOG_TAG, "preferences changed - restarting");

            sleepBetweenMeasures =  Preferences.getAsLong(preferences, Preferences.sleep_between_measures, 30)*1000l;
            updateDuration = Preferences.getAsLong(preferences, Preferences.update_duration, 30) * 1000l;

            minLocationTime = Preferences.getAsLong(preferences, Preferences.min_location_time, 60) * 1000l;
            minLocationDistance = Preferences.getAsLong(preferences, Preferences.min_location_distance, 50);

            // ensure a minimum value
            minLocationTime = Math.max(minLocationTime, 1000);

            restart();
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

        restart();
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

    private synchronized void restart() {
        // don't start twice
        stop();

        // this is the main thread
        runner = new Thread() {

            @Override
            public void run() {
                setName("LoggerServiceThread");
                try {
                    Looper.prepare();
//                    Looper.loop();
                    while (!isInterrupted()) {
                        Log.i(LOG_TAG, "start location listening");
                        addListener();

                        // request location updates for a period of
                        // 'updateDuration'ms
                        final long stopPeriod = System.currentTimeMillis() + updateDuration;
                        while (System.currentTimeMillis() < stopPeriod) {
                            Log.d(LOG_TAG, "request location update");
                            dataListener.onLocationChanged(getLocation());
                            sleep(minLocationTime);
                        }

                        // set asleep and wait for the next measurement period
                        Log.d(LOG_TAG, "wait for next iteration in " + sleepBetweenMeasures + "ms and disable updates");
                        removeListener();
                        sleep(sleepBetweenMeasures);
                    }
                } catch (InterruptedException e) {
                    Log.i(LOG_TAG, "location thread interrupted");
                    removeListener();
                } finally {
                    Looper.myLooper().quit();
                }
            }
        };
        runner.start();
    }

    private Location getLocation() {
        Location locationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location locationGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        final long maxLocationAgeSec = maxLocationAge / 1000;

        // select the more recent one
        final long limit = System.currentTimeMillis() - maxLocationAge;
        if (locationNetwork != null && locationNetwork.getTime() < limit) {
            long age = (System.currentTimeMillis() - locationNetwork.getTime()) / 1000;
            Log.d(LOG_TAG, "reject network location. Age: " + age + "s. Max: " + maxLocationAgeSec + "s");
            locationNetwork = null;
        }
        if (locationGps != null && locationGps.getTime() < limit) {
            long age = (System.currentTimeMillis() - locationGps.getTime()) / 1000;
            Log.d(LOG_TAG, "reject gps location. Age: " + age + "s. Max: " + maxLocationAgeSec + "s");
            locationGps = null;
        }

        if (locationGps == null && locationNetwork == null) {
            Log.d(LOG_TAG, "both locations rejected");
            return null;
        }

        // ONE is not null now, set to Float Max if null
        float accNetwork = locationNetwork == null ? Float.MAX_VALUE : locationNetwork.getAccuracy();
        float accGps = locationGps == null ? Float.MAX_VALUE : locationGps.getAccuracy();

        // choose the more accurate one
        Location newLocation = accNetwork < accGps ? locationNetwork : locationGps;

        // check distance
        if (minLocationDistance > 0 && lastLocation != null
                && newLocation.distanceTo(lastLocation) < minLocationDistance) {
            float dist = newLocation.distanceTo(lastLocation);
            Log.d(LOG_TAG, "location rejected due to distance limit. Distance to last location: " + dist + "m");
            return null;
        }

        lastLocation = newLocation;
        return newLocation;
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
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minLocationTime, minLocationDistance,
                dataListener);
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minLocationTime,
                    minLocationDistance, dataListener);
        } catch (IllegalArgumentException iae) {
            Log.w(LOG_TAG, "Network provider is unavailable?! This seems to be an issue with the emulator", iae);
        }
    }

    private void removeListener() {
        Log.i(LOG_TAG, "remove listeners");
        locationManager.removeUpdates(dataListener);
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
    }
}
