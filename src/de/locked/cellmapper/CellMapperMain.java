package de.locked.cellmapper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.gson.Gson;

import de.locked.cellmapper.model.DbHandler;
import de.locked.cellmapper.share.Data;
import de.locked.cellmapper.share.Signer;
import de.locked.cellmapper.share.User;

public class CellMapperMain extends Activity {
    private static final String LOG_TAG = CellMapperMain.class.getName();
    private static final int UI_REFRESH_INTERVAL = 2000;

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
        try {
            // get a userId
            HttpGet get = new HttpGet("http://192.168.178.32:8084/cellmapper/rest/user/signUp/");
            HttpResponse httpResponse = new DefaultHttpClient().execute(get);
            String dataString = EntityUtils.toString(httpResponse.getEntity());
            Log.i(LOG_TAG, "received: " + dataString);
            User user = new Gson().fromJson(dataString, User.class);

            // create the secret hash
            int userId = user.userId;
            String unencrypted = user.secret;
            String encrypted = Base64.encodeToString(User.makePass(userId, unencrypted), Base64.DEFAULT);

            // build the data list
            Collection<Data> dataList = new ArrayList<Data>();
            Cursor cursor = DbHandler.getAll(this);
            while (cursor.moveToNext()) {
                Data data = new Data();
                data.userId = userId;
                data.time = cursor.getInt(cursor.getColumnIndex("time"));
                data.accuracy = cursor.getDouble(cursor.getColumnIndex("accuracy"));
                data.altitude = cursor.getFloat(cursor.getColumnIndex("altitude"));
                data.satellites = cursor.getInt(cursor.getColumnIndex("satellites"));
                data.latitude = cursor.getDouble(cursor.getColumnIndex("latitude"));
                data.longitude = cursor.getDouble(cursor.getColumnIndex("longitude"));
                data.speed = cursor.getDouble(cursor.getColumnIndex("speed"));
                data.cdmaDbm = cursor.getInt(cursor.getColumnIndex("cdmaDbm"));
                data.evdoDbm = cursor.getInt(cursor.getColumnIndex("evdoDbm"));
                data.evdoSnr = cursor.getInt(cursor.getColumnIndex("evdoSnr"));
                data.signalStrength = cursor.getInt(cursor.getColumnIndex("signalStrength"));
                data.carrier = cursor.getString(cursor.getColumnIndex("carrier"));

                dataList.add(data);
            }
            cursor.close();

            // encode list to JSON
            String jsonPayload = new Gson().toJson(Collections.unmodifiableCollection(dataList));
            
            int timestamp = (int) (Calendar.getInstance().getTimeInMillis() / 1000);
            String signature = new Signer().createSignature(userId, encrypted, timestamp, jsonPayload);
            
            String log = "computing sig: " + userId + " / " + encrypted + " / " + timestamp + " / " + jsonPayload;
            Log.i(LOG_TAG, log);
            Log.i(LOG_TAG, "signature " + signature);

            // /{login}/{timestamp}/{signature}
            String url = String.format(Locale.US, "http://192.168.178.32:8084/cellmapper/rest/data/%s/%d/%s/", userId,
                    timestamp, signature);
            StringEntity entityPayload = new StringEntity(jsonPayload);

            Log.i(LOG_TAG, "URL: " + url+"\npayload: " + entityPayload);

            HttpPut httpPut = new HttpPut(url);
            httpPut.setEntity(entityPayload);
            HttpResponse response = new DefaultHttpClient().execute(httpPut);
            Log.i(LOG_TAG, "Response: " + response.getStatusLine().toString());

        } catch (Exception e) {
            Log.e(LOG_TAG, "error", e);
        }
    }

    private void dumpDataToFile() {
        ProgressBar mProgress = (ProgressBar) findViewById(R.id.main_progressBar);
        new FileExporter(mProgress, this).execute();
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
