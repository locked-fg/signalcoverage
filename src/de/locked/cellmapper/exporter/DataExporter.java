package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;
import java.io.IOException;

public interface DataExporter {
    public static final String EVT_STATUS = "status";

    public abstract void addPropertyChangeListener(PropertyChangeListener listener);

    public abstract void process() throws IOException;

}