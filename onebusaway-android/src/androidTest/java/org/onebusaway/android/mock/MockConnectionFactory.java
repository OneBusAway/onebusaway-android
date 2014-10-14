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
import org.onebusaway.android.io.ObaConnectionFactory;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

public class MockConnectionFactory implements ObaConnectionFactory {

    private final Context mContext;

    private final UriMap mUriMap;

    public MockConnectionFactory(Context context) {
        mContext = context;
        try {
            mUriMap = Resources.readAs(mContext, Resources.getTestUri("urimap"), UriMap.class);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to read urimap: " + e);
        }
    }

    @Override
    public ObaConnection newConnection(Uri uri) throws IOException {
        return new MockConnection(mContext, mUriMap, uri);
    }

    public static class UriMap {

        //
        // Normalize request URI.
        // This removes the scheme, host and port,
        // It removes any query parameters we know don't matter to the request
        // like the version, API key and so forth.
        // Then sort the remaining query parameters.
        //
        private static final List<String> PARAMS_LIST =
                Arrays.asList("version", "key", "app_ver", "app_uid");

        private HashMap<String, String> uris;

        public String getUri(Uri uri) {
            String normalizedUri = normalizeUri(uri);
            String result = null;
            if (uris == null) {
                throw new RuntimeException("No uris in URIMap -- did the file parse correctly?");
            }
            result = uris.get(normalizedUri);
            if (result == null) {
                throw new RuntimeException("No response for URI: " + normalizedUri);
            }
            return result;
        }

        private String normalizeUri(Uri uri) {
            Uri.Builder builder = new Uri.Builder()
                    .encodedPath(uri.getEncodedPath());
            // getQueryParameterNames returns an unmodifiable set.
            // In any case, we need the parameters in sorted order
            TreeSet<String> params = new TreeSet<String>(UriCompat.getQueryParameterNames(uri));
            params.removeAll(PARAMS_LIST);
            for (String name : params) {
                builder.appendQueryParameter(name, uri.getQueryParameter(name));
            }

            return builder.build().toString();
        }
    }
}
