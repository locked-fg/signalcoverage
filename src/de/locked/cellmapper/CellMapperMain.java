package de.locked.cellmapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
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
import de.locked.cellmapper.model.Preferences;
import de.locked.cellmapper.model.Utils;

public class CellMapperMain extends Activity {
    private static final String LOG_TAG = CellMapperMain.class.getName();
    private static final int UI_REFRESH_INTERVAL = 200; // ms

    private Thread refresher;
    private Handler handler;
    private boolean informedUserAboutProblems = false;

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
                    stopService();
                } else {
                    ensureServiceStarted();
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

        informedUserAboutProblems = false;
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
        Log.d(LOG_TAG, "stopping service");
        stopService(new Intent(this, DbLoggerService.class));
    }

    private void ensureServiceStarted() {
        Log.d(LOG_TAG, "ensuring that the service is up");
        if (Utils.isServiceRunning(this, DbLoggerService.class)) {
            return;
        }

        // check if location check is allowed at all
        String locationProvidersAllowed = Secure.getString(getContentResolver(), Secure.LOCATION_PROVIDERS_ALLOWED);
        if (locationProvidersAllowed == null || locationProvidersAllowed.length() == 0) {
            String msg = "Postitioning is completely disabled in your device. Please enable it.";
            Log.i(LOG_TAG, msg);
            maybeShowGPSDisabledAlertToUser(msg);
            return;
        }

        // GPS requested but disabled
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean gpsRequested = preferences.getBoolean(Preferences.use_gps, true);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (gpsRequested && !gpsEnabled) {
            String msg = "GPS is disabled in your device. Would you like to enable it?";
            maybeShowGPSDisabledAlertToUser(msg);
        }

        // now start!
        Log.i(LOG_TAG, "starting service");
        // stop the ui refresh thread to avoid starting service and checing if
        // the service is running
        stopUiUpdates();
        startService(new Intent(this, DbLoggerService.class));
        startUiUpdates();
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
            
            private void refresh() {
                final Context context = getApplicationContext();
                final StringBuilder sb = new StringBuilder(100);
                sb.append(DbHandler.getLastEntryString(context)).append("\n");
                sb.append("Data rows: " + DbHandler.getRows(context)).append("\n");
                sb.append("------\n");
                sb.append(DbHandler.getLastRowAsString(context));

                // update UI
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        boolean isRunning = Utils.isServiceRunning(getApplicationContext(), DbLoggerService.class);
                        ToggleButton onOff = (ToggleButton) findViewById(R.id.onOffButton);
                        if (onOff.isChecked() != isRunning) {
                            onOff.setChecked(isRunning);
                        }

                        ((TextView) findViewById(R.id.textfield)).setText(sb.toString());
                    }
                });
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
        Log.i(LOG_TAG, "upload " + max + " rows");

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



    /**
     * show a pop up that tells the user that he wants GPS but it's disabled in
     * the phone. - I want lambdas :-(
     */
    private void maybeShowGPSDisabledAlertToUser(String message) {
        if (informedUserAboutProblems) {
            return;
        }

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(message).setCancelable(false)
                .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        alertDialogBuilder.create().show();
        informedUserAboutProblems = true;
    }
}
