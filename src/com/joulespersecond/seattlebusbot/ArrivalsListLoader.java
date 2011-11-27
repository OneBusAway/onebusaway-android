package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.request.ObaArrivalInfoRequest;
import com.joulespersecond.oba.request.ObaArrivalInfoResponse;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;


class ArrivalsListLoader extends AsyncTaskLoader<ObaArrivalInfoResponse> {
    private final String mStopId;
    private ObaArrivalInfoResponse mLastGoodResponse;
    private long mLastGoodResponseTime = 0;

    public ArrivalsListLoader(Context context, String stopId) {
        super(context);
        mStopId = stopId;
    }

    @Override
    public ObaArrivalInfoResponse loadInBackground() {
        return ObaArrivalInfoRequest.newRequest(getContext(), mStopId).call();
    }

    @Override
    public void deliverResult(ObaArrivalInfoResponse data) {
        if (data.getCode() == ObaApi.OBA_OK) {
            mLastGoodResponse = data;
            mLastGoodResponseTime = System.currentTimeMillis();
        }
        super.deliverResult(data);
    }

    public ObaArrivalInfoResponse getLastGoodResponse() {
        return mLastGoodResponse;
    }

    public long getLastGoodResponseTime() {
        return mLastGoodResponseTime;
    }

    @Override
    protected void onStartLoading() {
        // TODO: If the last good response time is < 1 second
        // just use that.
        forceLoad();
    }

    /**
     * Handles a request to stop the Loader.
     */
    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    @Override
    protected void onReset() {
        super.onReset();
        mLastGoodResponse = null;
        mLastGoodResponseTime = 0;
        // Ensure the loader is stopped
        onStopLoading();
    }
}
