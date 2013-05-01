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
    private final TelephonyManager telephonyManager;
    private final DbHandler db;
    private GpsStatus gpsStatus = null;
//    private final ConnectivityManager connectivityManager;

    private SignalStrength signal;

    public DataListener(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.telephonyManager = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
//        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        db = DbHandler.get(context);
    }

//  private void networkInfo() {
//  ConnectivityManager service = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
//  NetworkInfo a = service.getActiveNetworkInfo();
//  if (a != null) {
//      String extraInfo = a.getExtraInfo(); // eplus.de
//      State state = a.getState(); // http://developer.android.com/reference/android/net/NetworkInfo.State.html
//      String typeName = a.getTypeName(); // mobile
//      String subtypeName = a.getSubtypeName(); // HSDPA
//
//      Log.i(LOG_TAG, "extra: " + extraInfo);
//      Log.i(LOG_TAG, "state: " + state.toString());
//      Log.i(LOG_TAG, "type: " + typeName);
//      Log.i(LOG_TAG, "subtype: " + subtypeName);
//  }
//
//  TelephonyManager telephonyManager = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));
//  String carrier = telephonyManager.getNetworkOperatorName();
//  Log.i(LOG_TAG, "carrier: " + carrier);
//}
    
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

        Log.d(LOG_TAG, "location update received");
//        {
//            
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
//        }

        String carrier = telephonyManager.getNetworkOperatorName();
        int satellites = countSatellites();
        Data data = new Data(location, signal, satellites, carrier);
        db.save(data);
    }

    private int countSatellites() {
        gpsStatus = locationManager.getGpsStatus(gpsStatus);
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
