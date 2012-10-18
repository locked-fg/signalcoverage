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
import android.util.Log;

public class DataListener extends PhoneStateListener implements LocationListener {
    public static final String LOG_TAG = DataListener.class.getName();
    public static final String DB_NAME = "CellMapper";
    public static final String TABLE = "Base";

    private final Context context;
    private final LocationManager locationManager;

    private SignalStrength signal;

    public DataListener(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(LOG_TAG, "loction update received");

        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        int satellites = countSatellites(gpsStatus);

        Data data = new Data(location, signal, gpsStatus, satellites);
        DbHandler.save(data, context);
    }

    private int countSatellites(GpsStatus gpsStatus) {
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

    public class Data {
        public final Location location;
        public final SignalStrength signal;
        public final GpsStatus gpsStatus;
        public final int satellites;

        public Data(Location location, SignalStrength signal, GpsStatus gpsStatus, int satellites) {
            super();
            this.location = location;
            this.signal = signal;
            this.gpsStatus = gpsStatus;
            this.satellites = satellites;
        }
    }
}
