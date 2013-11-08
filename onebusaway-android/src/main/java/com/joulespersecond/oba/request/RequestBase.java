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
package com.joulespersecond.oba.request;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaConnection;
import com.joulespersecond.oba.ObaContext;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;

/**
 * The base class for Oba requests.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public class RequestBase {
    private static final String TAG = "RequestBase";

    protected final Uri mUri;
    protected final String mPostData;

    protected RequestBase(Uri uri) {
        mUri = uri;
        mPostData = null;
    }

    protected RequestBase(Uri uri, String postData) {
        mUri = uri;
        mPostData = postData;
    }

    public static class BuilderBase {
        protected static final String BASE_PATH = "api/where";

        protected final Uri.Builder mBuilder;
        protected ObaContext mObaContext;
        protected Context mContext;

        protected BuilderBase(Context context, String path) {
            this(context, null, path);
        }

        protected BuilderBase(Context context, ObaContext obaContext, String path) {
            mContext = context;
            mObaContext = obaContext;
            mBuilder = new Uri.Builder();
            mBuilder.path(path);
        }

        protected static String getPathWithId(String pathElement, String id) {
            StringBuilder builder = new StringBuilder(BASE_PATH);
            builder.append(pathElement);
            builder.append(Uri.encode(id));
            builder.append(".json");
            return builder.toString();
        }

        protected Uri buildUri() {
            ObaContext context = (mObaContext != null) ? mObaContext : ObaApi.getDefaultContext();
            context.setBaseUrl(mContext, mBuilder);
            context.setAppInfo(mBuilder);
            mBuilder.appendQueryParameter("version", "2");
            mBuilder.appendQueryParameter("key", context.getApiKey());
            return mBuilder.build();
        }

        public ObaContext getObaContext() {
            if (mObaContext == null) {
                mObaContext = ObaApi.getDefaultContext().clone();
            }
            return mObaContext;
        }
    }

    /**
     * Subclass for BuilderBase that can handle post data as well.
     * @author paulw
     */
    public static class PostBuilderBase extends BuilderBase {
        protected final Uri.Builder mPostData;

        protected PostBuilderBase(Context context, String path) {
            super(context, path);
            mPostData = new Uri.Builder();
        }

        public String buildPostData() {
            return mPostData.build().getEncodedQuery();
        }
    }

    protected <T> T call(Class<T> cls) {
        ObaApi.SerializationHandler handler = ObaApi.getSerializer(cls);
        ObaConnection conn = null;
        try {
            conn = ObaApi.getDefaultContext().getConnectionFactory().newConnection(mUri);
            Reader reader;
            if (mPostData != null) {
                reader = conn.post(mPostData);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    // Theoretically you can't call ResponseCode before calling
                    // getInputStream, but you can't read from the input stream
                    // before you read the response???
                    int responseCode = conn.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        return handler.createFromError(cls, responseCode, "");
                    }
                }

                reader = conn.get();
            }
            T t = handler.deserialize(reader, cls);
            if (t == null) {
                t = handler.createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, "Json error");
            }
            return t;
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.toString());
            return handler.createFromError(cls, ObaApi.OBA_NOT_FOUND, e.toString());
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return handler.createFromError(cls, ObaApi.OBA_IO_EXCEPTION, e.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    protected <T> T callPostHack(Class<T> cls) {
        ObaApi.SerializationHandler handler = ObaApi.getSerializer(cls);
        ObaConnection conn = null;
        try {
            conn = ObaApi.getDefaultContext().getConnectionFactory().newConnection(mUri);
            BufferedReader reader = new BufferedReader(conn.post(mPostData), 8*1024);

            String line;
            StringBuffer text = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                text.append(line + "\n");
            }

            String response = text.toString();
            if (TextUtils.isEmpty(response)) {
                return handler.createFromError(cls, ObaApi.OBA_OK, "OK");
            } else {
                // {"actionErrors":[],"fieldErrors":{"stopId":["requiredField.stopId"]}}
                // TODO: Deserialize the JSON and check "fieldErrors"
                // if this is empty, then it succeeded? Or check for an actual ObaResponse???
                return handler.createFromError(cls, ObaApi.OBA_INTERNAL_ERROR, response);
            }

        } catch (FileNotFoundException e) {
            Log.e(TAG, e.toString());
            return handler.createFromError(cls, ObaApi.OBA_NOT_FOUND, e.toString());
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return handler.createFromError(cls, ObaApi.OBA_IO_EXCEPTION, e.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
