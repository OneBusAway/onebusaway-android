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

    private ArrayList<ObaRegion> mResults;
    private final boolean mForceReload;

    public ObaRegionsLoader(Context context) {
        super(context);
        mForceReload = false;
    }

    /**
     * @param context The context.
     * @param force Forces loading the regions from the remote repository.
     */
    public ObaRegionsLoader(Context context, boolean force) {
        super(context);
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
            results = getRegionsFromProvider();
            if (results != null) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions from database."); }
                return results;
            }
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from database was null."); }
        }

        results = getRegionsFromServer();
        if (results == null) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from server was null."); }
            return null;
        }

        if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions list from server."); }
        
        saveToProvider(results);
        return results;
    }

    private ArrayList<ObaRegion> getRegionsFromProvider() {
        // Prefetch the bounds to limit the number of DB calls.
        HashMap<Long, ArrayList<ObaRegionElement.Bounds>> allBounds = getBoundsFromProvider();

        Cursor c = null;
        try {
            final String[] PROJECTION = {
                Regions._ID,
                Regions.NAME,
                Regions.OBA_BASE_URL,
                Regions.SIRI_BASE_URL,
                Regions.LANGUAGE,
                Regions.CONTACT_EMAIL,
                Regions.SUPPORTS_OBA_DISCOVERY,
                Regions.SUPPORTS_OBA_REALTIME,
                Regions.SUPPORTS_SIRI_REALTIME
            };

            ContentResolver cr = getContext().getContentResolver();
            c = cr.query(Regions.CONTENT_URI, PROJECTION, null, null, Regions._ID);
            if (c == null) {
                return null;
            }
            if (c.getCount() == 0) {
                c.close();
                return null;
            }
            ArrayList<ObaRegion> results = new ArrayList<ObaRegion>();

            c.moveToFirst();
            do {
                long id = c.getLong(0);
                ArrayList<ObaRegionElement.Bounds> bounds = allBounds.get(id);
                ObaRegionElement.Bounds[] bounds2 = (bounds != null) ?
                        bounds.toArray(new ObaRegionElement.Bounds[] {}) :
                        null;

                results.add(new ObaRegionElement(id,   // id
                    c.getString(1),             // Name
                    true,                       // Active
                    c.getString(2),             // OBA Base URL
                    c.getString(3),             // SIRI Base URL
                    bounds2,                    // Bounds
                    c.getString(4),             // Lang
                    c.getString(5),             // Contact Email
                    c.getInt(6) > 0,            // Supports Oba Discovery
                    c.getInt(7) > 0,            // Supports Oba Realtime
                    c.getInt(8) > 0             // Supports Siri Realtime
                ));

            } while (c.moveToNext());

            return results;

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private HashMap<Long, ArrayList<ObaRegionElement.Bounds>> getBoundsFromProvider() {
        // Prefetch the bounds to limit the number of DB calls.
        Cursor c = null;
        try {
            final String[] PROJECTION = {
                RegionBounds.REGION_ID,
                RegionBounds.LATITUDE,
                RegionBounds.LONGITUDE,
                RegionBounds.LAT_SPAN,
                RegionBounds.LON_SPAN
            };
            HashMap<Long, ArrayList<ObaRegionElement.Bounds>> results = new HashMap<Long, ArrayList<ObaRegionElement.Bounds>>();

            ContentResolver cr = getContext().getContentResolver();
            c = cr.query(RegionBounds.CONTENT_URI, PROJECTION, null, null, null);
            if (c == null) {
                return results;
            }
            if (c.getCount() == 0) {
                c.close();
                return results;
            }
            c.moveToFirst();
            do {
                long regionId = c.getLong(0);
                ArrayList<ObaRegionElement.Bounds> bounds = results.get(regionId);
                ObaRegionElement.Bounds b = new ObaRegionElement.Bounds(
                        c.getDouble(1),
                        c.getDouble(2),
                        c.getDouble(3),
                        c.getDouble(4));
                if (bounds != null) {
                    bounds.add(b);
                } else {
                    bounds = new ArrayList<ObaRegionElement.Bounds>();
                    bounds.add(b);
                    results.put(regionId, bounds);
                }

            } while (c.moveToNext());

            return results;

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private ArrayList<ObaRegion> getRegionsFromServer() {
        ObaRegionsResponse response = ObaRegionsRequest.newRequest(getContext()).call();
        return new ArrayList<ObaRegion>(Arrays.asList(response.getRegions()));
    }

    //
    // Saving
    //
    private void saveToProvider(ArrayList<ObaRegion> regions) {
        // Delete all the existing regions
        ContentResolver cr = getContext().getContentResolver();
        cr.delete(Regions.CONTENT_URI, null, null);
        // Should be a no-op?
        cr.delete(RegionBounds.CONTENT_URI, null, null);

        for (ObaRegion region: regions) {
            if (!region.getActive()) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Region '" + region.getName() + "' is not active, skipping insert..."); }
                continue;
            }
            cr.insert(Regions.CONTENT_URI, toContentValues(region));
            if (BuildConfig.DEBUG) { Log.d(TAG, " Saved region '" + region.getName() + "' to provider"); }
            long regionId = region.getId();
            // Bulk insert the bounds
            ObaRegion.Bounds[] bounds = region.getBounds();
            if (bounds != null) {
                ContentValues[] values = new ContentValues[bounds.length];
                for (int i = 0; i < bounds.length; ++i) {
                    values[i] = toContentValues(regionId, bounds[i]);
                }
                cr.bulkInsert(RegionBounds.CONTENT_URI, values);
            }
        }
    }

    private static ContentValues toContentValues(ObaRegion region) {
        ContentValues values = new ContentValues();
        values.put(Regions._ID, region.getId());
        values.put(Regions.NAME, region.getName());
        String obaUrl = region.getObaBaseUrl();
        values.put(Regions.OBA_BASE_URL, obaUrl != null ? obaUrl : "");
        String siriUrl = region.getSiriBaseUrl();
        values.put(Regions.SIRI_BASE_URL, siriUrl != null ? siriUrl : "");
        values.put(Regions.LANGUAGE, region.getLanguage());
        values.put(Regions.CONTACT_EMAIL, region.getContactEmail());
        values.put(Regions.SUPPORTS_OBA_DISCOVERY, region.getSupportsObaDiscovery() ? 1 : 0);
        values.put(Regions.SUPPORTS_OBA_REALTIME, region.getSupportsObaRealtime() ? 1 : 0);
        values.put(Regions.SUPPORTS_SIRI_REALTIME, region.getSupportsSiriRealtime() ? 1 : 0);
        return values;
    }

    private static ContentValues toContentValues(long region, ObaRegion.Bounds bounds) {
        ContentValues values = new ContentValues();
        values.put(RegionBounds.REGION_ID, region);
        values.put(RegionBounds.LATITUDE, bounds.getLat());
        values.put(RegionBounds.LONGITUDE, bounds.getLon());
        values.put(RegionBounds.LAT_SPAN, bounds.getLatSpan());
        values.put(RegionBounds.LON_SPAN, bounds.getLonSpan());
        return values;
    }
}
