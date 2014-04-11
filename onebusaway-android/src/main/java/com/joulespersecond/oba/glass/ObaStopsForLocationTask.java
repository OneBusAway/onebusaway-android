/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com) 
 * and individual contributors
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
package com.joulespersecond.oba.glass;

import com.joulespersecond.oba.request.ObaStopsForLocationResponse;

import android.app.ProgressDialog;
import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;

/**
 * AsyncTask used to refresh region info from the Regions REST API.
 *
 * Classes utilizing this task can request a callback via MapModeController.Callback.setMyLocation()
 * by passing in class implementing MapModeController.Callback in the constructor
 *
 * @author barbeau
 */
public class ObaStopsForLocationTask extends AsyncTask<Void, Integer, ObaStopsForLocationResponse> {

    public interface Callback {

        /**
         * Called when the ObaStopsForLocationTask is complete
         *
         * @param response response from the server
         */
        public void onTaskFinished(ObaStopsForLocationResponse response);
    }

    private static final String TAG = "ObaStopsForLocationTask";

    private final int CALLBACK_DELAY = 100;  //in milliseconds

    private Context mContext;

    private ProgressDialog mProgressDialog;

    private ObaStopsForLocationTask.Callback mCallback;

    private Location location;

    /**
     * @param callback a callback will be made via this interface after the task is complete
     *                 (null if no callback is requested)
     */
    public ObaStopsForLocationTask(Context context, ObaStopsForLocationTask.Callback callback,
            Location location) {
        this.mContext = context;
        this.mCallback = callback;
        this.location = location;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected ObaStopsForLocationResponse doInBackground(Void... params) {
        return new ObaGlassStopsForLocationRequest.Builder(mContext, location)
                .build()
                .call();
    }

    @Override
    protected void onPostExecute(ObaStopsForLocationResponse results) {
        if (mCallback != null) {
            mCallback.onTaskFinished(results);
        }

        super.onPostExecute(results);
    }
}
