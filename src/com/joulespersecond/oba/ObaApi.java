/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba;

import com.google.android.maps.GeoPoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ConcurrentHashMap;

public final class ObaApi {
    private static final String TAG = "ObaApi";
    // Uninstantiatable
    private ObaApi() { throw new AssertionError(); }

    public static final int OBA_OK = 200;
    public static final int OBA_BAD_REQUEST = 400;
    public static final int OBA_UNAUTHORIZED = 401;
    public static final int OBA_NOT_FOUND = 404;
    public static final int OBA_INTERNAL_ERROR = 500;
    public static final int OBA_OUT_OF_MEMORY = 666;

    public static final String VERSION1 = "1";
    public static final String VERSION2 = "2";

    private static final String API_KEY = "v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc=cGF1bGN3YXR0c0BnbWFpbC5jb20=";

    private static class GsonHolder {
        static final JsonHelp.CachingDeserializer<ObaAgency> mAgencyDeserializer =
            new JsonHelp.CachingDeserializer<ObaAgency>(
                    new ObaAgency.Deserialize(), "id");
        static final JsonHelp.CachingDeserializer<ObaRoute> mRouteDeserializer =
            new JsonHelp.CachingDeserializer<ObaRoute>(
                    new ObaRoute.Deserialize(), "id");
        static final JsonHelp.CachingDeserializer<ObaStop> mStopDeserializer =
            new JsonHelp.CachingDeserializer<ObaStop>(
                    new ObaStop.Deserialize(), "id");

        @SuppressWarnings("unchecked")
        static final Gson gsonObj = new GsonBuilder()
            .registerTypeAdapter(ObaArray.class, new ObaArray.Deserializer())
            .registerTypeAdapter(ObaRefMap.class, new ObaRefMap.Deserializer())
            .registerTypeAdapter(ObaResponse.class, new ObaResponse.Deserializer())
            .registerTypeAdapter(ObaData2.class, new ObaData2.Deserializer())
            .registerTypeAdapter(ObaEntry.class, new ObaEntry.Deserializer())
            .registerTypeAdapter(ObaReferences.class, new ObaReferences.Deserializer())
            .registerTypeAdapter(ObaAgency.class, mAgencyDeserializer)
            .registerTypeAdapter(ObaRoute.class, mRouteDeserializer)
            .registerTypeAdapter(ObaStop.class, mStopDeserializer)
            .create();

        static final void clearCache() {
            mAgencyDeserializer.clear();
            mRouteDeserializer.clear();
            mStopDeserializer.clear();
        }
    }

    //
    // Gson doesn't allow us to pass anything of our own into the custom
    // deserializers, so we have to store it here, indexed by the context object
    // itself.
    //
    static final ConcurrentHashMap<JsonDeserializationContext,ObaReferences> mRefMap =
        new ConcurrentHashMap<JsonDeserializationContext,ObaReferences>();

    public static Gson getGson() {
        return GsonHolder.gsonObj;
    }

    private static ObaResponse doRequest(String urlStr) {
        Log.d(TAG, "Request: "  + urlStr);
        try {
            return ObaResponse.createFromURL(new URL(urlStr));
        } catch (IOException e) {
            e.printStackTrace();
            return ObaResponse.createFromError(e.toString());
        } catch (OutOfMemoryError e) {
            return ObaResponse.createFromError(e.toString(), OBA_OUT_OF_MEMORY);
        }
    }

    private static String getUrl(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String serverName = preferences.getString("preferences_oba_api_servername", "api.onebusaway.org");
        return "http://" + serverName+ "/api/where";
    }

    private static final String V2_ARGS = "version=2&";
    private static String mVersion = V2_ARGS;
    // AppInfo is for API v1
    private static String mAppInfo = "";
    private static int mAppVer = 0;
    private static String mAppUid = null;

    @Deprecated
    public static void setVersion(String version) {
        if (VERSION2.equals(version)) {
            mVersion = V2_ARGS;
        }
        else {
            mVersion = "";
        }
    }
    public static void setAppInfo(int version, String uuid) {
        mAppVer = version;
        mAppUid = uuid;
        mAppInfo = String.format("&app_ver=%d&app_uid=%s", version, uuid);
    }
    public static void setAppInfo(Uri.Builder builder) {
        if (mAppVer != 0) {
            builder.appendQueryParameter("app_ver", String.valueOf(mAppVer));
        }
        if (mAppUid != null) {
            builder.appendQueryParameter("app_uid", mAppUid);
        }
    }


    /**
     * Retrieves a stop by its full ID.
     *
     * @param id The stop ID.
     * @return A response object.
     */
    @Deprecated
    public static ObaResponse getStopById(Context context, String id) {

        // We can do a simple format since we're not expecting the id needs escaping.
        return doRequest(
                String.format("%s/stop/%s.json?%skey=%s%s",
                        getUrl(context), id, mVersion, API_KEY, mAppInfo));
    }
    /**
     * Retrieves a route by its full ID.
     *
     * @param id The route ID.
     * @return A response object.
     */
    @Deprecated
    public static ObaResponse getRouteById(Context context, String id) {
        return doRequest(
                String.format("%s/route/%s.json?%skey=%s%s",
                        getUrl(context), id, mVersion, API_KEY, mAppInfo));
    }
    /**
     * Search for stops by a location in a specified radius,
     * with an optional stop ID query.
     *
     * @param location The latitude/longitude of the search center.
     * @param radius The optional search radius in meters.
     * @param latSpan Optional latitude height of the search area centered on lat.
     * @param lonSpan Optional longitude width of the search area centered on lon.
     * @param query Optional stop ID to search for.
     * @param maxCount Optional maximum number of stop entries to return.
     * @return A response object.
     */
    @Deprecated
    public static ObaResponse getStopsByLocation(Context context, GeoPoint location,
            int radius,
            int latSpan,
            int lonSpan,
            String query,
            int maxCount) {
        // NOTE: this hardcodes v1 of the API because we currently can't handle
        // the "list" type (it's ambiguous as to what it holds)
        // In order to handle this properly, we will have to bite the bullet
        // and have multiple response types.
        String url = String.format("%s/stops-for-location.json?key=%s%s&lat=%f&lon=%f",
                getUrl(context),
                API_KEY,
                mAppInfo,
                (double)location.getLatitudeE6()/1E6,
                (double)location.getLongitudeE6()/1E6);
        if (radius != 0) {
            url += "&radius=";
            url += String.valueOf(radius);
        }
        if (latSpan != 0) {
            url += "&latSpan=";
            url += String.valueOf(latSpan/1E6);
        }
        if (lonSpan != 0) {
            url += "&lonSpan=";
            url += String.valueOf(lonSpan/1E6);
        }
        if (query != null) {
            url += "&query=";
            try {
                url += URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }
        }
        if (maxCount != 0) {
            url += "&maxCount=";
            url += String.valueOf(maxCount);
        }
         return doRequest(url);
    }
    /**
     * Search for routes by location in a specified radius,
     * with an optional route name query term.
     *
     * @param location The location of the search center.
     * @param radius The optional search radius in meters.
     * @param query The optional route name to search for.
     * @return A response object.
     */
    @Deprecated
    public static ObaResponse getRoutesByLocation(Context context, GeoPoint location,
            int radius,
            String query) {
        // NOTE: this hardcodes v1 of the API because we currently can't handle
        // the "list" type (it's ambiguous as to what it holds)
        // In order to handle this properly, we will have to bite the bullet
        // and have multiple response types.
        StringBuilder url = new StringBuilder(
                String.format("%s/routes-for-location.json?key=%s%s&lat=%f&lon=%f",
                getUrl(context),
                API_KEY,
                mAppInfo,
                (double)location.getLatitudeE6()/1E6,
                (double)location.getLongitudeE6()/1E6));
        if (radius != 0) {
            url.append("&radius=");
            url.append(String.valueOf(radius));
        }
        if (query != null) {
            url.append("&query=");
            try {
                url.append(URLEncoder.encode(query, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }
        }
         return doRequest(url.toString());
    }
    /**
     * Get a list of stops that are services by a given route, including
     * ordered list of stops for each direction of service, and polylines
     * mapping the route where available.
     *
     * @param id The route ID.
     * @return A response object.
     */
    @Deprecated
    public static ObaResponse getStopsForRoute(Context context, String id) {
        return doRequest(
                String.format("%s/stops-for-route/%s.json?%skey=%s%s",
                        getUrl(context), id, mVersion, API_KEY, mAppInfo));
    }

    /**
     * Get a list of stops that are services by a given route, including
     * ordered list of stops for each direction of service, and polylines
     * mapping the route where available.
     *
     * @param id The route ID.
     * @return A response object.
     */
    @Deprecated
    public static ObaResponse getStopsForRoute(Context context,
            String id,
            boolean includePolylines) {
        return doRequest(
                String.format("%s/stops-for-route/%s.json?%skey=%s%s&includePolylines=%s",
                        getUrl(context),
                        id,
                        mVersion,
                        API_KEY,
                        mAppInfo,
                        String.valueOf(includePolylines)));
    }

    /**
     * Get current arrivals and departures for routes serving the specified stop.
     * When available, real-time arrival and departure preditions will be
     * provided in addition to static schedule information.
     *
     * @param id The stop ID.
     * @return true if successful, false otherwise.
     */
    @Deprecated
    public static ObaResponse getArrivalsDeparturesForStop(Context context, String id) {
        return doRequest(
                String.format("%s/arrivals-and-departures-for-stop/%s.json?%skey=%s%s",
                        getUrl(context), id, mVersion, API_KEY, mAppInfo));
    }

    /**
     * Converts a latitude/longitude to a GeoPoint.
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A GeoPoint representing this latitude/longitude.
     */
    public static final GeoPoint makeGeoPoint(double lat, double lon) {
        return new GeoPoint((int)(lat*1E6), (int)(lon*1E6));
    }

    /**
     * Clears the object cache for low memory situations.
     */
    public static final void clearCache() {
        GsonHolder.clearCache();
    }
}
