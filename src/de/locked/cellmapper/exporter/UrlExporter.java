package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.http.client.ClientProtocolException;

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
    private User user;

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);

    }

    @Override
    public void process(Cursor cursor) {
        try {
            user = getUser();

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
                    upload(new ArrayList<Data>(dataList));
                    dataList.clear();
                }
            }
            if (!dataList.isEmpty()) {
                upload(new ArrayList<Data>(dataList));
                dataList.clear();
            }
        } catch (ClientProtocolException e) {
            Log.e(LOG_TAG, "protocol error", e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "IO error", e);
        }
    }

    private void upload(Collection<Data> dataList) throws UnsupportedEncodingException, ClientProtocolException,
            IOException {
        rest.putData(user, dataList);
    }

    private User getUser() throws ClientProtocolException, IOException {
        // acquire a userId from remote
        User user = rest.signUp();

        // create the secret hash
        String encrypted = Base64.encodeToString(User.makePass(user.userId, user.secret), Base64.DEFAULT);
        user = new User(user.userId, encrypted);
        return user;
    }
}