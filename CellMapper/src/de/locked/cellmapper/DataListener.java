package de.locked.cellmapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.util.Log;

public class DataListener extends PhoneStateListener implements LocationListener {
    public static final String LOG_TAG = DataListener.class.getName();
    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private static final SimpleDateFormat sdf = new SimpleDateFormat("y-MM-dd HH:mm:ss");
    private final Context context;
    private final SQLiteDatabase db;

    private SignalStrength signal;

    public DataListener(Context context) {
        this.context = context;
        this.db = context.openOrCreateDatabase(DB_NAME, Activity.MODE_PRIVATE, null);;
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