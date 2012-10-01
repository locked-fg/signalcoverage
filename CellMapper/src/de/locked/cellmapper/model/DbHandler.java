package de.locked.cellmapper.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import de.locked.cellmapper.model.DataListener.Data;

public class DbHandler {
    private static final int ALLOWED_TIME_DRIFT = 30000;
    public static final String LOG_TAG = DbHandler.class.getName();
    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private static final SimpleDateFormat sdf = new SimpleDateFormat("y-MM-dd HH:mm:ss");
    private static SQLiteDatabase db = null;

    private static void setupDB(Context context) {
        if (db == null || !db.isOpen()) {
            Log.i(LOG_TAG, "opening db");
            db = context.openOrCreateDatabase(DB_NAME, Activity.MODE_PRIVATE, null);
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
    }

    public static void close() {
        if (db != null && db.isOpen()) {
            Log.i(LOG_TAG, "closing DB");
            db.close();
            db = null;
        }
    }

    public static String getLastEntryString(Context context) {
        setupDB(context);
        Cursor cursor = db.rawQuery("SELECT datetime(time, 'unixepoch', 'localtime') AS LastEntry FROM " + TABLE
                + " ORDER BY time DESC LIMIT 1", null);

        String result = "";
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();

        return result;
    }

    public static void save(Data data, Context context) {
        if (data.location == null || data.signal == null) {
            return;
        }
        setupDB(context);

        // all location data
        long time = data.location.getTime();
        int timeSec = (int) (data.location.getTime() / 1000);
        float accuracy = data.location.getAccuracy();
        double altitude = data.location.getAltitude();
        int satellites = data.satellites;
        double latitude = data.location.getLatitude();
        double longitude = data.location.getLongitude();
        float speed = data.location.getSpeed();

        // strange: I logged updates for timestamps ~12h ago right after a
        // regula timestamp
        if (time < System.currentTimeMillis() - ALLOWED_TIME_DRIFT) {
            Log.i(LOG_TAG, "out of date location ignored: " + sdf.format(new Date(time)));
            return;
        }

        // /data/data/de.locked.cellmapper/databases/CellMapper
        // sqlite> select datetime(time, 'unixepoch', 'localtime') FROM Base
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
        } catch (SQLException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        } finally {
            db.endTransaction();
            int bytes = SQLiteDatabase.releaseMemory();
            Log.d(LOG_TAG, "released " + bytes + "bytes");
        }
    }

    public static String getLastRowAsString(Context context) {
        setupDB(context);
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE + " ORDER BY time DESC LIMIT 1", null);

        StringBuilder sb = new StringBuilder(64);
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

        return sb.toString();
    }

    public static int getRows(Context context) {
        setupDB(context);
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }
}
