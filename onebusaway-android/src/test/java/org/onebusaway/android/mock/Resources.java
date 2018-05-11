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

import org.junit.runner.RunWith;
import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.io.ObaApi;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.apache.commons.lang3.CharEncoding.UTF_8;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class Resources {

    private static final Uri TEST_RAW_URI =
            Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + ".test/raw/");

    public static Uri.Builder buildTestUri() {
        return TEST_RAW_URI.buildUpon();
    }

    public static Uri getTestUri(String path) {
        return Uri.withAppendedPath(TEST_RAW_URI, path);
    }

    /**
     * Read a resource by Uri
     */
    public static Reader read(Context context, Uri uri) throws IOException {
        ContentResolver contentResolver = RuntimeEnvironment.application.getContentResolver();
        ShadowContentResolver shadowContentResolver = shadowOf(contentResolver);

        InputStream stream = new ByteArrayInputStream("ourStream".getBytes(UTF_8));
        shadowContentResolver.registerInputStream(uri, stream);

        InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
        return reader;
    }

    public static <T> T readAs(Context context, Uri uri, Class<T> cls) throws IOException {
        Reader reader = read(context, uri);
        ObaApi.SerializationHandler serializer = ObaApi.getSerializer(cls);
        T response = serializer.deserialize(reader, cls);
        return response;
    }
}
