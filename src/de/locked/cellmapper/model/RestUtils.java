package de.locked.cellmapper.model;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Log;

public class RestUtils {
    private final static String LOG_TAG = RestUtils.class.getName();

    public static String get(String url) {
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url);
        String sb = null;
        try {
            HttpResponse response = httpClient.execute(httpGet);
            Log.i(LOG_TAG, response.getStatusLine().toString());

            sb = EntityUtils.toString(response.getEntity());
        } catch (ClientProtocolException e) {
            Log.e(LOG_TAG, "", e);
        } catch (IOException e) {
            Log.e(LOG_TAG, "", e);
        }

        return sb;
    }
}
