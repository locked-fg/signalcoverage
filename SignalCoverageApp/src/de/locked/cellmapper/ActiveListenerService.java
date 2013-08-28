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

public class ActiveListenerService extends Service {
    private static final String LOG_TAG = ActiveListenerService.class.getName();
    private static final int START_LISTENING = 0;
    private static final int MIN_TIME = 250;
    private final SignalChangeTrigger trigger = new SignalChangeTrigger();
    // get an update every this many meters (min distance)
    private long minLocationDistance = 50; // m
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
    private Thread thread;
    private boolean updateOnSignalChange;

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
                loadPreferences();
                stopListening();
                startListening();
            }
        });

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
        if (thread != null && thread.isAlive()) {
            Log.d(LOG_TAG, "interrupting thread");
            thread.interrupt();
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
        if (thread != null && thread.isAlive()) {
            Log.w(LOG_TAG, "Thread still active. do nothing. - This shouldn't happen!");
            return;
        }

        addListener();

        Log.d(LOG_TAG, "starting location polling thread");
        thread = new Thread() {
            private final String LOG = LOG_TAG + "#Thread";
            private final long maxLocationAge = 5 * 60 * 1000; // 5min
            private final long startTime = System.currentTimeMillis();

            @Override
            public void run() {
                try {
                    long threadAge = 0;
                    Location lastLocation = null;
                    while (!isInterrupted() && reschedule && threadAge < updateDuration) {
                        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, dataListener, getMainLooper());

                        // handle polling
                        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (location != null && !sameLocation(location, lastLocation)) {
                            long age = System.currentTimeMillis() - location.getTime();
                            float dist = dist(lastLocation, location);
                            Log.d(LOG, "location age: " + (age / 1000) + "s // dist: " + dist + "m // accuracy " + location.getAccuracy());
                            if (age < maxLocationAge && dist > minLocationDistance) {
                                dataListener.onLocationChanged(location);
                                lastLocation = location;
                            }
                        }

                        sleep(minLocationTime);
                        threadAge = System.currentTimeMillis() - startTime;
                    }

                    Log.d(LOG, "thread age " + threadAge + "ms reached - stopping self and reschedule in " + sleepBetweenMeasures + "ms");
                    handler.sendEmptyMessageDelayed(START_LISTENING, sleepBetweenMeasures);
                } catch (InterruptedException e) {
                    Log.i(LOG, "thread interrupted");
                } finally {
                    removeListener();
                }
            }

            private boolean sameLocation(Location a, Location b) {
                Log.d(LOG, "same location as before");
                return a != null && b != null && a.getTime() == b.getTime();
            }

            private float dist(Location a, Location b) {
                if (a == null || b == null) {
                    return Float.MAX_VALUE;
                } else {
                    return a.distanceTo(b);
                }
            }
        };
        thread.start();
    }

    /**
     * We want this service to continue running until it is explicitly stopped, so return sticky.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.reschedule = true;
        handler.sendEmptyMessage(START_LISTENING);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "destroy");
        stopListening();
        handler.removeMessages(START_LISTENING);
        this.reschedule = false;
        stopListening();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

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
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        if (updateOnSignalChange) {
            telephonyManager.listen(trigger, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minLocationTime, minLocationDistance, dataListener);
//        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, dataListener);
        locationManager.addGpsStatusListener(dataListener);
    }

    private void removeListener() {
        Log.i(LOG_TAG, "remove listeners");
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
        telephonyManager.listen(trigger, PhoneStateListener.LISTEN_NONE);
        locationManager.removeUpdates(dataListener);
        locationManager.removeGpsStatusListener(dataListener);
    }

    /**
     * Class that triggers a measurement when the signal strength changes
     */
    class SignalChangeTrigger extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, dataListener, getMainLooper());
        }
    }
}
