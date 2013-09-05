package de.locked.cellmapper.model;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class DataListener extends PhoneStateListener implements LocationListener, GpsStatus.Listener {
    private static final String LOG_TAG = DataListener.class.getName();
    private static final SimpleDateFormat sdf = new SimpleDateFormat(
            "y-MM-dd HH:mm:ss", Locale.US);
    private final Context context;
    private final LocationManager locationManager;
    private final TelephonyManager telephonyManager;
    private final DbHandler db;
    private final ConnectivityManager connectivityManager;
    private final int signalListMaxLength = 1000;
    private final LinkedList<SignalEntry> signalList = new LinkedList<SignalEntry>();
    // device data
    private final String manufacturer = Build.MANUFACTURER; // HTC
    private final String device = Build.DEVICE; // bravo
    private final String model = Build.MODEL; // HTC Desire
    private final String osVersion; // 2.6.37.6-cyanogenmod-01509-g8913be8
    // Build.VERSION.SDK_INT returns the API version. In a rooted phone,
    // this might be null!
    private final String androidRelease = Build.VERSION.RELEASE; // android version like 2.3.7
    private int satellitesInFix;

    public DataListener(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);
        this.telephonyManager = ((TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE));
        this.connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        this.db = DbHandler.get(context);

        // http://stackoverflow.com/questions/5499217/how-to-recognize-that-cyanogenmod-is-on-a-board/9801191
        this.osVersion = System.getProperty("os.version");
    }

    // private void networkInfo() {
    // ConnectivityManager service = (ConnectivityManager)
    // getSystemService(CONNECTIVITY_SERVICE);
    // NetworkInfo a = service.getActiveNetworkInfo();
    // if (a != null) {
    // String extraInfo = a.getExtraInfo(); // eplus.de
    // State state = a.getState(); //
    // http://developer.android.com/reference/android/net/NetworkInfo.State.html
    // String typeName = a.getTypeName(); // mobile
    // String subtypeName = a.getSubtypeName(); // HSDPA
    //
    // Log.i(LOG_TAG, "extra: " + extraInfo);
    // Log.i(LOG_TAG, "state: " + state.toString());
    // Log.i(LOG_TAG, "type: " + typeName);
    // Log.i(LOG_TAG, "subtype: " + subtypeName);
    // }
    //
    // TelephonyManager telephonyManager = ((TelephonyManager)
    // getSystemService(Context.TELEPHONY_SERVICE));
    // String carrier = telephonyManager.getNetworkOperatorName();
    // Log.i(LOG_TAG, "carrier: " + carrier);
    // }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            Log.i(LOG_TAG, "null location received, ignore.");
            return;
        }
        if (MobileStatusUtils.isAirplaneModeOn(context)) {
            Log.i(LOG_TAG, "we are in airplane mode, ignore.");
            return;
        }

        // I logged updates for timestamps ~12h ago right after a regular timestamp.
        // So reject all timestamps that seem to be older than an hour
        long age = Math.abs(location.getTime() - System.currentTimeMillis());
        if (age > 3600 * 1000) {
            Log.i(LOG_TAG, "out of date location ignored: "
                    + sdf.format(new Date(location.getTime())));
            return;
        }

        // signal information might be younger than the location
        SignalStrength signal = findSignalFor(location.getTime());
        if (signal == null) {
            Log.d(LOG_TAG, "no signal found. Can't save anything");
            return; // well, without signal information all this is rather useless
        }

        // keep roaming in mind!
        String carrier = telephonyManager.getNetworkOperatorName();
        db.save(location, signal, satellitesInFix, carrier, androidRelease,
                manufacturer, model, device, osVersion);
    }

    /**
     * Find most recent signal that is younger than the age og the location.
     * <p/>
     * S9 = Signal at timestamp 9, L7.5 = Location at 7.5</br>
     * S9 S8 L7.5 S7 S6 S5</br>
     * Find first signal that is younger than the age of the location</br>
     * Would be S7 in this case.
     *
     * @param timestamp of the location
     * @return the SignalStrength if one was found, null otherwise.
     */
    private SignalStrength findSignalFor(long timestamp) {
        for (SignalEntry entry : signalList) {
            if (timestamp > entry.time) {
                return entry.signal;
            }
        }
        return null;
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        Log.d(LOG_TAG, "signal strength update received. Keeping " + signalList.size() + " measures.");
        signalList.addFirst(new SignalEntry(signalStrength));
        while (signalList.size() > signalListMaxLength) {
            signalList.removeLast();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onGpsStatusChanged(int i) {
        int satellites = 0;
        int satellitesInFix = 0;
        int timetofix = locationManager.getGpsStatus(null).getTimeToFirstFix();
        for (GpsSatellite sat : locationManager.getGpsStatus(null).getSatellites()) {
            if (sat.usedInFix()) {
                satellitesInFix++;
            }
            satellites++;
        }
        Log.d(LOG_TAG, "Time to first fix = " + timetofix + "ms, Satellites: " + satellites + ", In last fix: " + satellitesInFix);
        this.satellitesInFix = satellitesInFix;
    }

    class SignalEntry {
        final long time = System.currentTimeMillis();
        final SignalStrength signal;

        public SignalEntry(SignalStrength s) {
            this.signal = s;
        }
    }
}
