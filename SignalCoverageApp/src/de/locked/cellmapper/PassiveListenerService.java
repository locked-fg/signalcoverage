package de.locked.cellmapper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import de.locked.cellmapper.model.DataListener;

public class PassiveListenerService extends Service {
    private static final String LOG_TAG = PassiveListenerService.class.getName();
    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    private DataListener dataListener;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start passive service");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        dataListener = new DataListener(this);

        addListener();
    }

    /**
     * We want this service to continue running until it is explicitly stopped, so return sticky.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeListener();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void addListener() {
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, dataListener);
    }

    private void removeListener() {
        telephonyManager.listen(dataListener, PhoneStateListener.LISTEN_NONE);
        locationManager.removeUpdates(dataListener);
    }
}
