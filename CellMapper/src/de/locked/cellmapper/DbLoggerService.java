package de.locked.cellmapper;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class DbLoggerService extends Service {
    private static final String LOG_TAG = DbLoggerService.class.getName();
    private Timer timer;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "start service");

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        startService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(LOG_TAG, "destroy service");
        timer.cancel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startService() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                getUpdateLocation();
            }
        };
        
        int currentRate = getUpdateInterval();
        timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 0, currentRate * 1000);
        Log.i(LOG_TAG, "Timer started");
    }

    protected void getUpdateLocation() {
        Log.i(LOG_TAG, "update location");
    }

    private int getUpdateInterval() {
        String updateIntervalString = preferences.getString(Preferences.update_interval, "300"); // s
        return Integer.parseInt(updateIntervalString);
    }

}
