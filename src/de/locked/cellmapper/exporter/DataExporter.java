package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeListener;

import android.database.Cursor;

public interface DataExporter {

    public abstract void addPropertyChangeListener(PropertyChangeListener listener);

    public abstract void process(Cursor cursor);

}