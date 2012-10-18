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
    private float minLocationDistance = 50; // m
    // parameter passed to location listener to get an update every this many
    // milliseconds
    private long minLocationTime = 5000; // ms

    // keep the location lister that long active before unregistering again
    // thanx htc Desire + cyanogen mod
    private long sleepBetweenMeasures = 30000; // ms
    private long sleepForMeasures = 30000; // ms

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

                sleepBetweenMeasures = getAsLong(Preferences.sleep_between_measures, "30") * 1000l;
                sleepForMeasures = getAsLong(Preferences.update_duration, "30") * 1000l;

                minLocationTime = getAsLong(Preferences.min_location_time, "60") * 1000l;
                minLocationDistance = getAsLong(Preferences.min_location_distance, "50");

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
        runner = new Thread() {

            @Override
            public void run() {
                try {
                    Looper.prepare();
                    while (!isInterrupted()) {
                        Log.i(LOG_TAG, "start location listening");
                        addListener();

                        // set asleep and get some location information
                        Log.i(LOG_TAG, "wait " + sleepForMeasures + "ms for location updates");
                        sleep(sleepForMeasures);

                        Log.i(LOG_TAG, "force location updates");
                        forceUpdateLocation();

                        // set asleep and wait for the next iteration
                        Log.i(LOG_TAG, "wait for next iteration in " + sleepBetweenMeasures + "ms and disable updates");
                        removeListener();
                        sleep(sleepBetweenMeasures);
                    }
                    Looper.loop();
                } catch (InterruptedException e) {
                    Log.i(LOG_TAG, "location thread interrupted");
                }
            }
        };
        runner.start();
    }

    private void forceUpdateLocation() {
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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minLocationTime, minLocationDistance,
                    dataListener);
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minLocationTime, minLocationDistance,
                dataListener);
    }

    private void removeListener() {
        Log.i(LOG_TAG, "remove listeners");

        // unregister location
        locationManager.removeUpdates(dataListener);
        // unregister telephone
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
    }
}
