package de.locked.cellmapper.model;

import android.location.Location;
import android.telephony.SignalStrength;

public class Data {
    public final Location location;
    public final SignalStrength signal;
    public final int satellites;
    public final String carrier;

    public Data(Location location, SignalStrength signal, int satellites, String carrier) {
        this.location = location;
        this.signal = signal;
        this.satellites = satellites;
        this.carrier = carrier;
    }
}