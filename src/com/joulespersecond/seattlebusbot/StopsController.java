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
package com.joulespersecond.seattlebusbot;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;

/**
 * This class controls when and how the map refreshes the stops it is showing.
 * @author paulw
 */
public class StopsController {
    private static final String TAG = "StopsController";

    public interface Listener {
        public void onRequestFulfilled(ObaResponse response);
    }

    public static final class RequestInfo {
        private final String mRouteId;
        private final GeoPoint mCenter;
        private final int mLatSpan;
        private final int mLonSpan;
        private final int mZoomLevel;

        RequestInfo(String routeId,
                GeoPoint center,
                int latSpan,
                int lonSpan,
                int zoomLevel) {
            mRouteId = routeId;
            mCenter = center;
            mLatSpan = latSpan;
            mLonSpan = lonSpan;
            mZoomLevel = zoomLevel;
        }
        String getRouteId() {
            return mRouteId;
        }
        GeoPoint getCenter() {
            return mCenter;
        }
        int getLatSpan() {
            return mLatSpan;
        }
        int getLonSpan() {
            return mLonSpan;
        }
        int getZoomLevel() {
            return mZoomLevel;
        }
        @Override
        public String toString() {
            return "Request: Center=("+mCenter+") Zoom="+mZoomLevel+" Route="+mRouteId;
        }
    }

    private final AsyncTasks.ProgressIndeterminateVisibility mAsyncProgress;

    private class AsyncTask extends AsyncTasks.StringToResponse {
        private final RequestInfo mInfo;

        AsyncTask(RequestInfo info) {
            super(mAsyncProgress);
            mInfo = info;
        }

        @Override
        protected ObaResponse doInBackground(String... params) {
            if (mInfo.getRouteId() != null) {
                return ObaApi.getStopsForRoute(mActivity, mInfo.getRouteId());
            }
            else {
                return ObaApi.getStopsByLocation(mActivity,
                        mInfo.getCenter(),
                        0,
                        mInfo.getLatSpan(),
                        mInfo.getLonSpan(),
                        null,
                        0);
            }
        }
        @Override
        protected void doResult(ObaResponse result) {
            if (canFulfillRequest(mInfo)) {
                mCurrentResponse = result;
                mListener.onRequestFulfilled(result);
            }
            else {
                // Most a message back to ourselves in order
                // to start a new task
                mMyHandler.post(mStartNewTask);
            }

        }
    }

    private final Activity mActivity;
    private final Listener mListener;
    private AsyncTask mTask;
    private RequestInfo mCurrentRequest;
    private ObaResponse mCurrentResponse;
    private final Handler mMyHandler = new Handler();
    private final Runnable mStartNewTask = new Runnable() {
        @Override
        public void run() {
            startTask();
        }
    };

    StopsController(Activity activity, Listener listener) {
        mActivity = activity;
        mListener = listener;
        mAsyncProgress = new AsyncTasks.ProgressIndeterminateVisibility(activity);
    }

    void setNonConfigurationInstance(Object obj) {
        if (obj != null) {
            Object[] obj2 = (Object[])obj;
            mCurrentRequest = (RequestInfo)obj2[0];
            mCurrentResponse = (ObaResponse)obj2[1];
        }
    }
    Object onRetainNonConfigurationInstance() {
        return new Object[] { mCurrentRequest, mCurrentResponse };
    }

    void setCurrentRequest(RequestInfo info) {
        assert(info != null);
        if (!canFulfillRequest(info)) {
            mCurrentRequest = info;
            // Start the task if it isn't running.
            // If it is running, wait for it to complete and it
            // will restart itself.
            if (!AsyncTasks.isRunning(mTask)) {
                startTask();
            }
        }
    }
    private void startTask() {
        Log.d(TAG, "StartTask: " + mCurrentRequest);
        mTask = new AsyncTask(mCurrentRequest);
        mTask.execute();
    }

    RequestInfo getCurrentResponse() {
        return mCurrentRequest;
    }
    void cancel() {
        // Cancel the async task
        if (mTask != null) {
            mTask.cancel(true);
            mTask = null;
        }
    }

    ObaResponse getResponse() {
        return mCurrentResponse;
    }

    /**
     * Returns true if the current request can fulfill this request.
     * @return
     */
    boolean canFulfillRequest(RequestInfo info) {
        Log.d(TAG, "CurrentRequest: " + mCurrentRequest);
        Log.d(TAG, "Candidate: " + info);
        if (mCurrentRequest == null) {
            return false;
        }
        if (info == null) {
            return true;
        }
        // This is the old logic, we can do better:
        if (!mCurrentRequest.getCenter().equals(info.getCenter())) {
            return false;
        }
        if (mCurrentResponse != null) {
            final int oldZoom = mCurrentRequest.getZoomLevel();
            final int newZoom = info.getZoomLevel();
            if ((newZoom > oldZoom) &&
                    mCurrentResponse.getData().getLimitExceeded()) {
                return false;
            }
            else if (newZoom < oldZoom) {
                return false;
            }
        }
        return true;


        // Otherwise:

        // If the new request's is zoomed in and the current
        // response has limitExceeded, then no.

        // If the new request's lat/lon span is contained
        // entirely within the old one:
        //  Then the new request is fulfilled IFF the old request's
        //  limitExceeded == false.

        // If the new request's lat/lon span is not contained
        // entirely within the old one (fuzzy match)
        //  FALSE

    }

    static RequestInfo requestFromView(MapView view, String routeId) {
        return new RequestInfo(routeId,
                view.getMapCenter(),
                view.getLatitudeSpan(),
                view.getLongitudeSpan(),
                view.getZoomLevel());
    }
}
