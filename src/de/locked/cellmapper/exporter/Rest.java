package de.locked.cellmapper.exporter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Log;

import com.google.gson.Gson;

import de.locked.cellmapper.share.v1.Data;
import de.locked.cellmapper.share.v1.Signer;
import de.locked.cellmapper.share.v1.User;

/**
 * Wrapper for the Rest API
 */
public class Rest {
    private static final String LOG_TAG = Rest.class.getName();

    private final String server; // "http://192.168.178.32:8084/cellmapper/rest";
    private final String signupUrl = "/1/user/signUp/";
    // userId, timestamp, signature
    private final String uploadPattern = "/1/data/%s/%d/%s/";

    public Rest(String serverUrl) {
        serverUrl = sanitizeUploadURL(serverUrl);
        this.server = serverUrl;
    }

    /**
     * add http before and remove a trailing slash
     * 
     * @param url
     * @return
     */
    private String sanitizeUploadURL(String url) {
        Log.d(LOG_TAG, "sanitizing url: " + url);
        if (url == null) {
            return null;
        }

        url = url.trim();
        if (url.length() != 0) {
            if (url.endsWith("/")) {
                url = url.substring(0, url.length()-1);
            }
            if (!url.startsWith("http")) {
                url = "http://" + url;
            }
        }
        Log.d(LOG_TAG, "sanitized url: " + url);
        return url;
    }

    public final User signUp() throws ClientProtocolException, IOException {
        String url = server + signupUrl;

        Log.d(LOG_TAG, "request signup from " + url);
        HttpGet get = new HttpGet(url);
        HttpResponse httpResponse = new DefaultHttpClient().execute(get);
        String dataString = EntityUtils.toString(httpResponse.getEntity());
        Log.i(LOG_TAG, "received: " + dataString);

        User user = new Gson().fromJson(dataString, User.class);
        return user;
    }

    /**
     * 
     * @param userId
     *            the remote user Id
     * @param timestamp
     *            timestamp (regular unix timestamp - seconds since 1970 in UTC)
     * @param jsonPayload
     * @param signature
     * @throws UnsupportedEncodingException
     * @throws IOException
     * @throws ClientProtocolException
     */
    private final void putData(int userId, int timestamp, String jsonPayload, String signature)
            throws UnsupportedEncodingException, IOException, ClientProtocolException {
        String url = String.format(Locale.US, server + uploadPattern, //
                userId, timestamp, signature);
        Log.i(LOG_TAG, "URL: " + url + " \npayload size: " + jsonPayload.length() + " chars \npayload: " + jsonPayload);

        HttpPut httpPut = new HttpPut(url);
        httpPut.setEntity(new StringEntity(jsonPayload));
        HttpResponse response = new DefaultHttpClient().execute(httpPut);
        Log.i(LOG_TAG, "Response: " + response.getStatusLine().toString());
    }

    public final void putData(User user, Collection<Data> dataList) throws UnsupportedEncodingException,
            ClientProtocolException, IOException {
        // set userId to the data elements
        for (Data data : dataList) {
            data.userId = user.userId;
        }

        // encode list to JSON
        int timestamp = (int) (Calendar.getInstance().getTimeInMillis() / 1000);
        String jsonPayload = new Gson().toJson(Collections.unmodifiableCollection(dataList));
        String signature = new Signer().createSignature(user.userId, user.secret, timestamp, jsonPayload);

        Log.d(LOG_TAG, String.format("%d / %s / %d / %s => %s", //
                user.userId, user.secret, timestamp, jsonPayload, signature));

        // /{login}/{timestamp}/{signature}
        putData(user.userId, timestamp, jsonPayload, signature);
    }
}
