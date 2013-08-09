package de.locked.cellmapper.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.telephony.SignalStrength;
import android.util.Log;

public class DbHandler extends SQLiteOpenHelper {
    public static final String LOG_TAG = DbHandler.class.getName();
    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private static final SimpleDateFormat sdf = new SimpleDateFormat("y-MM-dd HH:mm:ss", Locale.US);
    private static final int DATABASE_VERSION = 5;

    private static DbHandler instance = null;
    private int writecount = 0;

    public synchronized static DbHandler get(Context context) {
        if (instance == null) {
            instance = new DbHandler(context);
        }
        return instance;
    }

    private DbHandler(Context context) {
        super(context, DB_NAME, null, DATABASE_VERSION);
    }

    public String getLastEntryString() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT datetime(time, 'unixepoch', 'localtime') AS LastEntry FROM " + TABLE + " ORDER BY time DESC LIMIT 1", null);

        String result = "";
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();
        return result;
    }

	public void save(Location location, SignalStrength signal, int satellites,
			String carrier, String androidRelease, 
			String manufacturer, String model, String device, String osVersion) {
        int timeSec = (int) (location.getTime() / 1000);
        carrier = carrier == null ? "" : carrier;

        // /data/data/de.locked.cellmapper/databases/CellMapper
        // sqlite> select datetime(time, 'unixepoch', 'localtime') FROM Base
        // ORDER BY TIME DESC LIMIT 4;
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            Log.i(LOG_TAG, "writing data to db (location+signal) at time " + sdf.format(new Date(timeSec * 1000L)));

            ContentValues values = new ContentValues();
            values.put("time", timeSec);
            values.put("accuracy", location.getAccuracy());
            values.put("altitude", location.getAltitude());
            values.put("satellites", satellites);
            values.put("latitude", location.getLatitude());
            values.put("longitude", location.getLongitude());
            values.put("speed", location.getSpeed());
            values.put("signalStrength", signal.getGsmSignalStrength());
            values.put("carrier", carrier);
            values.put("androidrelease", androidRelease);
            values.put("manufacturer", manufacturer);
            values.put("model", model);
            values.put("device", device);
            values.put("osVersion", osVersion);
            long success = db.replace(TABLE, null, values);

            Log.i(LOG_TAG, "transaction successfull: " + success);
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        } finally {
            db.endTransaction();
        }

        if (writecount++ % 100 == 0) {
            SQLiteDatabase.releaseMemory();
        }
	}

    public String getLastRowAsString() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE + " ORDER BY time DESC LIMIT 1", null);

        StringBuilder sb = new StringBuilder(64);
        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String columnName = cursor.getColumnName(i);
                String value = cursor.getString(i);

                sb.append(Strings.rpad(columnName + ":", 16, " "));
                sb.append(value);
                sb.append("\n");
            }
        }
        cursor.close();
        return sb.toString();
    }

    public int getRows() {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    public Cursor getAll() {
        return getReadableDatabase().rawQuery("SELECT * FROM " + TABLE + " ORDER BY time ASC", null);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(LOG_TAG, "create db");
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
                " signalStrength INT, " + //
                " carrier TEXT, " + //
                " androidRelease TEXT, " + //
                " manufacturer TEXT, " + //
        		" model TEXT, " + //
                " device TEXT, " + //
                " osVersion TEXT " + //
                " );");
    }

    @Override
    public void close() {
        super.close();
        instance = null;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(LOG_TAG, "Upgrade from " + oldVersion + " to " + newVersion);
        switch (oldVersion) {
            case 1:
            case 2:
            case 3:
                // remove cdmaDbm, evdoDbm, evdoSnr
                String cols = "time, accuracy, altitude, satellites, latitude, longitude, speed, signalStrength, carrier";
                keep(db, cols);
            case 4:
                // add androidRelease
                addColumn(db, "androidRelease");
            case 5:
            	// add model specific stuff
                addColumn(db, "manufacturer");
                addColumn(db, "model");
                addColumn(db, "device");
                addColumn(db, "osVersion");

            default:
                break;
        }
    }

    private void addColumn(SQLiteDatabase db, String colname) {
        Log.d(LOG_TAG, "add column "+colname);
        db.beginTransaction();
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + colname + " TEXT");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void keep(SQLiteDatabase db, String cols) {
        Log.d(LOG_TAG, "delete all but those columns "+cols);
        db.beginTransaction();
        try {
            db.execSQL("CREATE TEMPORARY TABLE t1_backup(" + cols + ") ");
            db.execSQL("INSERT INTO t1_backup SELECT " + cols + " FROM " + TABLE);
            db.execSQL("DROP TABLE " + TABLE);
            db.execSQL("CREATE TABLE " + TABLE + "(" + cols + "), PRIMARY KEY (time)");
            db.execSQL("INSERT INTO " + TABLE + " SELECT " + cols + " FROM t1_backup");
            db.execSQL("DROP TABLE t1_backup");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        Log.w(LOG_TAG, "someone forgot to close the database");
        close();
    }
}
