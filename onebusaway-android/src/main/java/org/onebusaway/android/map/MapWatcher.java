/*
 * Copyright (C) 2011-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.map;

import org.onebusaway.android.util.LocationUtils;

import android.location.Location;
import android.os.Handler;

/**
 * Because the map object doesn't seem to have callbacks when the map
 * center or zoom is changed, we have our own watcher for it.
 *
 * @author paulw
 */
public class MapWatcher {

    public interface Listener {

        public void onMapCenterChanging();

        public void onMapCenterChanged();

        public void onMapZoomChanging();

        public void onMapZoomChanged();
    }

    private static final int WAIT_TIME = 1000;

    private static final int POLL_TIME = 250;

    private final MapModeController.ObaMapView mObaMapView;

    private final Handler mHandler;

    private final Listener mListener;

    private Location mCurrentCenter;

    private long mCurrentCenterMillis;

    private float mCurrentZoom;

    private long mCurrentZoomMillis;

    private final Runnable mChecker = new Runnable() {
        @Override
        public void run() {
            Location newCenter = mObaMapView.getMapCenterAsLocation();
            float newZoom = mObaMapView.getZoomLevelAsFloat();

            final boolean centerChanged = !LocationUtils.fuzzyEquals(newCenter, mCurrentCenter);
            final boolean zoomChanged = newZoom != mCurrentZoom;

            final long now = System.currentTimeMillis();
            if (centerChanged) {
                mCurrentCenterMillis = now;
                mListener.onMapCenterChanging();
                mCurrentCenter = newCenter;
            } else if (mCurrentCenterMillis != 0 && (now - mCurrentCenterMillis) > WAIT_TIME) {
                mListener.onMapCenterChanged();
                mCurrentCenterMillis = 0;
            }
            if (zoomChanged) {
                mCurrentZoomMillis = now;
                mListener.onMapZoomChanging();
                mCurrentZoom = newZoom;
            } else if (mCurrentZoomMillis != 0 && (now - mCurrentZoomMillis) > WAIT_TIME) {
                mListener.onMapZoomChanged();
                mCurrentZoomMillis = 0;
            }
            mHandler.postDelayed(mChecker, POLL_TIME);
        }
    };

    public MapWatcher(MapModeController.ObaMapView view, Listener listener) {
        mObaMapView = view;
        mHandler = new Handler();
        mListener = listener;
    }

    /**
     * Start watching.
     */
    public void start() {
        mCurrentCenter = mObaMapView.getMapCenterAsLocation();
        mCurrentZoom = mObaMapView.getZoomLevelAsFloat();
        mHandler.postDelayed(mChecker, POLL_TIME);
    }

    /**
     * Stop watching.
     */
    public void stop() {
        mHandler.removeCallbacks(mChecker);
    }

    /**
     * Check to see if anything changed now.
     * Fires the listener events if so.
     */
    public void checkNow() {
        mHandler.removeCallbacks(mChecker);
        mChecker.run();
    }
}
