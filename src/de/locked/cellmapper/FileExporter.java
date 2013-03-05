package de.locked.cellmapper;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.widget.ProgressBar;
import de.locked.cellmapper.model.DbHandler;
import de.locked.cellmapper.model.Filesaver;

/**
 * Async task that queries the database and saves the result using the Filesaver while updating the given progress bar
 * 
 * @see Filesaver
 */
public class FileExporter extends AsyncTask<Void, Void, Void> {
    private final ProgressBar mProgress;
    private final Context context;
    
    public FileExporter(ProgressBar mProgress, Context context) {
        this.mProgress = mProgress;
        this.context = context;
    }
    
    @Override
    protected Void doInBackground(Void... params) {
        Filesaver saver = new Filesaver();
        saver.addPropertyChangeListener(new ProgressUpdater(mProgress));

        Cursor cursor = DbHandler.getAll(context);
        saver.saver("CellMapper/data", cursor);
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