package de.locked.cellmapper;

import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class CellMapperMain extends Activity {
    private static final String LOG_TAG = CellMapperMain.class.getName();
    private static final int UI_REFRESH_INTERVAL = 5000;

    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private SQLiteDatabase db;
    private Thread refresher;
    private Handler handler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        handler = new Handler();
        db = openOrCreateDatabase(DB_NAME, MODE_PRIVATE, null);

        // set defaults
        PreferenceManager.setDefaultValues(this, R.xml.config, false);
        startUiUpdates();
        ensureServiceStarted();

        final ToggleButton onOff = (ToggleButton) findViewById(R.id.onOffButton);
        onOff.setChecked(isServiceRunning(DbLoggerService.class.getName()));

        onOff.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!onOff.isChecked()) {
                    Log.i(LOG_TAG, "stopping");
                    stopService();
                    stopUiUpdates();
                } else {
                    Log.i(LOG_TAG, "starting");
                    ensureServiceStarted();
                    startUiUpdates();
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        startUiUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopUiUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopUiUpdates();
        stopService();
    }

    protected void stopService() {
        stopService(new Intent(this, DbLoggerService.class));
    }

    private void ensureServiceStarted() {
        if (!isServiceRunning(DbLoggerService.class.getName())) {
            startService(new Intent(this, DbLoggerService.class));
        }
    }

    protected void startUiUpdates() {
        // do nothing if we're alive
        if (refresher != null && refresher.isAlive()) {
            return;
        }

        refresher = new Thread() {
            @Override
            public void run() {
                try {
                    Looper.prepare();
                    while (!interrupted()) {
                        refresh();
                        sleep(UI_REFRESH_INTERVAL); // all 5s
                        Looper.loop();
                    }
                } catch (Exception e) {
                    Log.i(LOG_TAG, "interrupted refresh thread");
                }
            }

        };
        refresher.start();
    }

    private void stopUiUpdates() {
        if (refresher != null) {
            refresher.interrupt();
            refresher = null;
        }
    }

    private boolean isServiceRunning(String serviceName) {
        Log.i(LOG_TAG, "check if service is running");
        boolean serviceRunning = false;
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> l = am.getRunningServices(50);
        Iterator<ActivityManager.RunningServiceInfo> i = l.iterator();
        while (i.hasNext()) {
            ActivityManager.RunningServiceInfo runningServiceInfo = i.next();

            if (runningServiceInfo.service.getClassName().equals(serviceName)) {
                serviceRunning = true;
            }
        }

        Log.i(LOG_TAG, "service is running: " + serviceRunning);
        return serviceRunning;
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
