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

import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ObaDefaultConnection implements ObaConnection {

    private static final String TAG = "ObaDefaultConnection";

    private HttpURLConnection mConnection;

    ObaDefaultConnection(Uri uri) throws IOException {
        Log.d(TAG, uri.toString());
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
        return new InputStreamReader(
                new BufferedInputStream(mConnection.getInputStream(), 8 * 1024));
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

    @Override
    public int getResponseCode() throws IOException {
        return mConnection.getResponseCode();
    }
}
