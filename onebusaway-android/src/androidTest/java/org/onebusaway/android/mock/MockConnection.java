/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.mock;

import org.onebusaway.android.io.ObaConnection;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;

public class MockConnection implements ObaConnection {

    private static final String TAG = "MockConnection";

    private final MockConnectionFactory.UriMap mUriMap;

    private final Context mContext;

    private final Uri mUri;

    private int mResponseCode = HttpURLConnection.HTTP_OK;

    MockConnection(Context context,
            MockConnectionFactory.UriMap map,
            Uri uri) {
        mUriMap = map;
        mContext = context;
        mUri = uri;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public Reader get() throws IOException {
        Log.d(TAG, "Get URI: " + mUri);
        // Find a mock response for this URI.
        String response = mUriMap.getUri(mUri);
        if ("__404__".equals(response)) {
            mResponseCode = HttpURLConnection.HTTP_NOT_FOUND;
            throw new FileNotFoundException();
        }
        return Resources.read(mContext, Resources.getTestUri(response));
    }

    @Override
    public Reader post(String string) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getResponseCode() throws IOException {
        return mResponseCode;
    }
}
