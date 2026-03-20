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

import androidx.loader.content.AsyncTaskLoader;

/**
 * Loader that fetches arrival info for multiple starred stops in the background.
 */
public class StarredStopsArrivalsLoader
        extends AsyncTaskLoader<HashMap<String, ArrayList<ArrivalInfo>>> {

    private static final String TAG = "StarredArrivalsLoader";

    private static final int MINUTES_AFTER = 35;

    private static final int MAX_ARRIVALS_PER_STOP = 3;

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

        for (String stopId : mStopIds) {
            ArrayList<ArrivalInfo> arrivals = new ArrayList<>();
            try {
                ObaArrivalInfoResponse response =
                        ObaArrivalInfoRequest.newRequest(getContext(), stopId, MINUTES_AFTER)
                                .call();
                if (response != null && response.getCode() == ObaApi.OBA_OK
                        && response.getArrivalInfo() != null) {
                    arrivals = ArrivalInfoUtils.convertObaArrivalInfo(
                            getContext(), response.getArrivalInfo(), null, now, false);
                    Collections.sort(arrivals, new ArrivalInfoUtils.InfoComparator());
                    if (arrivals.size() > MAX_ARRIVALS_PER_STOP) {
                        arrivals = new ArrayList<>(
                                arrivals.subList(0, MAX_ARRIVALS_PER_STOP));
                    }
                } else if (response != null) {
                    Log.w(TAG, "API error for stop " + stopId
                            + ": code=" + response.getCode());
                } else {
                    Log.w(TAG, "Null response for stop " + stopId);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch arrivals for stop " + stopId, e);
            }
            result.put(stopId, arrivals);
        }
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
}
