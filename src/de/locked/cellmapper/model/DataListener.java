package de.locked.cellmapper.model;

import java.util.Iterator;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DataListener extends PhoneStateListener implements LocationListener {
    public static final String LOG_TAG = DataListener.class.getName();

    private final Context context;
    private final LocationManager locationManager;

    private SignalStrength signal;

    public DataListener(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            Log.i(LOG_TAG, "null location received, ignoring");
            return;
        }
        Log.i(LOG_TAG, "location update received");

        TelephonyManager telephonyManager = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
        String carrier = telephonyManager.getNetworkOperatorName();

        int satellites = countSatellites();
        Data data = new Data(location, signal, satellites, carrier);
        DbHandler.save(data, context);
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
