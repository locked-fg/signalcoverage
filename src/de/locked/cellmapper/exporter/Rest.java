package de.locked.cellmapper.exporter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Collection;
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
        if (serverUrl == null) {
            throw new NullPointerException("url must not be null");
        }
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

        url = url.trim();
        url = url.replace(". ", ".");
        if (url.length() == 0) {
            throw new IllegalArgumentException("url must not be empty");
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }

        Log.d(LOG_TAG, "sanitized url: " + url);
        return url;
    }

    public final User signUp() throws ClientProtocolException, IOException {
        String url = server + signupUrl;
        Log.d(LOG_TAG, "request signup from " + url);

        HttpResponse httpResponse = new DefaultHttpClient().execute(new HttpGet(url));
        int status = httpResponse.getStatusLine().getStatusCode();
        if (200 != status) {
            Log.i(LOG_TAG, "response failed. Status: " + status);
            return null;
        }

        String dataString = EntityUtils.toString(httpResponse.getEntity());
        return new Gson().fromJson(dataString, User.class);
    }

    /**
     * 
     * @param userId
     *            the remote user Id
     * @param timestamp
     *            timestamp (regular unix timestamp - seconds since 1970 in UTC)
     * @param jsonPayload
     * @param signature
     * @return the status code
     * @throws UnsupportedEncodingException
     * @throws IOException
     * @throws ClientProtocolException
     */
    private final int putData(int userId, int timestamp, Collection<Data> dataList, String signature) throws IOException {
        String url = String.format(Locale.US, server + uploadPattern, //
                userId, timestamp, signature);
        String jsonPayload = new Gson().toJson(dataList);
        HttpPut httpPut = new HttpPut(url);
        httpPut.setEntity(new StringEntity(jsonPayload));
        HttpResponse response = new DefaultHttpClient().execute(httpPut);
        Log.i(LOG_TAG, "Response: " + response.getStatusLine().toString());
        return response.getStatusLine().getStatusCode();
    }

    /**
     * 
     * @param user
     * @param dataList
     * @return status code
     * @throws IOException
     */
    public final int putData(User user, Collection<Data> dataList) throws IOException {
        int timestamp = (int) (Calendar.getInstance().getTimeInMillis() / 1000);
        String signature = new Signer().createSignature(user.userId, user.secret, timestamp, dataList);

        Log.d(LOG_TAG, String.format("Signature: %d / %s / %d / <jsonPayload> => %s", //
                user.userId, user.secret, timestamp, signature));
        return putData(user.userId, timestamp, dataList, signature);
    }
}
