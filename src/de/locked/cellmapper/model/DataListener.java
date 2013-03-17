package de.locked.cellmapper.model;

import java.util.Iterator;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DataListener extends PhoneStateListener implements LocationListener {
    public static final String LOG_TAG = DataListener.class.getName();

    private final Context context;
    private final LocationManager locationManager;
    private final TelephonyManager telephonyManager;
//    private final ConnectivityManager connectivityManager;

    private SignalStrength signal;

    public DataListener(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.telephonyManager = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
//        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            Log.i(LOG_TAG, "null location received, ignoring");
            return;
        }
        if (isAirplaneModeOn()) {
            Log.i(LOG_TAG, "we are in airplane mode, ignore");
            return;
        }

        Log.i(LOG_TAG, "location update received");
        {
//            // http://stackoverflow.com/questions/5499217/how-to-recognize-that-cyanogenmod-is-on-a-board/9801191
//            // os stuff
//            int sdkInt = Build.VERSION.SDK_INT; // API version
//            String release = Build.VERSION.RELEASE; // android version like
//                                                    // 2.3.7
//
//            // device stuff
//            String device = Build.DEVICE; // bravo
//            String manufacturer = Build.MANUFACTURER; // HTC
//            String model = Build.MODEL; // HTC Desire
//
//            // os version: for example 2.6.37.6-cyanogenmod-01509-g8913be8
//            String version = System.getProperty("os.version");
        }

        String carrier = telephonyManager.getNetworkOperatorName();
        int satellites = countSatellites();
        Data data = new Data(location, signal, satellites, carrier);
        DbHandler.save(data, context);
    }

    /**
     * Gets the state of Airplane Mode.
     * 
     * @return true if enabled.
     */
    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private int countSatellites() {
        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        int i = 0;
        if (gpsStatus != null) {
            Iterator<GpsSatellite> it = gpsStatus.getSatellites().iterator();
            while (it.hasNext()) {
                i++;
            }
        }
        return i;
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        Log.d(LOG_TAG, "signal strength update received");
        this.signal = signalStrength;
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
}
