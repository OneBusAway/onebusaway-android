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

import android.net.Uri;

public final class ObaApi {
    //private static final String TAG = "ObaApi";
    // Uninstantiatable
    private ObaApi() { throw new AssertionError(); }

    public static final int OBA_OK = 200;
    public static final int OBA_BAD_REQUEST = 400;
    public static final int OBA_UNAUTHORIZED = 401;
    public static final int OBA_NOT_FOUND = 404;
    public static final int OBA_INTERNAL_ERROR = 500;
    public static final int OBA_OUT_OF_MEMORY = 666;
    public static final int OBA_IO_EXCEPTION = 700;

    public static final String VERSION1 = "1";
    public static final String VERSION2 = "2";

    private static class GsonHolder {
        /*
        static final JsonHelp.CachingDeserializer<ObaAgency> mAgencyDeserializer =
            new JsonHelp.CachingDeserializer<ObaAgency>(
                    new ObaAgency.Deserialize(), "id");
        static final JsonHelp.CachingDeserializer<ObaRoute> mRouteDeserializer =
            new JsonHelp.CachingDeserializer<ObaRoute>(
                    new ObaRoute.Deserialize(), "id");
        static final JsonHelp.CachingDeserializer<ObaStop> mStopDeserializer =
            new JsonHelp.CachingDeserializer<ObaStop>(
                    new ObaStop.Deserialize(), "id");
        */

        //@SuppressWarnings("unchecked")
        static final Gson gsonObj = new GsonBuilder()
            /*
            .registerTypeAdapter(ObaArray.class, new ObaArray.Deserializer())
            .registerTypeAdapter(ObaRefMap.class, new ObaRefMap.Deserializer())
            .registerTypeAdapter(ObaResponse.class, new ObaResponse.Deserializer())
            .registerTypeAdapter(ObaData2.class, new ObaData2.Deserializer())
            .registerTypeAdapter(ObaEntry.class, new ObaEntry.Deserializer())
            .registerTypeAdapter(ObaReferences.class, new ObaReferences.Deserializer())
            .registerTypeAdapter(ObaAgency.class, mAgencyDeserializer)
            .registerTypeAdapter(ObaRoute.class, mRouteDeserializer)
            .registerTypeAdapter(ObaStop.class, mStopDeserializer)
            */
            .create();

        static final void clearCache() {
            /*
            mAgencyDeserializer.clear();
            mRouteDeserializer.clear();
            mStopDeserializer.clear();
            */
        }
    }

    public static Gson getGson() {
        return GsonHolder.gsonObj;
    }

    private static int mAppVer = 0;
    private static String mAppUid = null;

    public static void setAppInfo(int version, String uuid) {
        mAppVer = version;
        mAppUid = uuid;
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
