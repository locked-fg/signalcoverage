//package de.locked.cellmapper;
//
//import java.beans.PropertyChangeEvent;
//import java.beans.PropertyChangeListener;
//
//import android.os.Handler;
//import android.widget.ProgressBar;
//
///**
// * class to update a progressbar from a property change listener.
// * 
// * @author Franz
// */
//public class ProgressUpdater implements PropertyChangeListener {
//
//    private final Handler mHandler = new Handler();
//    // may be null!
//    private final ProgressBar mProgress;
//
//    /**
//     * Null safe constructor
//     * 
//     * @param mProgress
//     */
//    public ProgressUpdater(ProgressBar mProgress) {
//        this.mProgress = mProgress;
//    }
//
//    @Override
//    public void propertyChange(final PropertyChangeEvent event) {
//        mHandler.post(new Runnable() {
//
//            @Override
//            public void run() {
//                if (mProgress != null) {
//                    mProgress.setProgress((Integer) event.getNewValue());
//                }
//            }
//        });
//    }
//
//}