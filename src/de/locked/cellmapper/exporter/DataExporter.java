package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;

public interface DataExporter {
    public static final String EVT_ERROR = "error";
    public static final String EVT_STATUS = "status";

    public abstract void addPropertyChangeListener(PropertyChangeListener listener);

    public abstract void process();

}