package com.joulespersecond.oba.region;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaConnection;
import com.joulespersecond.oba.provider.ObaContract.RegionBounds;
import com.joulespersecond.oba.provider.ObaContract.Regions;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.AsyncTaskLoader;
import android.text.TextUtils;
import android.util.Log;

import au.com.bytecode.opencsv.CSVReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class ObaRegionsLoader extends AsyncTaskLoader<ArrayList<ObaRegion>> {
    private static final String TAG = "ObaRegionsLoader";
    private static final Uri REGIONS_URI = Uri.parse("https://docs.google.com/spreadsheet/ccc?key=0AsoU647elPShdHlYc0RJbkEtZnVvTW11WE5NbHNiMXc&pli=1&gid=0&output=csv");

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
                return results;
            }
        }

        results = getRegionsFromServer();
        if (results == null) {
            return null;
        }

        saveToProvider(results);
        return results;
    }

    private ArrayList<ObaRegion> getRegionsFromProvider() {
        // Prefetch the bounds to limit the number of DB calls.
        HashMap<Long, ArrayList<ObaRegion.Bounds>> allBounds = getBoundsFromProvider();

        Cursor c = null;
        try {
            final String[] PROJECTION = {
                Regions._ID,
                Regions.NAME,
                Regions.OBA_BASE_URL,
                Regions.SIRI_BASE_URL,
                Regions.LANGUAGE,
                Regions.CONTACT_NAME,
                Regions.CONTACT_EMAIL,
                Regions.SUPPORTS_OBA_DISCOVERY,
                Regions.SUPPORTS_OBA_REALTIME,
                Regions.SUPPORTS_SIRI_REALTIME
            };

            ContentResolver cr = getContext().getContentResolver();
            c = cr.query(Regions.CONTENT_URI, PROJECTION, null, null, Regions.NAME);
            if (c == null || c.getCount() == 0) {
                return null;
            }
            ArrayList<ObaRegion> results = new ArrayList<ObaRegion>();

            c.moveToFirst();
            do {
                long id = c.getLong(0);
                ArrayList<ObaRegion.Bounds> bounds = allBounds.get(id);
                ObaRegion.Bounds[] bounds2 = (bounds != null) ?
                        bounds.toArray(new ObaRegion.Bounds[] {}) :
                        null;

                results.add(new ObaRegion(id,   // id
                    c.getString(1),             // Name
                    Uri.parse(c.getString(2)),  // OBA Base URL
                    Uri.parse(c.getString(3)),  // SIRI Base URL
                    bounds2,                    // Bounds
                    c.getString(4),             // Lang
                    c.getString(5),             // Contact Name
                    c.getString(6),             // Contact Email
                    c.getInt(7) > 0,            // Supports Oba Discovery
                    c.getInt(8) > 0,            // Supports Oba Realtime
                    c.getInt(9) > 0             // Supports Siri Realtime
                ));

            } while (c.moveToNext());

            return results;

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private HashMap<Long, ArrayList<ObaRegion.Bounds>> getBoundsFromProvider() {
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
            HashMap<Long, ArrayList<ObaRegion.Bounds>> results = new HashMap<Long, ArrayList<ObaRegion.Bounds>>();

            ContentResolver cr = getContext().getContentResolver();
            c = cr.query(RegionBounds.CONTENT_URI, PROJECTION, null, null, null);
            if (c == null || c.getCount() == 0) {
                return results;
            }
            c.moveToFirst();
            do {
                long regionId = c.getLong(0);
                ArrayList<ObaRegion.Bounds> bounds = results.get(regionId);
                ObaRegion.Bounds b = new ObaRegion.Bounds(
                        c.getDouble(1),
                        c.getDouble(2),
                        c.getDouble(3),
                        c.getDouble(4));
                if (bounds != null) {
                    bounds.add(b);
                } else {
                    bounds = new ArrayList<ObaRegion.Bounds>();
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

    private static final Comparator<ObaRegion> mSortAlpha = new Comparator<ObaRegion>() {
        @Override
        public int compare(ObaRegion lhs, ObaRegion rhs) {
            return lhs.getName().compareTo(rhs.getName());
        }
    };

    private ArrayList<ObaRegion> getRegionsFromServer() {
        ObaConnection conn = null;
        CSVReader reader = null;
        try {
            conn = ObaApi.getConnectionFactory().newConnection(REGIONS_URI);
            reader = new CSVReader(conn.get());
            List<String[]> entries = reader.readAll();
            ArrayList<ObaRegion> results = new ArrayList<ObaRegion>();
            long id = 0;
            for (String[] line: entries) {
                if ("Region".equals(line[0])) {
                    // Ignore the description line.
                    ++id;
                    continue;
                }
                results.add(new ObaRegion(id,
                        line[0],                // Name
                        Uri.parse(line[1]),     // OBA Base URL
                        Uri.parse(line[2]),     // SIRI Base URL
                        parseBounds(line[3]),   // Bounds
                        line[4],                // Language
                        line[5],                // Contact Name
                        line[6],                // Contact Email
                        "TRUE".equals(line[7]), // Supports OBA Discovery
                        "TRUE".equals(line[8]), // Supports OBA Realtime
                        "TRUE".equals(line[9]))); // Supports SIRI Realtime
                ++id;
            }
            // Sort by name
            Collections.sort(results, mSortAlpha);
            return results;

        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to get regions from server: " + e);
            return null;

        } catch (IOException e) {
            Log.e(TAG, "Unable to get regions from server: " + e);
            return null;

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static ObaRegion.Bounds[] parseBounds(String bounds) {
        if (TextUtils.isEmpty(bounds)) {
            return null;
        }
        ArrayList<ObaRegion.Bounds> results = new ArrayList<ObaRegion.Bounds>();
        // This could potentially be more efficient, but it's rarely, rarely run.
        String[] boundList = bounds.split("\\|");
        for (String bound: boundList) {
            try {
                String[] boundTypes = bound.split("\\:");
                double lat = Double.parseDouble(boundTypes[0]);
                double lon = Double.parseDouble(boundTypes[1]);
                double latSpan = Double.parseDouble(boundTypes[2]);
                double lonSpan = Double.parseDouble(boundTypes[3]);
                results.add(new ObaRegion.Bounds(lat, lon, latSpan, lonSpan));

            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Unable to parse: IndexOutOfBounds: " + bound);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Unable to parse: NumberFormat: " + bound);
            }
        }
        return results.toArray(new ObaRegion.Bounds[] {});
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
            cr.insert(Regions.CONTENT_URI, toContentValues(region));
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
        values.put(Regions.OBA_BASE_URL, region.getObaBaseUri().toString());
        values.put(Regions.SIRI_BASE_URL, region.getSiriBaseUri().toString());
        values.put(Regions.LANGUAGE, region.getLanguage());
        values.put(Regions.CONTACT_NAME, region.getContactName());
        values.put(Regions.CONTACT_EMAIL, region.getContactEmail());
        values.put(Regions.SUPPORTS_OBA_DISCOVERY, region.supportsObaDiscovery() ? 1 : 0);
        values.put(Regions.SUPPORTS_OBA_REALTIME, region.supportsObaRealtime() ? 1 : 0);
        values.put(Regions.SUPPORTS_SIRI_REALTIME, region.supportsSiriRealtime() ? 1 : 0);
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
