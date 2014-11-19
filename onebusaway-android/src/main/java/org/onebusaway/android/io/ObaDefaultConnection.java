/*
 * Copyright (C) 2010-2012 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.io;

import org.onebusaway.android.BuildConfig;

import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class ObaDefaultConnection implements ObaConnection {

    private static final String TAG = "ObaDefaultConnection";

    private HttpURLConnection mConnection;

    ObaDefaultConnection(Uri uri) throws IOException {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, uri.toString());
        }
        URL url = new URL(uri.toString());
        mConnection = (HttpURLConnection) url.openConnection();
        mConnection.setReadTimeout(30 * 1000);
    }

    @Override
    public void disconnect() {
        mConnection.disconnect();
    }

    @Override
    public Reader get() throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return get_Gingerbread();
        } else {
            return get_Froyo();
        }
    }

    @Override
    public Reader post(String string) throws IOException {
        byte[] data = string.getBytes();

        mConnection.setDoOutput(true);
        mConnection.setFixedLengthStreamingMode(data.length);
        mConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        // Set the output stream
        OutputStream stream = mConnection.getOutputStream();
        stream.write(data);
        stream.flush();
        stream.close();

        return new InputStreamReader(
                new BufferedInputStream(mConnection.getInputStream(), 8 * 1024));
    }

    //
    // Gingerbread and above support Gzip natively.
    //
    private Reader get_Gingerbread() throws IOException {
        return new InputStreamReader(
                new BufferedInputStream(mConnection.getInputStream(), 8 * 1024));
    }

    private Reader get_Froyo() throws IOException {
        boolean useGzip = false;
        mConnection.setRequestProperty("Accept-Encoding", "gzip");

        InputStream in = mConnection.getInputStream();

        final Map<String, List<String>> headers = mConnection.getHeaderFields();
        // This is a map, but we can't assume the key we're looking for
        // is in normal casing. So it's really not a good map, is it?
        final Set<Map.Entry<String, List<String>>> set = headers.entrySet();
        for (Iterator<Map.Entry<String, List<String>>> i = set.iterator(); i.hasNext(); ) {
            Map.Entry<String, List<String>> entry = i.next();
            if ("Content-Encoding".equalsIgnoreCase(entry.getKey())) {
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
            return new InputStreamReader(
                    new BufferedInputStream(new GZIPInputStream(in), 8 * 1024));
        } else {
            return new InputStreamReader(new BufferedInputStream(in, 8 * 1024));
        }
    }

    @Override
    public int getResponseCode() throws IOException {
        return mConnection.getResponseCode();
    }
}
