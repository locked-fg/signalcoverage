package de.locked.cellmapper.exporter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import android.os.AsyncTask;
import android.widget.ProgressBar;

/**
 * Async task that queries the database and saves the result using the
 * DataExporter while updating the given progress bar
 */
public class AsyncExporterTask extends AsyncTask<Void, Integer, Void> {
    private final ProgressBar mProgress;
    private final DataExporter saver;
    private final int max;

    public AsyncExporterTask(ProgressBar mProgress, int max, DataExporter saver) {
        this.mProgress = mProgress;
        this.saver = saver;
        this.max = max;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (mProgress != null) {
//            saver.addPropertyChangeListener(new ProgressUpdater(mProgress));
//            saver.addPropertyChangeListener(this);
            saver.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent event) {
                    publishProgress((Integer) event.getNewValue());
                }
            });
        }

        saver.process();
        return null;
    }

    @Override
    protected void onProgressUpdate(Integer... i){
        if (i.length > 0){
            mProgress.setProgress(i[0]);
        }
    }
    
    @Override
    protected void onPostExecute(Void result) {
        mProgress.setVisibility(ProgressBar.GONE);
    }

    @Override
    protected void onPreExecute() {
        mProgress.setVisibility(ProgressBar.VISIBLE);
        mProgress.setMax(max);
    }

}