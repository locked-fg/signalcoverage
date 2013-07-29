package de.locked.cellmapper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start service");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        dataListener = new DataListener(this);

        // restart on preference change
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                restart();
            }
        });

        restart();
    }

    /**
     * We want this service to continue running until it is explicitly stopped, so return sticky.
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
        removeListener();
        if (runner != null) {
            runner.interrupt();
        }
        runner = null;
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
        stop();
        loadPreferences();

        // this is the main thread
        runner = new Thread() {

            @Override
            public void run() {
                setName("LoggerServiceThread");
                try {
                    Looper.prepare();
                    while (!isInterrupted()) {
                        Log.i(LOG_TAG, "start location listening");
                        addListener();
                        sleep(updateDuration);

                        Log.d(LOG_TAG, "wait for next iteration in " + sleepBetweenMeasures + "ms and disable updates");
                        removeListener();
                        sleep(sleepBetweenMeasures);
                    }
                    Looper.loop();
                    Looper.myLooper().quit();
                } catch (InterruptedException e) {
                    Log.i(LOG_TAG, "location thread interrupted");
                    removeListener();
                }
            }
        };
        runner.start();
    }

    /**
     * Listen updates on location and signal provider
     */
    private void addListener() {
        Log.i(LOG_TAG, "add listeners");
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minLocationTime, minLocationDistance, dataListener);
    }

    private void removeListener() {
        Log.i(LOG_TAG, "remove listeners");
        locationManager.removeUpdates(dataListener);
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
    }
}
