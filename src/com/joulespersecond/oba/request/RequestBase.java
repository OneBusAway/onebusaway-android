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
package com.joulespersecond.oba.request;

import com.google.gson.JsonParseException;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaHelp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

/**
 * The base class for Oba requests.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public class RequestBase {
    protected final Uri mUri;

    protected RequestBase(Uri uri) {
        mUri = uri;
    }

    private static String getServer(Context context) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString("preferences_oba_api_servername",
                "api.onebusaway.org");
    }

    public static class BuilderBase {
        private static final String API_KEY = "v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc=cGF1bGN3YXR0c0BnbWFpbC5jb20=";
        protected static final String BASE_PATH = "/api/where";

        protected final Uri.Builder mBuilder;
        private String mApiKey = API_KEY;

        protected BuilderBase(Context context, String path) {
            mBuilder = new Uri.Builder();
            mBuilder.scheme("http");
            mBuilder.authority(getServer(context));
            mBuilder.path(path);
            mBuilder.appendQueryParameter("version", "2");
            ObaApi.setAppInfo(mBuilder);
        }

        protected BuilderBase(Context context, String path, boolean noVersion) {
            mBuilder = new Uri.Builder();
            mBuilder.scheme("http");
            mBuilder.authority(getServer(context));
            mBuilder.path(path);
            ObaApi.setAppInfo(mBuilder);
        }

        protected static String getPathWithId(String pathElement, String id) {
            StringBuilder builder = new StringBuilder(BASE_PATH);
            builder.append(pathElement);
            builder.append(id);
            builder.append(".json");
            return builder.toString();
        }

        protected Uri buildUri() {
            mBuilder.appendQueryParameter("key", mApiKey);
            return mBuilder.build();
        }

        /**
         * Allows the caller to assign a different server for a specific request.
         * Useful for unit-testing against specific servers (for instance, soak-api
         * when some new APIs haven't been released to production).
         *
         * Because this is implemented in the base class, it can't return 'this'
         * to use the standard builder pattern. Oh well, it's only for test.
         */
        public void setServer(String server) {
            mBuilder.authority(server);
        }

        /**
         * Allows the caller to assign a different API key for a specific request.
         *
         * Because this is implemented in the base class, it can't return 'this'
         * to use the standard builder pattern. Oh well, it's only for test.
         */
        public void setApiKey(String key) {
            mApiKey = key;
        }
    }

    private <T> T createFromError(Class<T> cls, int code, String error) {
        // This is not very efficient, but it's an error case and it's easier
        // than instantiating one ourselves.
        final String jsonErr =  ObaApi.getGson().toJson(error);
        final String json = String.format("{code: %d,version:\"2\",text:%s}", code, jsonErr);
        // Hopefully this never returns null.
        return ObaApi.getGson().fromJson(json, cls);
    }

    protected <T> T call(Class<T> cls) {
        try {
            Reader reader = ObaHelp.getUri(mUri);
            T t = ObaApi.getGson().fromJson(reader, cls);
            if (t == null) {
                t = createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, "Json error");
            }
            return t;
        }
        catch (FileNotFoundException e) {
            return createFromError(cls, ObaApi.OBA_NOT_FOUND, e.toString());
        }
        catch (IOException e) {
            return createFromError(cls, ObaApi.OBA_IO_EXCEPTION, e.toString());
        }
        catch (JsonParseException e) {
            return createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, e.toString());
        }
    }
}
