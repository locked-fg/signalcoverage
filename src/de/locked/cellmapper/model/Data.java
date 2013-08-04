package de.locked.cellmapper.model;

import android.location.Location;
import android.telephony.SignalStrength;

public class Data {
    public final Location location;
    public final SignalStrength signal;
    public final int satellites;
    public final String carrier;
    public final String androidRelease;

    public Data(Location location, SignalStrength signal, int satellites, String carrier, String androidRelease) {
        this.location = location;
        this.signal = signal;
        this.satellites = satellites;
        this.carrier = carrier;
        this.androidRelease = androidRelease;
    }
}