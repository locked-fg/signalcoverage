package de.locked.cellmapper.exporter;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import de.locked.cellmapper.ProgressUpdater;
import de.locked.cellmapper.model.DbHandler;

/**
 * Async task that queries the database and saves the result using the
 * DataExporter while updating the given progress bar
 */
public class AsyncFileExporterTask extends AsyncTask<Void, Void, Void> {
    private static final String LOG_TAG = AsyncFileExporterTask.class.getName();

    private final ProgressBar mProgress;
    private final Context context;
    private final DataExporter saver;

//    public AsyncFileExporterTask(ProgressBar mProgress, Context context, DataExporter saver) {
//        this.mProgress = mProgress;
//        this.context = context;
//        this.saver = saver;
//    }

    public AsyncFileExporterTask(int progressBarId, Activity context, DataExporter saver) {
        View bar = context.findViewById(progressBarId);
        if (bar == null) {
            Log.i(LOG_TAG, "progress bar id does not identify a view, using null");
            this.mProgress = null;
        } else if (!bar.getClass().isAssignableFrom(ProgressBar.class)) {
            throw new IllegalArgumentException("ID does not identify a progress bar");
        } else {
            this.mProgress = (ProgressBar) context.findViewById(progressBarId);
        }
        
        this.context = context;
        this.saver = saver;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (mProgress != null) {
            saver.addPropertyChangeListener(new ProgressUpdater(mProgress));
        }

        Cursor cursor = DbHandler.getAll(context);
        saver.process(cursor);
        cursor.close();

        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        mProgress.setVisibility(ProgressBar.GONE);
    }

    @Override
    protected void onPreExecute() {
        mProgress.setVisibility(ProgressBar.VISIBLE);
        mProgress.setMax(DbHandler.getRows(context));
    }

}