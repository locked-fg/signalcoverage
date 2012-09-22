package de.locked.cellmapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.util.Log;

public class DataListener extends PhoneStateListener implements LocationListener {
    public static final String LOG_TAG = DataListener.class.getName();
    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private static final SimpleDateFormat sdf = new SimpleDateFormat("y-MM-dd HH:mm:ss");
    private final SharedPreferences preferences;
    private final Context context;
    private final SQLiteDatabase db;

    private SignalStrength signal;

    public DataListener(Context context) {
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        db = context.openOrCreateDatabase(DB_NAME, Activity.MODE_PRIVATE, null);
        setupDB();
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
    public void onLocationChanged(Location location) {
        Log.d(LOG_TAG, "loction update received");

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        int satellites = count(gpsStatus);

        save(new Data(location, signal, gpsStatus, satellites));
    }

    private int count(GpsStatus gpsStatus) {
        if (gpsStatus == null)
            return 0;
        int i = 0;
        Iterator<GpsSatellite> it = gpsStatus.getSatellites().iterator();
        while (it.hasNext()) {
            i++;
        }
        return i;
    }

    public void save(Data data) {
        if (data.location == null || data.signal == null) {
            return;
        }

        // all location stuff
        long time = data.location.getTime();
        int timeSec = (int) (data.location.getTime() / 1000);
        float accuracy = data.location.getAccuracy();
        double altitude = data.location.getAltitude();
        int satellites = data.satellites;
        double latitude = data.location.getLatitude();
        double longitude = data.location.getLongitude();
        float speed = data.location.getSpeed();

        // # sqlite3
        // /data/data/de.locked.cellmapper/databases/CellMapper
        // sqlite> select datetime(time, 'unixepoch', 'localtime')
        // FROM Base
        // ORDER BY TIME DESC LIMIT 4;
        db.beginTransaction();
        try {
            if (Utilities.isAirplaneModeOn(context)) {
                Log.i(LOG_TAG, "writing data to db (location) at time " + sdf.format(new Date(time)));
                db.execSQL("INSERT OR IGNORE INTO " + TABLE
                        + "(time, accuracy, altitude, satellites, latitude, longitude, speed) " + " VALUES " //
                        + String.format(Locale.US, //
                                "(%d, %f, %f, %d, %f, %f, %f)", //
                                timeSec, accuracy, altitude, satellites, latitude, longitude, speed) //
                );
            } else {
                int cdmaDbm = data.signal.getCdmaDbm();
                int evdoDbm = data.signal.getEvdoDbm();
                int evdoSnr = data.signal.getEvdoSnr();
                int signalStrength = data.signal.getGsmSignalStrength();

                Log.i(LOG_TAG, "writing data to db (location+signal) at time " + sdf.format(new Date(time)));
                db.execSQL("INSERT OR REPLACE INTO "
                        + TABLE
                        + "(time, accuracy, altitude, satellites, latitude, longitude, speed, cdmaDbm, evdoDbm, evdoSnr, signalStrength) "
                        + " VALUES " //
                        + String.format(
                                Locale.US, //
                                "(%d, %f, %f, %d, %f, %f, %f, %d, %d, %d, %d)", //
                                timeSec, accuracy, altitude, satellites, latitude, longitude, speed, cdmaDbm, evdoDbm,
                                evdoSnr, signalStrength) //
                );
            }
            Log.i(LOG_TAG, "transaction successfull");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        Log.d(LOG_TAG, "signal strength update received");
        this.signal = signalStrength;
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
}

class Data {
    final Location location;
    final SignalStrength signal;
    final GpsStatus gpsStatus;
    final int satellites;

    public Data(Location location, SignalStrength signal, GpsStatus gpsStatus, int satellites) {
        super();
        this.location = location;
        this.signal = signal;
        this.gpsStatus = gpsStatus;
        this.satellites = satellites;
    }
}


// // unregister location
// LocationManager locationManager = (LocationManager)
// getSystemService(Context.LOCATION_SERVICE);
// locationManager.removeUpdates(dataListener);
//
// // unregister telephone
// TelephonyManager tm = (TelephonyManager)
// getSystemService(Context.TELEPHONY_SERVICE);
// tm.listen(dataListener, PhoneStateListener.LISTEN_NONE);
//



// private void initPhoneListener() {
// TelephonyManager tm = (TelephonyManager)
// getSystemService(Context.TELEPHONY_SERVICE);
// tm.listen(dataListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
// PhoneStateListener.LISTEN_CELL_LOCATION
// | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE |
// PhoneStateListener.LISTEN_SERVICE_STATE);
// }
//
// private void initLocationListener() {
// LocationManager locationManager = (LocationManager)
// getSystemService(Context.LOCATION_SERVICE);
// locationManager.removeUpdates(dataListener);
//
// // Register the listener with the Location Manager
// String updateIntervalString =
// preferences.getString(Preferences.update_interval, "300");
// int updateInterval = 300;
// try {
// updateInterval = Integer.parseInt(updateIntervalString);
// } catch (Exception e) {
// Log.w(LOG_TAG, e.toString());
// }
// boolean useGPS = preferences.getBoolean(Preferences.use_gps, true);
// if (useGPS) {
// locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
// updateInterval, MIN_LOCATION_DISTANCE,
// dataListener);
// }
// locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
// updateInterval, MIN_LOCATION_DISTANCE,
// dataListener);
// }