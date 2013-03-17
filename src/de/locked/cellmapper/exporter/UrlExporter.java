package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.http.client.ClientProtocolException;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;
import de.locked.cellmapper.share.Data;
import de.locked.cellmapper.share.User;

public class UrlExporter implements DataExporter {
    private static final String LOG_TAG = UrlExporter.class.getName();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Rest rest;
    private final Cursor cursor;
    private final SharedPreferences preferences;
    private final int chunksize = 1000;

    public UrlExporter(Cursor cursor, SharedPreferences preferences) {
        this.cursor = cursor;
        this.preferences = preferences;
        
        String baseURL = preferences.getString("uploadUrl", null);
        if (baseURL != null) {
            rest = new Rest(baseURL);
        } else {
            Log.i(LOG_TAG, "no base URL given");
            rest = null;
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void process() {
        if (rest == null) {
            Log.i(LOG_TAG, "no Rest service initialized");
            return;
        }
        
        try {
            User user = getUser();
            if (user == null) {
                return;
            }

            // build the data list
            int i = 0;
            Collection<Data> dataList = new ArrayList<Data>(chunksize);
            while (cursor.moveToNext()) {
                Data data = new Data();
                // data.userId = userId;
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
                i++;

                if (dataList.size() == chunksize) {
                    rest.putData(user, new ArrayList<Data>(dataList));
                    dataList.clear();
                    pcs.firePropertyChange("status", 0, i);
                }
            }
            cursor.close();
            if (!dataList.isEmpty()) {
                rest.putData(user, dataList);
                dataList.clear();
                pcs.firePropertyChange("status", 0, i);
            }

        } catch (ClientProtocolException e) {
            Log.e(LOG_TAG, "protocol error", e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IO error", e);
        }
    }

    private User getUser() throws ClientProtocolException, IOException {
        Log.i(LOG_TAG, "getting user login");
        User user = getUserFromPreference();

        // we don't have a user right now, auto acquire?
        if (user == null) {
            Log.i(LOG_TAG, "no credentials given");

            String url = preferences.getString("uploadUrl", null);
            if (url != null) {
                Log.i(LOG_TAG, "auto login allowed and url given");

                User plainPassUser = rest.signUp();
                user = encrypt(plainPassUser);

                // if succeeded, save credentials
                if (user != null) {
                    Log.i(LOG_TAG, "got a user name!");

                    Editor editor = preferences.edit();
                    editor.putString("login", Integer.toString(plainPassUser.userId));
                    editor.putString("password", plainPassUser.secret);
                    editor.commit();
                } else {
                    Log.w(LOG_TAG, "auto login failed!");
                }
            }
        }

        return user;
    }

    private User getUserFromPreference() {
        String loginString = preferences.getString("login", "");
        String pass = preferences.getString("password", null);

        int login = 0;
        if (loginString.trim().length() > 0){
            login = Integer.parseInt(loginString);
        }

        if (login > 0 && pass != null) {
            User user = new User(login, pass);
            return encrypt(user);
        }

        return null;
    }

    // create the secret hash
    private User encrypt(User user) {
        if (user == null) {
            return null;
        }
        String encrypted = Base64.encodeToString(User.makePass(user.userId, user.secret), Base64.DEFAULT);
        return new User(user.userId, encrypted);
    }
}