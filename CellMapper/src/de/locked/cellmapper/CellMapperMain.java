package de.locked.cellmapper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class CellMapperMain extends Activity {
    private static final String LOG_TAG = CellMapperMain.class.getName();

    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private static final long MIN_LOCATION_DISTANCE = 100; // m
    // private static final SimpleDateFormat sdf = new
    // SimpleDateFormat("y-MM-dd HH:mm:ss");

    private SQLiteDatabase db;
    private DataListener dataListener;
    private SharedPreferences preferences;
    private Thread refresher;
    private Handler handler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        handler = new Handler();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        db = openOrCreateDatabase(DB_NAME, MODE_PRIVATE, null);
        setupDB();
        dataListener = new DataListener(this, db);

        // set defaults once
        PreferenceManager.setDefaultValues(this, R.xml.config, false);

        final ToggleButton onOff = (ToggleButton) findViewById(R.id.onOffButton);
        onOff.setChecked(true);
        onOff.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!onOff.isChecked()) {
                    Log.i(LOG_TAG, "stopping");
                    stop();
                } else {
                    Log.i(LOG_TAG, "starting");
                    start();
                }
            }
        });

        start();
    }

    private void initPhoneListener() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_CELL_LOCATION
                | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | PhoneStateListener.LISTEN_SERVICE_STATE);
    }

    private void initLocationListener() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(dataListener);

        // Register the listener with the Location Manager
        String updateIntervalString = preferences.getString(Preferences.update_interval, "300");
        int updateInterval = 300;
        try {
            updateInterval = Integer.parseInt(updateIntervalString);
        } catch (Exception e) {
            Log.w(LOG_TAG, e.toString());
        }
        boolean useGPS = preferences.getBoolean(Preferences.use_gps, true);
        if (useGPS) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, updateInterval, MIN_LOCATION_DISTANCE,
                    dataListener);
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, updateInterval, MIN_LOCATION_DISTANCE,
                dataListener);
    }

    private void setupDB() {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + "(" + //
                // location
                " time INT PRIMARY KEY, " + //
                " accuracy REAL, " + //
                " altitude REAL, " + //
                " satellites INT, " + //
                " latitude REAL, " + //
                " longitude REAL," + //
                " speed REAL, " + //
                // signal
                " cdmaDbm INT, " + //
                " evdoDbm INT, " + //
                " evdoSnr INT, " + //
                " signalStrength INT " + //
                " );");
    }

    @Override
    protected void onResume() {
        super.onResume();

        stop();
        start();
    }

    protected void start() {
        initLocationListener();
        initPhoneListener();

        // refresh();
        refresher = new Thread() {
            @Override
            public void run() {
                try {
                    while (!interrupted()) {
                        refresh();
                        sleep(5000); // all 5s
                    }
                } catch (Exception e) {
                    Log.i(LOG_TAG, "interrupted refresh thread");
                }
            }

        };
        refresher.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        // db.close();
    }

    private void stop() {
        // unregister location
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(dataListener);

        // unregister telephone
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(dataListener, PhoneStateListener.LISTEN_NONE);

        if (refresher != null) {
            refresher.interrupt();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_settings:
            startActivity(new Intent(this, ConfigActivity.class));
            return true;
        }
        return false;
    }

    private void refresh() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getLastEntryString());
        sb.append("\n------\n");

        Cursor cursor = db.rawQuery("SELECT *  FROM " + TABLE + " ORDER BY time DESC LIMIT 1", null);
        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String columnName = cursor.getColumnName(i);
                String value = cursor.getString(i);

                sb.append(Utilities.rpad(columnName + ":", 16, " "));
                sb.append(value);
                sb.append("\n");
            }
        }
        cursor.close();

        // update UI

        handler.post(new Runnable() {

            @Override
            public void run() {
                TextView text = (TextView) findViewById(R.id.textfield);
                text.setText(sb.toString());
            }
        });
    }

    private String getLastEntryString() {
        Cursor cursor = db.rawQuery("SELECT datetime(time, 'unixepoch', 'localtime') AS LastEntry FROM " + TABLE
                + " ORDER BY time DESC LIMIT 1", null);

        String result = "";
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();

        return result;
    }

}
