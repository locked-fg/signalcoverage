package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    private final int chunksize = 100;
    private final Rest rest = new Rest();
    private final Cursor cursor;
    private final SharedPreferences preferences;

    public UrlExporter(Cursor cursor, SharedPreferences preferences) {
        this.cursor = cursor;
        this.preferences = preferences;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);

    }

    @Override
    public void process() {
        try {
            User user = getUser();
            if (user == null){
                return;
            }

            // build the data list
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

                if (dataList.size() == chunksize) {
                    upload(user, new ArrayList<Data>(dataList));
                    dataList.clear();
                }
            }
            cursor.close();
            if (!dataList.isEmpty()) {
                upload(user, new ArrayList<Data>(dataList));
                dataList.clear();
            }

        } catch (ClientProtocolException e) {
            Log.e(LOG_TAG, "protocol error", e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IO error", e);
        }
    }

    private User getUser() throws ClientProtocolException, IOException {
        User user = getUserFromPreference();

        // we don't have a user right now, auto acquire?
        if (user == null) {
            String url = preferences.getString("uploadUrl", null);
            boolean acquireLogin = preferences.getBoolean("acquireLogin", false);
            if (url != null && acquireLogin) {
                User plainPassUser = rest.signUp();
                user = encrypt(plainPassUser);

                // if succeeded, save credentials
                if (user != null) {
                    Editor editor = preferences.edit();
                    editor.putInt("login", plainPassUser.userId);
                    editor.putString("password", plainPassUser.secret);
                    editor.commit();
                }
            }
        }

        return user;
    }

    private User getUserFromPreference() {
        int login = preferences.getInt("login", -1);
        String pass = preferences.getString("password", null);

        if (login > 0 && pass != null) {
            User user = new User(login, pass);
            return encrypt(user);
        }

        return null;
    }

    private void upload(User user, Collection<Data> dataList) throws UnsupportedEncodingException,
            ClientProtocolException, IOException {
        rest.putData(user, dataList);
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