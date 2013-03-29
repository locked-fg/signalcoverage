package de.locked.cellmapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import de.locked.cellmapper.exporter.FileExporter;
import de.locked.cellmapper.exporter.UrlExporter;
import de.locked.cellmapper.model.DbHandler;
import de.locked.cellmapper.model.Preferences;
import de.locked.cellmapper.model.Utils;

public class CellMapperMain extends Activity {
    private static final String LOG_TAG = CellMapperMain.class.getName();
    private static final int UI_REFRESH_INTERVAL = 150; // ms
    private final Handler handler = new Handler();

    private Thread refresher;
    private boolean informedUserAboutProblems = false;
    private AsyncTask<Void, Integer, Void> exporter;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "create activity");
        setContentView(R.layout.activity_main);

        // set defaults
        PreferenceManager.setDefaultValues(this, R.xml.config, false);

        ((Button) findViewById(R.id.startButton)).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                ensureServiceStarted();
            }
        });
        ((Button) findViewById(R.id.stopButton)).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                stopService();
            }
        });
        ((TextView) findViewById(R.id.progressX)).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (exporter != null){
                    exporter.cancel(true);
                }
            }
        });

        startUiUpdates();
        ensureServiceStarted();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "resume activity");
        informedUserAboutProblems = false;
        startUiUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "pause activity");
        stopUiUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "destroy activity");
        stopService();
        stopUiUpdates();
        DbHandler.close();
    }

    private void stopService() {
        Log.i(LOG_TAG, "stopping service");
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

        // GPS disabled
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!gpsEnabled) {
            maybeShowGPSDisabledAlertToUser("GPS is disabled in your device. Please enable it.");
            return;
        }

        // now start!
        Log.i(LOG_TAG, "starting service");
        startService(new Intent(this, DbLoggerService.class));
    }

    private void startUiUpdates() {
        // do nothing if we're alive
        if (refresher != null && refresher.isAlive()) {
            return;
        }
        final Context context = getApplicationContext();

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
                final StringBuilder sb = new StringBuilder(100);
                sb.append("Service Running: " + Utils.isServiceRunning(context, DbLoggerService.class) + "\n");
                sb.append(DbHandler.getLastEntryString(context)).append("\n");
                sb.append("Data rows: " + DbHandler.getRows(context)).append("\n");
                sb.append("------\n");
                sb.append(DbHandler.getLastRowAsString(context));
                final boolean isRunning = Utils.isServiceRunning(context, DbLoggerService.class);

                // update UI
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.textfield)).setText(sb.toString());
                        ((Button) findViewById(R.id.startButton)).setEnabled(!isRunning);
                        ((Button) findViewById(R.id.stopButton)).setEnabled(isRunning);
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
                ProgressBar bar = (ProgressBar) findViewById(R.id.main_progressBar);
                View row = findViewById(R.id.progress);
                exporter = new FileExporter(row,bar, "CellMapper/data");
                exporter.execute();
                return true;

            default:
                return false;
        }
    }

    private void upload() {
        Log.i(LOG_TAG, "upload data");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (null == preferences.getString(Preferences.uploadURL, null)) {
            Toast toast = Toast.makeText(getApplicationContext(),
                    "You need to set an upload URL before you can upload data.\n"
                            + "Go to Settings > Account to enter access to an upload service.", Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        ProgressBar bar = (ProgressBar) findViewById(R.id.main_progressBar);
        View row = findViewById(R.id.progress);
        exporter = new UrlExporter(row, bar, preferences);
        exporter.execute();
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
                .setPositiveButton(R.string.open_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        alertDialogBuilder.create().show();
        informedUserAboutProblems = true;
    }
}
