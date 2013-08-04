package de.locked.cellmapper.exporter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.http.client.ClientProtocolException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import de.locked.cellmapper.R;
import de.locked.cellmapper.model.Preferences;
import de.locked.cellmapper.share.v1.Data;
import de.locked.cellmapper.share.v1.User;

public class UrlExporter extends AbstractAsyncExporterTask {
    private static final String LOG_TAG = UrlExporter.class.getName();
    private final Rest rest;
    private final SharedPreferences preferences;
    private final int chunksize = 300;

    public UrlExporter(Context context) {
        super(context, R.string.exportNotificationUrl, android.R.drawable.ic_menu_upload);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String baseURL = preferences.getString(Preferences.uploadURL, null);
        rest = (baseURL != null) ? new Rest(baseURL) : null;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (rest == null) {
            Log.i(LOG_TAG, "no Rest service initialized");
            return null;
        }

        try {
            User user = getUser();
            if (user == null) {
                return null;
            }

            // build the data list
            int i = 0;
            Collection<Data> dataList = new ArrayList<Data>(chunksize);
            while (cursor.moveToNext() && !isCancelled()) {
                Data data = new Data();
                // data.userId = userId;
                data.time = cursor.getInt(cursor.getColumnIndex("time"));
                data.accuracy = cursor.getDouble(cursor.getColumnIndex("accuracy"));
                data.altitude = cursor.getFloat(cursor.getColumnIndex("altitude"));
                data.satellites = cursor.getInt(cursor.getColumnIndex("satellites"));
                data.latitude = cursor.getDouble(cursor.getColumnIndex("latitude"));
                data.longitude = cursor.getDouble(cursor.getColumnIndex("longitude"));
                data.speed = cursor.getDouble(cursor.getColumnIndex("speed"));
                data.signalStrength = cursor.getInt(cursor.getColumnIndex("signalStrength"));
                data.carrier = cursor.getString(cursor.getColumnIndex("carrier"));

                dataList.add(data);
                i++;

                if (dataList.size() == chunksize) {
                    upload(user, dataList, i);
                }
            }
            cursor.close();
            if (!dataList.isEmpty()) {
                upload(user, dataList, i);
            }
        } catch (IOException e) {
            notify("Encountered an issue: " + e.getMessage(), android.R.drawable.stat_notify_error);
            // android.R.drawable.stat_notify_sdcard_usb
            return null;
        }
        publishProgress(100);
        return null;
    }

    private void upload(User user, Collection<Data> dataList, int i) throws UnsupportedEncodingException,
            ClientProtocolException, IOException {
        int statusCode = rest.putData(user, dataList);
        dataList.clear();
        publishProgress(i * 100 / max);

        if (statusCode != 200) {
            String message = "Upload error, status code: " + statusCode;
            throw new IOException(message);
        }
    }

    private User getUser() throws IOException {
        Log.i(LOG_TAG, "getting user login");
        User user = getUserFromPreference();

        // we don't have a user right now, auto acquire?
        if (user == null) {
            Log.i(LOG_TAG, "no credentials given");

            String url = preferences.getString(Preferences.uploadURL, null);
            if (url != null) {
                Log.i(LOG_TAG, "auto login allowed and url given");

                User plainPassUser = rest.signUp();
                if (plainPassUser == null) { // no response
                    String message = "The server did not respond properly. We did not get a username.";
                    Log.w(LOG_TAG, message);
                    throw new IOException(message);
                }

                user = encrypt(plainPassUser);

                // if succeeded, save credentials
                Log.i(LOG_TAG, "got a user name: " + user.userId);
                Editor editor = preferences.edit();
                editor.putString(Preferences.login, Integer.toString(plainPassUser.userId));
                editor.putString(Preferences.password, plainPassUser.secret);
                editor.commit();
            }
        }

        return user;
    }

    private User getUserFromPreference() {
        String loginString = preferences.getString(Preferences.login, "");
        String pass = preferences.getString(Preferences.password, "");

        loginString = loginString.trim();
        pass = pass.trim();

        if (loginString.length() == 0 || pass.length() == 0) {
            return null;
        }

        int login = Integer.parseInt(loginString);
        User user = new User(login, pass);
        return encrypt(user);
    }

    // create the secret hash
    private User encrypt(User user) {
        if (user == null) {
            throw new NullPointerException("user must not be null");
        }
        String encrypted = Base64.encodeToString(User.makePass(user.userId, user.secret), Base64.DEFAULT);
        return new User(user.userId, encrypted);
    }
}