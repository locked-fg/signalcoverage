package de.locked.cellmapper.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

// enum!!!
public class Preferences {
    public static final String LOG_TAG = Preferences.class.getName();

    public static String update_duration = "update_duration";
    public static String sleep_between_measures = "sleep_between_measures";
    public static String min_location_time = "min_location_time";
    public static String min_location_distance = "min_location_dist";
    public static String uploadURL = "uploadUrl";
    public static String login = "login";
    public static String password = "password";
    public static String licenseAgreed = "licenseAgreed";
    public static String updateOnSignalChange = "updateOnSignalChange";

    public static String getString(Context c, String key, String def){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(c);
        return preferences.getString(key, def);
    }

    public static long getAsLong(SharedPreferences preferences, String key, long def) {
        try {
            long value = preferences.getLong(key, def);
            return value;
        } catch (ClassCastException e) {
            Log.d(LOG_TAG, "no long value found for key: " + key);
        }

        String valueString = null;
        try {
            valueString = preferences.getString(key, Long.toString(def));
            long value = Long.parseLong(valueString);
            return value;
        } catch (Exception e) {
            Log.d(LOG_TAG, "value '" + valueString + "' could not be parsed to long: " + key);
        }

        if (!preferences.getAll().containsKey(key)) {
            Log.i(LOG_TAG, "preference " + key + " was requested but not contained in the preferences! "
                    + "Are you sure that you chose the correct key?");
        }

        return def;
    }
}
