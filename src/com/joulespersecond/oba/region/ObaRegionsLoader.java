/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba.region;

import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.elements.ObaRegionElement;
import com.joulespersecond.oba.provider.ObaContract.RegionBounds;
import com.joulespersecond.oba.provider.ObaContract.Regions;
import com.joulespersecond.oba.request.ObaRegionsRequest;
import com.joulespersecond.oba.request.ObaRegionsResponse;
import com.joulespersecond.seattlebusbot.BuildConfig;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ObaRegionsLoader extends AsyncTaskLoader<ArrayList<ObaRegion>> {
    private static final String TAG = "ObaRegionsLoader";

    private Context mContext;
    private ArrayList<ObaRegion> mResults;
    private final boolean mForceReload;

    public ObaRegionsLoader(Context context) {
        super(context);
        this.mContext = context;
        mForceReload = false;
    }

    /**
     * @param context The context.
     * @param force Forces loading the regions from the remote repository.
     */
    public ObaRegionsLoader(Context context, boolean force) {
        super(context);
        this.mContext = context;
        mForceReload = force;
    }

    @Override
    protected void onStartLoading() {
        if (mResults != null) {
            deliverResult(mResults);
        } else {
            forceLoad();
        }
    }

    @Override
    public ArrayList<ObaRegion> loadInBackground() {
        ArrayList<ObaRegion> results;
        if (!mForceReload) {
            //
            // Check the DB
            //
            results = RegionUtils.getRegionsFromProvider(mContext);
            if (results != null) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions from database."); }
                return results;
            }
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from database was null."); }
        }

        results = RegionUtils.getRegionsFromServer(mContext);
        if (results == null) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from server was null."); }
            return null;
        }

        if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions list from server."); }
        
        RegionUtils.saveToProvider(mContext,results);
        return results;
    }
}
