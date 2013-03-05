package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;

public interface DataExporter {

    public abstract void addPropertyChangeListener(PropertyChangeListener listener);

    public abstract void process();

}