package de.locked.cellmapper.exporter;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import de.locked.cellmapper.model.DbHandler;

/**
 * Async task that queries the database and saves the result using the
 * DataExporter while updating the given progress bar
 */
public abstract class AbstractAsyncExporterTask extends AsyncTask<Void, Integer, Void> {
    @SuppressWarnings("unused")
    private static final String LogTag = AbstractAsyncExporterTask.class.getName();
    private final ProgressBar mProgress;
    private final View progressRow;
    private final String error = null;
    //
    protected final Cursor cursor;
    protected final int max;

    public AbstractAsyncExporterTask(View progressRow, ProgressBar mProgress) {
        this.progressRow = progressRow;
        this.mProgress = mProgress;
        
        Context context = progressRow.getContext();
        this.cursor = DbHandler.getAll(context);
        this.max = DbHandler.getRows(context);
    }

    @Override
    protected void onProgressUpdate(Integer... i) {
        if (i.length > 0) {
            mProgress.setProgress(i[0]);
        }
    }

    @Override
    protected void onCancelled() {
        done();
    }

    @Override
    protected void onPostExecute(Void result) {
        done();
    }

    private void done() {
        progressRow.setVisibility(View.GONE);
        if (error != null) {
            Toast.makeText(mProgress.getContext(), "an Error occured: " + error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPreExecute() {
        progressRow.setVisibility(ProgressBar.VISIBLE);
        mProgress.setMax(100);
        mProgress.setProgress(0);
    }

}