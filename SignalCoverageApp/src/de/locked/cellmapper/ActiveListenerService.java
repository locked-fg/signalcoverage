package de.locked.cellmapper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import de.locked.cellmapper.model.DataListener;
import de.locked.cellmapper.model.Preferences;

public class ActiveListenerService extends Service implements OnSharedPreferenceChangeListener {
    private static final String LOG_TAG = ActiveListenerService.class.getName();
    private static final int START_LISTENING = 0;
    private static final int MIN_TIME = 150; // ms - Minimum for minLocationTime
    private final SignalChangeTrigger trigger = new SignalChangeTrigger();
    // get an update every this many meters (min distance)
    private long minLocationDistance = 5; // m
    // get an update every this many milliseconds
    private long minLocationTime = 5000; // ms
    // keep the location lister that long active before unregistering again
    // thanks htc Desire + cyanogen mod
    private long sleepBetweenMeasures = 30000; // ms
    private long updateDuration = 30000; // ms
    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    private volatile boolean reschedule;
    private DataListener dataListener;
    private Handler handler;
    private Thread pollingThread;
    private boolean updateOnSignalChange;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start service");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        dataListener = DataListener.getInstance(this);
        //
        preferences.registerOnSharedPreferenceChangeListener(this);
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        loadPreferences();

        handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case START_LISTENING:
                        startListening();
                        break;

                    default:
                        Log.e(LOG_TAG, "this was unexpected better stop");
                        stopListening();
                        break;
                }
            }
        };
    }

    private void stopListening() {
        Log.d(LOG_TAG, "stopListening()");
        removeListener();

        if (pollingThread != null && pollingThread.isAlive()) {
            Log.d(LOG_TAG, "interrupting pollingThread");
            pollingThread.interrupt();
        }
        if (reschedule) {
            Log.d(LOG_TAG, "reschedule to start listening in " + sleepBetweenMeasures + "ms");
            handler.sendEmptyMessageDelayed(START_LISTENING, sleepBetweenMeasures);
        }
    }

    private void startListening() {
        if (!reschedule) {
            Log.d(LOG_TAG, "reschedule = false. do nothing.");
            return;
        }
        if (pollingThread != null && pollingThread.isAlive()) {
            Log.w(LOG_TAG, "Thread still active. Do nothing. - This shouldn't happen!");
            return;
        }

        addListener();

        Log.d(LOG_TAG, "starting location polling thread");
        pollingThread = new Thread() {
            private final String LOG = LOG_TAG + "#Thread";
            private final long maxLocationAge = 5 * 60 * 1000; // 5min
            private final long startTime = System.currentTimeMillis();

            @Override
            public void run() {
                try {
                    long threadAge = 0;
                    while (!isInterrupted() && reschedule && threadAge < updateDuration) {
                        Log.i(LOG, "poll location");
                        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, dataListener, getMainLooper());

                        // are the following 2 lines REALLY necessary?
                        // Could be superseded by the requestSingleUpdate() call
                        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        dataListener.onLocationChanged(location);

                        sleep(minLocationTime);
                        threadAge = System.currentTimeMillis() - startTime;
                    }

                    Log.d(LOG, "thread age: " + threadAge + "ms reached - stopping self and reschedule in " + sleepBetweenMeasures + "ms");
                    handler.sendEmptyMessageDelayed(START_LISTENING, sleepBetweenMeasures);
                } catch (InterruptedException e) {
                    Log.i(LOG, "thread interrupted");
                } finally {
                    removeListener();
                }
            }
        };
        pollingThread.start();
    }

    private void loadPreferences() {
        Log.i(LOG_TAG, "(re)load preferences");
        sleepBetweenMeasures = Preferences.getAsLong(preferences, Preferences.sleep_between_measures, 10) * 1000l;
        updateDuration = Preferences.getAsLong(preferences, Preferences.update_duration, 30) * 1000l;

        minLocationTime = Preferences.getAsLong(preferences, Preferences.min_location_time, 60) * 1000l;
        minLocationDistance = Preferences.getAsLong(preferences, Preferences.min_location_distance, 50);

        updateOnSignalChange = preferences.getBoolean(Preferences.updateOnSignalChange, true);

        // ensure a minimum value
        minLocationTime = Math.max(minLocationTime, MIN_TIME);
    }

    private void addListener() {
        Log.i(LOG_TAG, "add listeners. minTime: " + minLocationTime + " / min dist: " + minLocationDistance);
        if (updateOnSignalChange) {
            telephonyManager.listen(trigger, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minLocationTime, minLocationDistance, dataListener);
        locationManager.addGpsStatusListener(dataListener);
    }

    private void removeListener() {
        Log.i(LOG_TAG, "remove listeners");
        telephonyManager.listen(trigger, PhoneStateListener.LISTEN_NONE);
        locationManager.removeUpdates(dataListener);
        locationManager.removeGpsStatusListener(dataListener);
    }

    /**
     * We want this service to continue running until it is explicitly stopped, so return sticky.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        reschedule = true;
        handler.sendEmptyMessage(START_LISTENING);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "destroy");
        reschedule = false;
        handler.removeMessages(START_LISTENING);
        stopListening();
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        loadPreferences();
        stopListening();
        startListening();
    }

    /**
     * Listener that triggers a measurement when the signal strength changes.
     */
    class SignalChangeTrigger extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, dataListener, getMainLooper());
        }
    }
}
