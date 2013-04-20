package de.locked.cellmapper.exporter;

import java.util.Random;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import de.locked.cellmapper.CellMapperMain;
import de.locked.cellmapper.R;
import de.locked.cellmapper.model.DbHandler;

/**
 * Async task that queries the database and saves the result using the
 * DataExporter while updating the given progress bar
 */
public abstract class AbstractAsyncExporterTask extends AsyncTask<Void, Integer, Void> {
    @SuppressWarnings("unused")
    private static final String LogTag = AbstractAsyncExporterTask.class.getName();
    private final Context context;
    private final String message;
    private final int icon;
    //
    protected final Cursor cursor;
    protected final int max;
    private final String headline;
    private final int notificationId;

    /**
     * @param c the context
     * @param messageId the R.message.id
     * @param icon the icon id
     */
    public AbstractAsyncExporterTask(Context c, int messageId, int icon) {
        this.context = c;
        this.icon = icon;
        DbHandler db = DbHandler.get(context);
        this.cursor = db.getAll();
        this.max = db.getRows();
        this.message = c.getString(messageId);
        this.headline = c.getString(R.string.exportNotificationHeadline);
        this.notificationId = new Random().nextInt();
    }

    protected Context getContext() {
        return context;
    }

    @Override
    protected void onProgressUpdate(Integer... i) {
        notify(String.format(message, i[0]));
    }

    protected void notify(String msg) {
        notify(msg, icon);
    }

    protected void notify(String msg, int iconId) {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context).setSmallIcon(iconId)
                .setContentTitle(headline).setContentText(msg);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(context, CellMapperMain.class);
        // The stack builder object will contain an artificial back stack for
        // the started Activity.This ensures that navigating backward from the
        // Activity leads out of your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(CellMapperMain.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(notificationId, mBuilder.build());
    }

    @Override
    protected void onPreExecute() {
        onProgressUpdate(0);
    }

    // @Override
    // protected void onCancelled() {}
    //
    // @Override
    // protected void onPostExecute(Void result) {}
}