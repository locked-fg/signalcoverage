package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import android.os.AsyncTask;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Async task that queries the database and saves the result using the
 * DataExporter while updating the given progress bar
 */
public class AsyncExporterTask extends AsyncTask<Void, Integer, Void> {
    private final ProgressBar mProgress;
    private final DataExporter saver;
    private final int max;
    private String error = null;

    public AsyncExporterTask(ProgressBar mProgress, int max, DataExporter saver) {
        this.mProgress = mProgress;
        this.saver = saver;
        this.max = max;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (mProgress != null) {
            saver.addPropertyChangeListener(new PropertyChangeListener() {

                @Override
                public void propertyChange(PropertyChangeEvent event) {
                    String pName = event.getPropertyName();
                    if (pName.equals(DataExporter.EVT_STATUS)) {
                        publishProgress((Integer) event.getNewValue());
                    } else if (pName.equals(DataExporter.EVT_ERROR)){
                        error = event.getNewValue().toString();
                    }
                }
            });
        }

        saver.process();
        return null;
    }

    @Override
    protected void onProgressUpdate(Integer... i) {
        if (i.length > 0) {
            mProgress.setProgress(i[0]);
        }
    }

    @Override
    protected void onPostExecute(Void result) {
        mProgress.setVisibility(ProgressBar.GONE);
        if (error != null){
            Toast.makeText(mProgress.getContext(), "an Error occured: "+error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPreExecute() {
        mProgress.setVisibility(ProgressBar.VISIBLE);
        mProgress.setMax(max);
    }

}