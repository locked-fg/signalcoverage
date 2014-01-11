package de.locked.cellmapper.exporter;

import android.util.Log;

import com.google.gson.Gson;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import de.locked.signalcoverage.share.ApiData;
import de.locked.signalcoverage.share.ApiUser;

/**
 * Wrapper for the Rest API
 */
public class Rest {
    private static final String LOG_TAG = Rest.class.getName();

    private final String signupUrl = "/user/signUp/";
    // userId, hashed pass
    private final String uploadPattern = "/data/%s/%s/";

    private final String fullUploadURL;
    private final String fullSignupURL;

    public Rest(String serverUrl) {
        if (serverUrl == null) {
            throw new NullPointerException("url must not be null");
        }
        serverUrl = beautify(serverUrl);
        this.fullUploadURL = serverUrl + uploadPattern;
        this.fullSignupURL = serverUrl + signupUrl;
    }

    private String beautify(String s) {
        String res = s.trim();
        if (!res.startsWith("http")) {
            res = "https://" + s;
        }
        while (res.endsWith("/")) {
            res = res.substring(0, res.length() - 1);
        }
        Log.d(LOG_TAG, "beautification: " + s + " -> " + res);
        return res;
    }

    public final ApiUser signUp() throws ClientProtocolException, IOException {
        Log.d(LOG_TAG, "request signup from " + fullSignupURL);

        HttpResponse httpResponse = new DefaultHttpClient().execute(new HttpGet(fullSignupURL));
        int status = httpResponse.getStatusLine().getStatusCode();
        if (200 != status) {
            Log.i(LOG_TAG, "response failed. Status: " + status + ": " + httpResponse);
            return null;
        }

        String dataString = EntityUtils.toString(httpResponse.getEntity());
        return new Gson().fromJson(dataString, ApiUser.class);
    }

    /**
     * @param user
     * @param dataList
     * @return status code
     * @throws IOException
     */
    public final int putData(ApiUser user, Collection<ApiData> dataList) throws IOException, URISyntaxException {
        String url = String.format(Locale.US, fullUploadURL, //
                user.getUserId(), URLEncoder.encode(user.getSecret(), "UTF-8"));

        String data = new Gson().toJson(dataList);
        HttpPut post = new HttpPut();
        post.setURI(new URI(url));
        post.setEntity(new StringEntity(data));
        HttpResponse response = new DefaultHttpClient().execute(post);

        Log.i(LOG_TAG, "upload to: " + url + "\n" +
                "Response: " + response.getStatusLine().toString());
        return response.getStatusLine().getStatusCode();
    }
}
