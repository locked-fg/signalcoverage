package de.locked.cellmapper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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

    private DataListener dataListener;
    private Thread runner;

    private Location lastLocation = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start service");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        dataListener = new DataListener(this);

        // restart on preference change
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(
                new OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                        restart();
                    }
                });

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

    private void loadPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        sleepBetweenMeasures = Preferences.getAsLong(preferences, Preferences.sleep_between_measures, 30) * 1000l;
        updateDuration = Preferences.getAsLong(preferences, Preferences.update_duration, 30) * 1000l;

        minLocationTime = Preferences.getAsLong(preferences, Preferences.min_location_time, 60) * 1000l;
        minLocationDistance = Preferences.getAsLong(preferences, Preferences.min_location_distance, 50);

        // ensure a minimum value
        minLocationTime = Math.max(minLocationTime, 1000);
    }

    private synchronized void restart() {
        loadPreferences();

        // don't start twice
        stop();

        // this is the main thread
        runner = new Thread() {

            @Override
            public void run() {
                setName("LoggerServiceThread");
                try {
                    Looper.prepare();
                    // Looper.loop();
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
        Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        long limit = System.currentTimeMillis() - maxLocationAge;
        long maxLocationAgeSec = maxLocationAge / 1000;

        float accNetwork = network.getAccuracy();
        float accGps = gps.getAccuracy();

        // filter too old locations
        if (network != null && network.getTime() < limit) {
            long age = (System.currentTimeMillis() - network.getTime()) / 1000;
            Log.d(LOG_TAG, "reject network location. Age: " + age + "s. Max: " + maxLocationAgeSec + "s");
            network = null;
            accNetwork = Float.MAX_VALUE;
        }
        if (gps != null && gps.getTime() < limit) {
            long age = (System.currentTimeMillis() - gps.getTime()) / 1000;
            Log.d(LOG_TAG, "reject gps location. Age: " + age + "s. Max: " + maxLocationAgeSec + "s");
            gps = null;
            accGps = Float.MAX_VALUE;
        }

        if (gps == null && network == null) {
            Log.d(LOG_TAG, "both locations rejected");
            return null;
        }

        // choose the more accurate one
        Location location = accNetwork < accGps ? network : gps;

        // check distance
        if (minLocationDistance > 0 && lastLocation != null && location.distanceTo(lastLocation) < minLocationDistance) {
            Log.d(LOG_TAG,
                    "location rejected due to distance limit. Distance to last location: "
                            + location.distanceTo(lastLocation) + "m");
            return null;
        }

        lastLocation = location;
        return location;
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
