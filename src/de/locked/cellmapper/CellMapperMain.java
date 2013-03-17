package de.locked.cellmapper;

import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import de.locked.cellmapper.exporter.AsyncExporterTask;
import de.locked.cellmapper.exporter.FileExporter;
import de.locked.cellmapper.exporter.UrlExporter;
import de.locked.cellmapper.model.DbHandler;

public class CellMapperMain extends Activity {
    private static final String LOG_TAG = CellMapperMain.class.getName();
    private static final int UI_REFRESH_INTERVAL = 1000;

    private Thread refresher;
    private Handler handler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "create activity");

        setContentView(R.layout.activity_main);

        networkInfo();

        handler = new Handler();

        // set defaults
        PreferenceManager.setDefaultValues(this, R.xml.config, false);
        startUiUpdates();
        ensureServiceStarted();

        final ToggleButton onOff = (ToggleButton) findViewById(R.id.onOffButton);
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

    private void networkInfo() {
        ConnectivityManager service = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo a = service.getActiveNetworkInfo();
        if (a != null) {
            String extraInfo = a.getExtraInfo(); // eplus.de
            State state = a.getState(); // http://developer.android.com/reference/android/net/NetworkInfo.State.html
            String typeName = a.getTypeName(); // mobile
            String subtypeName = a.getSubtypeName(); // HSDPA

            Log.i(LOG_TAG, "extra: " + extraInfo);
            Log.i(LOG_TAG, "state: " + state.toString());
            Log.i(LOG_TAG, "type: " + typeName);
            Log.i(LOG_TAG, "subtype: " + subtypeName);
        }

        TelephonyManager telephonyManager = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));
        String carrier = telephonyManager.getNetworkOperatorName();
        Log.i(LOG_TAG, "carrier: " + carrier);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(LOG_TAG, "resume activity");

        startUiUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(LOG_TAG, "pause activity");

        stopUiUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "destroy activity");

        DbHandler.close();
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
                        sleep(UI_REFRESH_INTERVAL); // all Xs
                    }
                    Looper.loop();
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

        Log.d(LOG_TAG, "service is running: " + serviceRunning);
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
            case R.id.menu_upload:
                upload();
                return true;
            case R.id.menu_saveSD:
                dumpDataToFile();
                return true;
        }
        return false;
    }

    private void upload() {
        Log.i(LOG_TAG, "upload data");
        Cursor cursor = DbHandler.getAll(this);
        ProgressBar bar = (ProgressBar) findViewById(R.id.main_progressBar);
        int max = DbHandler.getRows(this);
        Log.i(LOG_TAG, "upload max " + max + " rows");

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (null == preferences.getString("uploadUrl", null)) {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "You need to set an upload URL before you can upload data.\n"
                            + "Go to Settings > Account to enter access to an upload service.", Toast.LENGTH_LONG);
            toast.show();
        } else {
            new AsyncExporterTask(bar, max, new UrlExporter(cursor, preferences)).execute();
        }
    }

    private void dumpDataToFile() {
        Cursor cursor = DbHandler.getAll(this);
        ProgressBar bar = (ProgressBar) findViewById(R.id.main_progressBar);
        int max = DbHandler.getRows(this);
        new AsyncExporterTask(bar, max, new FileExporter("CellMapper/data", cursor)).execute();
    }

    private void refresh() {
        final StringBuilder sb = new StringBuilder(100);
        sb.append(DbHandler.getLastEntryString(this)).append("\n");
        sb.append("Data rows: " + DbHandler.getRows(this)).append("\n");
        sb.append("------\n");
        sb.append(DbHandler.getLastRowAsString(this));

        // update UI
        handler.post(new Runnable() {

            @Override
            public void run() {
                ToggleButton onOff = (ToggleButton) findViewById(R.id.onOffButton);
                onOff.setChecked(isServiceRunning(DbLoggerService.class.getName()));

                TextView text = (TextView) findViewById(R.id.textfield);
                text.setText(sb.toString());
            }
        });
    }
}
