package de.locked.cellmapper.exporter;

import android.os.AsyncTask;
import android.widget.ProgressBar;
import de.locked.cellmapper.ProgressUpdater;

/**
 * Async task that queries the database and saves the result using the
 * DataExporter while updating the given progress bar
 */
public class AsyncExporterTask extends AsyncTask<Void, Void, Void> {
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
            saver.addPropertyChangeListener(new ProgressUpdater(mProgress));
        }

        saver.process();
        return null;
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