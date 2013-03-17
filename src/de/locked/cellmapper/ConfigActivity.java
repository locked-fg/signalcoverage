package de.locked.cellmapper;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

public class ConfigActivity extends PreferenceActivity {
    private static final String LOG_TAG = ConfigActivity.class.getName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // convert the int-login to String
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            int login = preferences.getInt("login", -1);
            if (login >= 0) {
                String loginString = Integer.toString(login);
                preferences.edit().putString("login", loginString).commit();
            }
        } catch (ClassCastException e) {
            Log.d(LOG_TAG, "no int login - leave all as is");
        }

        addPreferencesFromResource(R.xml.config);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference != null)
            if (preference instanceof PreferenceScreen)
                if (((PreferenceScreen) preference).getDialog() != null)
                    ((PreferenceScreen) preference)
                            .getDialog()
                            .getWindow()
                            .getDecorView()
                            .setBackgroundDrawable(
                                    this.getWindow().getDecorView().getBackground().getConstantState().newDrawable());
        return false;
    }
}
