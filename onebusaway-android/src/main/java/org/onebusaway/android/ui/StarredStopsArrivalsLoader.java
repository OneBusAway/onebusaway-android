/*
 * Copyright (C) 2026 University of South Florida
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
package org.onebusaway.android.ui;

import android.content.Context;
import android.util.Log;

import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.util.ArrivalInfoUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import androidx.loader.content.AsyncTaskLoader;

/**
 * Loader that fetches arrival info for multiple starred stops in parallel.
 * Uses a null value in the result map to indicate a fetch error for a stop,
 * distinguishing it from an empty list (no upcoming arrivals).
 */
public class StarredStopsArrivalsLoader
        extends AsyncTaskLoader<HashMap<String, ArrayList<ArrivalInfo>>> {

    private static final String TAG = "StarredArrivalsLoader";

    private static final int MINUTES_AFTER = 35;

    private static final int MAX_ARRIVALS_PER_STOP = 3;

    private static final int THREAD_POOL_SIZE = 3;

    private final String[] mStopIds;

    private HashMap<String, ArrayList<ArrivalInfo>> mResult;

    public StarredStopsArrivalsLoader(Context context, String[] stopIds) {
        super(context);
        mStopIds = stopIds;
    }

    @Override
    public HashMap<String, ArrayList<ArrivalInfo>> loadInBackground() {
        HashMap<String, ArrayList<ArrivalInfo>> result = new HashMap<>();
        long now = System.currentTimeMillis();

        if (mStopIds.length == 0) {
            return result;
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(THREAD_POOL_SIZE, mStopIds.length));
        List<Future<StopArrivalsResult>> futures = new ArrayList<>();

        for (String stopId : mStopIds) {
            futures.add(executor.submit(new FetchArrivalsTask(getContext(), stopId, now)));
        }

        for (int i = 0; i < mStopIds.length; i++) {
            try {
                StopArrivalsResult stopResult = futures.get(i).get();
                result.put(stopResult.stopId, stopResult.arrivals);
            } catch (Exception e) {
                Log.w(TAG, "Failed to get result for stop " + mStopIds[i], e);
                result.put(mStopIds[i], null);
            }
        }

        executor.shutdown();
        return result;
    }

    @Override
    public void deliverResult(HashMap<String, ArrayList<ArrivalInfo>> data) {
        mResult = data;
        if (isStarted()) {
            super.deliverResult(data);
        }
    }

    @Override
    protected void onStartLoading() {
        if (mResult != null) {
            deliverResult(mResult);
        }
        if (takeContentChanged() || mResult == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();
        onStopLoading();
        mResult = null;
    }

    private static class StopArrivalsResult {

        final String stopId;

        final ArrayList<ArrivalInfo> arrivals;

        StopArrivalsResult(String stopId, ArrayList<ArrivalInfo> arrivals) {
            this.stopId = stopId;
            this.arrivals = arrivals;
        }
    }

    private static class FetchArrivalsTask implements Callable<StopArrivalsResult> {

        private final Context mContext;

        private final String mStopId;

        private final long mNow;

        FetchArrivalsTask(Context context, String stopId, long now) {
            mContext = context;
            mStopId = stopId;
            mNow = now;
        }

        @Override
        public StopArrivalsResult call() {
            ArrayList<ArrivalInfo> arrivals = new ArrayList<>();
            try {
                ObaArrivalInfoResponse response =
                        ObaArrivalInfoRequest.newRequest(mContext, mStopId, MINUTES_AFTER)
                                .call();
                if (response != null && response.getCode() == ObaApi.OBA_OK
                        && response.getArrivalInfo() != null) {
                    arrivals = ArrivalInfoUtils.convertObaArrivalInfo(
                            mContext, response.getArrivalInfo(), null, mNow, false);
                    Collections.sort(arrivals, new ArrivalInfoUtils.InfoComparator());
                    if (arrivals.size() > MAX_ARRIVALS_PER_STOP) {
                        arrivals = new ArrayList<>(
                                arrivals.subList(0, MAX_ARRIVALS_PER_STOP));
                    }
                } else if (response != null) {
                    Log.w(TAG, "API error for stop " + mStopId
                            + ": code=" + response.getCode());
                    return new StopArrivalsResult(mStopId, null);
                } else {
                    Log.w(TAG, "Null response for stop " + mStopId);
                    return new StopArrivalsResult(mStopId, null);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch arrivals for stop " + mStopId, e);
                return new StopArrivalsResult(mStopId, null);
            }
            return new StopArrivalsResult(mStopId, arrivals);
        }
    }
}
