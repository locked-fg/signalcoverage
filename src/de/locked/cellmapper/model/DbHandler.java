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
import android.util.Log;

public class DbHandler extends SQLiteOpenHelper {
    public static final int ALLOWED_TIME_DRIFT = 15000; // ms
    public static final String LOG_TAG = DbHandler.class.getName();
    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private static final SimpleDateFormat sdf = new SimpleDateFormat("y-MM-dd HH:mm:ss", Locale.US);
    private static final int DATABASE_VERSION = 1;

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
        Cursor cursor = db.rawQuery("SELECT datetime(time, 'unixepoch', 'localtime') AS LastEntry FROM " + TABLE
                + " ORDER BY time DESC LIMIT 1", null);

        String result = "";
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();
        return result;
    }

    public void save(Data data) {
        int timeSec = (int) (data.location.getTime() / 1000);
        String carrier = data.carrier == null ? "" : data.carrier;

        // /data/data/de.locked.cellmapper/databases/CellMapper
        // sqlite> select datetime(time, 'unixepoch', 'localtime') FROM Base
        // ORDER BY TIME DESC LIMIT 4;
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            Log.i(LOG_TAG, "writing data to db (location+signal) at time " + sdf.format(new Date(timeSec*1000L)));

            ContentValues values = new ContentValues();
            values.put("time", timeSec);
            values.put("accuracy", data.location.getAccuracy());
            values.put("altitude", data.location.getAltitude());
            values.put("satellites", data.satellites);
            values.put("latitude", data.location.getLatitude());
            values.put("longitude", data.location.getLongitude());
            values.put("speed", data.location.getSpeed());
            values.put("signalStrength", data.signal.getGsmSignalStrength());
            values.put("carrier", carrier);
            long success = db.replace(TABLE, null, values);

            Log.i(LOG_TAG, "transaction successfull: " + success);
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
        
        if (writecount++ % 100 == 0){
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
                " cdmaDbm INT, " + //
                " evdoDbm INT, " + //
                " evdoSnr INT, " + //
                " signalStrength INT, " + //
                " carrier TEXT " + //
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
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        Log.w(LOG_TAG, "someone forgot to close the database");
        close();
    }
}
