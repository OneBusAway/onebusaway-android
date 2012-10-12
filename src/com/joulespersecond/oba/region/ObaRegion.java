/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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

import android.location.Location;
import android.net.Uri;

public class ObaRegion {
    public static class Bounds {
        private final double mLat;
        private final double mLon;
        private final double mLatSpan;
        private final double mLonSpan;

        public Bounds(double lat, double lon, double latSpan, double lonSpan) {
            mLat = lat;
            mLon = lon;
            mLatSpan = latSpan;
            mLonSpan = lonSpan;
        }

        public double getLat() {
            return mLat;
        }

        public double getLon() {
            return mLon;
        }

        public double getLatSpan() {
            return mLatSpan;
        }

        public double getLonSpan() {
            return mLonSpan;
        }
    }

    private final long mId;
    private final String mName;
    private final Uri mObaBaseUri;
    private final Uri mSiriBaseUri;
    private final Bounds[] mBounds;
    private final String mLanguage;
    private final String mContactName;
    private final String mContactEmail;
    private final boolean mSupportsObaDiscovery;
    private final boolean mSupportsObaRealtime;
    private final boolean mSupportsSiriRealtime;

    //
    // We could potentially use a builder pattern,
    // but no one should really be creating these objects
    // except for the RegionsLoader.
    //
    public ObaRegion(long id,
            String name,
            Uri obaBaseUri,
            Uri siriBaseUri,
            Bounds[] bounds,
            String lang,
            String contactName,
            String contactEmail,
            boolean supportsObaDiscovery,
            boolean supportsObaRealtime,
            boolean supportsSiriRealtime) {
        mId = id;
        mName = name;
        mObaBaseUri = obaBaseUri;
        mSiriBaseUri = siriBaseUri;
        mBounds = bounds;
        mLanguage = lang;
        mContactName = contactName;
        mContactEmail = contactEmail;
        mSupportsObaDiscovery = supportsObaDiscovery;
        mSupportsObaRealtime = supportsObaRealtime;
        mSupportsSiriRealtime = supportsSiriRealtime;
    }

    public long getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public Uri getObaBaseUri() {
        return mObaBaseUri;
    }

    public Uri getSiriBaseUri() {
        return mSiriBaseUri;
    }

    public Bounds[] getBounds() {
        return mBounds;
    }

    public String getLanguage() {
        return mLanguage;
    }

    public String getContactName() {
        return mContactName;
    }

    public String getContactEmail() {
        return mContactEmail;
    }

    public boolean supportsObaDiscovery() {
        return mSupportsObaDiscovery;
    }

    public boolean supportsObaRealtime() {
        return mSupportsObaRealtime;
    }

    public boolean supportsSiriRealtime() {
        return mSupportsSiriRealtime;
    }

    /**
     * Returns the distance from the specified location
     * to the center of the closest bound in this region.
     */
    public Float getDistanceAway(double lat, double lon) {
        if (mBounds == null) {
            return null;
        }
        float[] results = new float[1];
        float minDistance = Float.MAX_VALUE;
        for (Bounds bound: mBounds) {
            Location.distanceBetween(lat, lon, bound.getLat(), bound.getLon(), results);
            if (results[0] < minDistance) {
                minDistance = results[0];
            }
        }
        return minDistance;
    }

    public Float getDistanceAway(Location loc) {
        return getDistanceAway(loc.getLatitude(), loc.getLongitude());
    }

    /**
     * Returns the center and lat/lon span for the entire region.
     * @param results Array to receive results.
     *              results[0] == latSpan of region
     *              results[1] == lonSpan of region
     *              results[2] == lat center of region
     *              results[3] == lon center of region
     */
    public void getRegionSpan(double[] results) {
        if (results.length < 4) {
            throw new IllegalArgumentException("Results array is < 4");
        }
        double latMin = 90;
        double latMax = -90;
        double lonMin = 180;
        double lonMax = -180;

        // This is fairly simplistic
        for (Bounds bound: mBounds) {
            // Get the top bound
            double lat = bound.getLat();
            double latSpanHalf = bound.getLatSpan() / 2.0;
            double lat1 = lat - latSpanHalf;
            double lat2 = lat + latSpanHalf;
            if (lat1 < latMin) {
                latMin = lat1;
            }
            if (lat2 > latMax) {
                latMax = lat2;
            }

            double lon = bound.getLon();
            double lonSpanHalf = bound.getLonSpan() / 2.0;
            double lon1 = lon - lonSpanHalf;
            double lon2 = lon + lonSpanHalf;
            if (lon1 < lonMin) {
                lonMin = lon1;
            }
            if (lon2 > lonMax) {
                lonMax = lon2;
            }
        }

        results[0] = latMax - latMin;
        results[1] = lonMax - lonMin;
        results[2] = latMin + ((latMax - latMin) / 2.0);
        results[3] = lonMin + ((lonMax - lonMin) / 2.0);
    }
}
