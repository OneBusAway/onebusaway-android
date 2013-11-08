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
import com.joulespersecond.oba.serialization.JacksonSerializer;

import java.io.Reader;

public final class ObaApi {
    //private static final String TAG = "ObaApi";
    // Uninstantiatable
    private ObaApi() { throw new AssertionError(); }

    public static final int OBA_OK = 200;
    public static final int OBA_BAD_REQUEST = 400;
    public static final int OBA_UNAUTHORIZED = 401;
    public static final int OBA_NOT_FOUND = 404;
    public static final int OBA_INTERNAL_ERROR = 500;
    public static final int OBA_BAD_GATEWAY = 502;
    public static final int OBA_OUT_OF_MEMORY = 666;
    public static final int OBA_IO_EXCEPTION = 700;

    public static final String VERSION1 = "1";
    public static final String VERSION2 = "2";

    private static final ObaContext mDefaultContext = new ObaContext();

    public static ObaContext getDefaultContext() {
        return mDefaultContext;
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

    public interface SerializationHandler {
        <T> T deserialize(Reader reader, Class<T> cls);
        String serialize(Object obj);

        <T> T createFromError(Class<T> cls, int code, String error);
    }

    public static final <T> SerializationHandler getSerializer(Class<T> cls) {
        return JacksonSerializer.getInstance();
    }
}
