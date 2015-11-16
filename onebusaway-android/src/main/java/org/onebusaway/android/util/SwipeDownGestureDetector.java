package org.onebusaway.android.util;

import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * Gesture detector that detects swipe down.  Based on http://stackoverflow.com/a/938657/937715.
 *
 * @author barbeau
 */
public class SwipeDownGestureDetector extends GestureDetector.SimpleOnGestureListener {

    public interface Listener {

        /**
         * Called when the detector detects a swipe down
         */
        void onSwipeDown();
    }

    private static final String TAG = "SwipeDownDetect";

    private static final int SWIPE_MIN_DISTANCE = 120;

    private static final int SWIPE_MAX_OFF_PATH = 250;

    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    private Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        try {
            if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
                return false;
            }
            // right to left swipe
            if (e2.getY() - e1.getY() > SWIPE_MIN_DISTANCE
                    && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                Log.d(TAG, "Swipe down");
                if (mListener != null) {
                    mListener.onSwipeDown();
                }
            }
        } catch (Exception e) {
            // nothing
        }
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }
}
