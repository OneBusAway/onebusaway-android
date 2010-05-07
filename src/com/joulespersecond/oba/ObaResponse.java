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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class ObaResponse {
    private static final String TAG = "ObaResponse";

    static class Deserializer implements JsonDeserializer<ObaResponse> {
        @Override
        public ObaResponse deserialize(JsonElement element,
                Type type,
                JsonDeserializationContext context) throws JsonParseException {
            final JsonObject obj = element.getAsJsonObject();

            final String version =
                JsonHelp.deserializeChild(obj, "version", String.class, context);
            final int code =
                JsonHelp.deserializeChild(obj, "code", int.class, context);
            final String text =
                JsonHelp.deserializeChild(obj, "text", String.class, context);
            ObaData data;

            if (ObaApi.VERSION2.equals(version)) {
                data = JsonHelp.deserializeChild(obj, "data", ObaData2.class, context);
            }
            else {
                data = JsonHelp.deserializeChild(obj, "data", ObaData1.class, context);
            }
            return new ObaResponse(version, code, text, data);
        }
    }

    private final String version;
    private final int code;
    private final String text;
    private final ObaData data;

    /**
     * Constructor from Deserializer
     */
    ObaResponse() {
        version = ObaApi.VERSION1;
        code = 0;
        text = "Uninit";
        data = ObaData1.EMPTY_OBJECT;
    }

    /**
     * Constructor for ObaResponse
     */
    private ObaResponse(String v, int c, String t, ObaData d) {
        version = v;
        code = c;
        text = t;
        data = d != null ? d : ObaData1.EMPTY_OBJECT;
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
        return new ObaResponse(ObaApi.VERSION1, 0, error, null);
    }
    static public ObaResponse createFromError(String error, int code) {
        return new ObaResponse(ObaApi.VERSION1, code, error, null);
    }
    static public ObaResponse createFromURL(URL url) throws IOException {
        long start = System.nanoTime();
        boolean useGzip = false;
        int code = 0;
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setReadTimeout(30*1000);
        conn.setRequestProperty("Accept-Encoding", "gzip");
        try {
            conn.connect();
            code = conn.getResponseCode();
        }
        catch (SocketException e) {
            Log.e(TAG, "Connection failed: " + e.toString());
            return createFromError("Connection failed: " + e.toString());
        }
        catch (IOException e) { // includes SocketTimeoutException
            Log.e(TAG, "Connection failed: " + e.toString());
            return createFromError("Connection failed: " + e.toString());
        }
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
        return data;
    }
    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
