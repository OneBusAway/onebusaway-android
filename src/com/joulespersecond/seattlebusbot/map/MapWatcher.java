/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot.map;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;

import android.os.Handler;

/**
 * Because the map object doesn't seem to have callbacks when the map
 * center or zoom is changed, we have our own watcher for it.
 *
 * @author paulw
 *
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

    private final MapView mMapView;
    private final Handler mHandler;
    private final Listener mListener;

    private GeoPoint mCurrentCenter;
    private long mCurrentCenterMillis;
    private int mCurrentZoom;
    private long mCurrentZoomMillis;

    private final Runnable mChecker = new Runnable() {
        @Override
        public void run() {
            GeoPoint newCenter = mMapView.getMapCenter();
            int newZoom = mMapView.getZoomLevel();

            // TODO: Allow for a "fuzzy equals" for the center, so
            // we don't report changes that are less than some epsilon.
            final boolean centerChanged = !newCenter.equals(mCurrentCenter);
            final boolean zoomChanged = newZoom != mCurrentZoom;

            final long now = System.currentTimeMillis();
            if (centerChanged) {
                mCurrentCenterMillis = now;
                mListener.onMapCenterChanging();
                mCurrentCenter = newCenter;
            }
            else if (mCurrentCenterMillis != 0 && (now-mCurrentCenterMillis) > WAIT_TIME) {
                mListener.onMapCenterChanged();
                mCurrentCenterMillis = 0;
            }
            if (zoomChanged) {
                mCurrentZoomMillis = now;
                mListener.onMapZoomChanging();
                mCurrentZoom = newZoom;
            }
            else if (mCurrentZoomMillis != 0 && (now-mCurrentZoomMillis) > WAIT_TIME) {
                mListener.onMapZoomChanged();
                mCurrentZoomMillis = 0;
            }
            mHandler.postDelayed(mChecker, POLL_TIME);
        }
    };

    public MapWatcher(MapView view, Listener listener) {
        mMapView = view;
        mHandler = new Handler();
        mListener = listener;
    }

    /**
     * Start watching.
     */
    public void start() {
        mCurrentCenter = mMapView.getMapCenter();
        mCurrentZoom = mMapView.getZoomLevel();
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
