package de.locked.cellmapper.exporter;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import android.util.Log;

import com.google.gson.Gson;

import de.locked.signalcoverage.share.v2.ApiData;
import de.locked.signalcoverage.share.v2.ApiUser;
import de.locked.signalcoverage.share.v2.Signer;

/**
 * Wrapper for the Rest API
 */
public class Rest {
    private static final String LOG_TAG = Rest.class.getName();

    private final String signupUrl = "/2/user/signUp/";
    // userId, timestamp, signature
    private final String uploadPattern = "/2/data/%s/%d/%s/";

    private final String fullUploadURL;
    private final String fullSignupURL;

    public Rest(String serverUrl) {
        if (serverUrl == null) {
            throw new NullPointerException("url must not be null");
        }
        serverUrl = sanitizeUploadURL(serverUrl);
        
        this.fullUploadURL = serverUrl + uploadPattern;
        this.fullSignupURL = serverUrl + signupUrl;
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
            throw new IllegalArgumentException("URL must not be empty");
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

    public final ApiUser signUp() throws ClientProtocolException, IOException {
        Log.d(LOG_TAG, "request signup from " + fullSignupURL);

        HttpResponse httpResponse = new DefaultHttpClient().execute(new HttpGet(fullSignupURL));
        int status = httpResponse.getStatusLine().getStatusCode();
        if (200 != status) {
            Log.i(LOG_TAG, "response failed. Status: " + status);
            return null;
        }

        String dataString = EntityUtils.toString(httpResponse.getEntity());
        return new Gson().fromJson(dataString, ApiUser.class);
    }

    /**
     * 
     * @param user
     * @param dataList
     * @return status code
     * @throws IOException
     */
    public final int putData(ApiUser user, Collection<ApiData> dataList) throws IOException {
        int timestamp = (int) (Calendar.getInstance().getTimeInMillis() / 1000);
        String signature = new Signer().createSignature(user.userId, user.secret, timestamp, dataList);
        String url = String.format(Locale.US, fullUploadURL, //
                user.userId, timestamp, signature);
        String jsonPayload = new Gson().toJson(dataList);
        
        Header jsonHeader = new BasicHeader(HTTP.CONTENT_TYPE, "application/json");
        StringEntity entity = new StringEntity(jsonPayload);
        entity.setContentType(jsonHeader);
        HttpPut httpPut = new HttpPut(url);
        httpPut.setEntity(entity);
        HttpResponse response = new DefaultHttpClient().execute(httpPut);
        
        Log.i(LOG_TAG, "Response: " + response.getStatusLine().toString());
        return response.getStatusLine().getStatusCode();
    }
}
