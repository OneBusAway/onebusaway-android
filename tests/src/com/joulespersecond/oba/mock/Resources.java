package com.joulespersecond.oba.mock;

import com.joulespersecond.oba.ObaApi;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class Resources {
    private static final Uri TEST_RAW_URI =
            Uri.parse("android.resource://com.joulespersecond.seattlebusbot.test/raw/");

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
        InputStream stream = context.getContentResolver().openInputStream(uri);
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
