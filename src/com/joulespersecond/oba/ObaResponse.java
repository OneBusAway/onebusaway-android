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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import android.util.Log;

import com.google.gson.JsonParseException;

public final class ObaResponse {
    private static final String TAG = "ObaResponse";
    public static final String VERSION = "1.0";

    private final String version;
    private final int code;
    private final String text;
    private final ObaData data;

    /**
     * Constructor from Deserializer
     */
    private ObaResponse() {
        version = VERSION;
        code = 0;
        text = "Uninit";
        data = null;
    }

    /**
     * Constructor for ObaResponse
     */
    private ObaResponse(String v, int c, String t) {
        version = v;
        code = c;
        text = t;
        data = null;
    }
    static public ObaResponse createFromString(String str)  {
        try {
            ObaResponse r = ObaApi.getGson().fromJson(str, ObaResponse.class);
            if (r != null) {
                return r;
            }
            // We must never ever return null, always an error object.
            return createFromError("null gson response");
        }
        catch (JsonParseException e) {
            return createFromError(e.toString());
        }
    }
    static public ObaResponse createFromError(String error) {
        return new ObaResponse(VERSION, 0, error);
    }
    static public ObaResponse createFromError(String error, int code) {
        return new ObaResponse(VERSION, code, error);
    }
    static public ObaResponse createFromURL(URL url) throws IOException {
        long start = System.nanoTime();
        boolean useGzip = false;
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.connect();
        final int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            return createFromError("Server returned an error", code);
        }
        InputStream in = conn.getInputStream();

        final Map<String,List<String>> headers = conn.getHeaderFields();
        // This is a map, but we can't assume the key we're looking for
        // is in normal casing. So it's really not a good map, is it?
        final Set<Map.Entry<String,List<String>>> set = headers.entrySet();
        for (Iterator<Map.Entry<String,List<String>>> i = set.iterator(); i.hasNext(); ) {
            Map.Entry<String,List<String>> entry = i.next();
            if (entry.getKey().equalsIgnoreCase("Content-Encoding")) {
                for (Iterator<String> j = entry.getValue().iterator(); j.hasNext(); ) {
                    String str = j.next();
                    if (str.equalsIgnoreCase("gzip")) {
                        useGzip = true;
                        break;
                    }
                }
                // Break out of outer loop.
                if (useGzip) {
                    break;
                }
            }
        }
        long end = System.nanoTime();
        Log.d(TAG, "Connect: " + (end-start)/1e6);
        start = end;

        Reader reader;
        if (useGzip) {
            reader = new BufferedReader(
                    new InputStreamReader(new GZIPInputStream(in)), 8*1024);
        }
        else {
            reader = new BufferedReader(
                    new InputStreamReader(in), 8*1024);
        }
        try {
            ObaResponse r = ObaApi.getGson().fromJson(reader, ObaResponse.class);
            end = System.nanoTime();
            Log.d(TAG, "Parse: " + (end-start)/1e6);
            if (r != null) {
                return r;
            }
            // Gson may return null, but we must never.
            return createFromError("null gson response");
        }
        catch (JsonParseException e) {
            return createFromError(e.toString());
        }
    }

    public String getVersion() {
        return version;
    }
    public int getCode() {
        return code;
    }
    public String getText() {
        return text;
    }
    public ObaData getData() {
        return (data != null) ? data : new ObaData1();
    }
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
