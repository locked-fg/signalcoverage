package de.locked.cellmapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

public class AppInfo extends Activity {
    private static final String LOG_TAG = AppInfo.class.getName();

    DefaultHttpClient http_client = new DefaultHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appinfo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        AccountManager accountManager = AccountManager.get(getApplicationContext());
        Account account = (Account) intent.getExtras().get("account");
        accountManager.invalidateAuthToken(account.type, null);
        accountManager.getAuthToken(account, "ah", false, new GetAuthTokenCallback(), null);
    }

    private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {
        @Override
        public void run(AccountManagerFuture<Bundle> result) {
            try {
                Bundle bundle = result.getResult();
                Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
                if (intent != null) { // User input required
                    Log.i(LOG_TAG, "user input required");
                    startActivity(intent);

                } else {
                    Log.i(LOG_TAG, "got token");
                    onGetAuthToken(bundle);
                }
            } catch (OperationCanceledException e) {
                Log.w(LOG_TAG, e.getMessage());
            } catch (AuthenticatorException e) {
                Log.w(LOG_TAG, e.getMessage());
            } catch (IOException e) {
                Log.w(LOG_TAG, e.getMessage());
            }
        }
    }

    protected void onGetAuthToken(Bundle bundle) {
        String auth_token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        new GetCookieTask().execute(auth_token);
    }

    private class GetCookieTask extends AsyncTask<String, Object, Boolean> {
        @Override
        protected Boolean doInBackground(String... tokens) {
            try {
                // Don't follow redirects
                http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

                HttpGet httpGet = new HttpGet(
                        "https://my-reception-map.appspot.com/_ah/login?continue=http://localhost/&auth=" + tokens[0]);
                HttpResponse response = http_client.execute(httpGet);
                if (response.getStatusLine().getStatusCode() != 302) {
                    // Response should be a redirect
                    return false;
                }

                for (Cookie cookie : http_client.getCookieStore().getCookies()) {
                    if (cookie.getName().equals("ACSID")) {
                        return true;
                    }
                }
            } catch (ClientProtocolException e) {
                Log.w(LOG_TAG, e.getMessage());
            } catch (IOException e) {
                Log.w(LOG_TAG, e.getMessage());
            } finally {
                http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            new AuthenticatedRequestTask().execute("http://my-reception-map.appspot.com/upload");
        }
    }

    private class AuthenticatedRequestTask extends AsyncTask<String, Object, HttpResponse> {
        @Override
        protected HttpResponse doInBackground(String... urls) {
            try {
                HttpGet http_get = new HttpGet(urls[0]);
                return http_client.execute(http_get);
            } catch (ClientProtocolException e) {
                Log.w(LOG_TAG, e.getMessage());
            } catch (IOException e) {
                Log.w(LOG_TAG, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(HttpResponse result) {
            if (result == null || result.getEntity() == null){
                Log.w(LOG_TAG, "null response");
                return;
            }
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(result.getEntity().getContent()), 4096);
                StringBuilder content = new StringBuilder(4096);
                String line;
                int max = 10000;
                while (max-- > 0 && (line = reader.readLine()) != null) {
//                    Log.d(LOG_TAG, line);
                    content.append(line).append(" \n");
                }
                if (max == 0){
                    Log.w(LOG_TAG, "max line count reached");
                }
                reader.close();
//                Toast.makeText(getApplicationContext(), content, Toast.LENGTH_LONG).show();
                
                TextView textview = (TextView) findViewById(R.id.textview);
                textview.setText(Html.fromHtml(content.toString()));
                
            } catch (IllegalStateException e) {
                Log.w(LOG_TAG, e.getMessage());
            } catch (IOException e) {
                Log.w(LOG_TAG, e.getMessage());
            }
        }
    }
}