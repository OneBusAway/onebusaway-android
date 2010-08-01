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

import android.net.Uri;
import android.util.Log;

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

public final class ObaHelp {
    private static final String TAG = "ObaHelp";

    public static Reader getUri(Uri uri) throws IOException {
        return getUri(new URL(uri.toString()));
    }

    public static Reader getUri(URL url) throws IOException {
        Log.d(TAG, "getUri: " + url.toString());

        boolean useGzip = false;
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setReadTimeout(30*1000);
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.connect();

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

        if (useGzip) {
            return new BufferedReader(
                    new InputStreamReader(new GZIPInputStream(in)), 8*1024);
        }
        else {
            return new BufferedReader(
                    new InputStreamReader(in), 8*1024);
        }
    }
}
