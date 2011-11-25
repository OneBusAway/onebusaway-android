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
package com.joulespersecond.oba.request.test;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.request.ObaResponse;

import android.content.Context;
import android.net.Uri;
import android.test.AndroidTestCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class ObaTestCase extends AndroidTestCase {
    protected static final String TEST_RAW_URI = "android.resource://com.joulespersecond.seattlebusbot.test/raw/";

    public static void assertOK(ObaResponse response) {
        assertNotNull(response);
        assertEquals(ObaApi.OBA_OK, response.getCode());
    }

    /**
     * Read a resource by Uri
     */
    protected Reader readResource(Uri resourceUri) throws IOException {
        Context localContext = getContext();
        InputStream stream = localContext.getContentResolver().openInputStream(resourceUri);
        InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
        return reader;
    }

    /**
     * Read a resource by Uri (String convenience overload)
     */
    protected Reader readResource(String resourceUriString) throws IOException {
        Uri uri = Uri.parse(resourceUriString);
        return readResource(uri);
    }

    protected <T> T getRawResourceAs(String resourceUriString, Class<T> cls) throws IOException {
        Reader reader = readResource(TEST_RAW_URI + resourceUriString);
        ObaApi.SerializationHandler serializer = ObaApi.getSerializer(cls);
        T response = serializer.deserialize(reader, cls);
        return response;
    }
}
