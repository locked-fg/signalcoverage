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
    private final int maxLength = 100;
    private final LinkedList<SignalEntry> ll = new LinkedList<SignalEntry>();
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
        if (Utils.isAirplaneModeOn(context)) {
            Log.i(LOG_TAG, "we are in airplane mode, ignore.");
            return;
        }

        long age = Math.abs(location.getTime() - System.currentTimeMillis()) / 1000;
        // strange: I logged updates for timestamps ~12h ago right after a
        // regular timestamp
        if (age > 3600 * 1000) {
            Log.i(LOG_TAG,
                    "out of date location ignored: "
                            + sdf.format(new Date(location.getTime())));
            return;
        }

        SignalStrength signal = null;
        for (int i = 0; i < ll.size() && signal == null; i++) {
            if (location.getTime() > ll.get(i).time) {
                signal = ll.get(i).signal;
            }
        }
        if (signal == null) {
            return;
        }

        String carrier = telephonyManager.getNetworkOperatorName();
        int satellites = countSatellites();

        db.save(location, signal, satellites, carrier, androidRelease,
                manufacturer, model, device, osVersion);
    }

    private int countSatellites() {
//        onGpsStatusChanged(0);
//        GpsStatus gpsstatus = locationManager.getGpsStatus(null);
//        if (gpsstatus == null) {
//            return 0;
//        }
//
//        int satellitesInFix = 0;
//        Iterator<GpsSatellite> it = gpsstatus.getSatellites().iterator();
//        while (it.hasNext()) {
//            satellitesInFix++;
//        }
        return satellitesInFix;
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        ll.addFirst(new SignalEntry(signalStrength));
        while (ll.size() > maxLength) {
            ll.removeLast();
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
            if(sat.usedInFix()) {
                satellitesInFix++;
            }
            satellites++;
        }
        Log.i(LOG_TAG, "Time to first fix = "+timetofix+"ms, Satellites: "+satellites + ", In last fix: "+satellitesInFix);
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
