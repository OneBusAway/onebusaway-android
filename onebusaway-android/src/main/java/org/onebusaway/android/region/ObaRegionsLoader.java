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
package org.onebusaway.android.region;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.RegionUtils;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import java.util.ArrayList;

public class ObaRegionsLoader extends AsyncTaskLoader<ArrayList<ObaRegion>> {
    //private static final String TAG = "ObaRegionsLoader";

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
     * @param force   Forces loading the regions from the remote repository.
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
        return RegionUtils.getRegions(mContext, mForceReload);
    }
}
