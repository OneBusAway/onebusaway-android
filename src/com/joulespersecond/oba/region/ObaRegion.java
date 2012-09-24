package com.joulespersecond.oba.region;

import android.location.Location;
import android.net.Uri;

public class ObaRegion {
    public static class Bounds {
        private final double mLat;
        private final double mLon;
        private final double mLatSpan;
        private final double mLonSpan;

        Bounds(double lat, double lon, double latSpan, double lonSpan) {
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
    ObaRegion(long id,
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
}
