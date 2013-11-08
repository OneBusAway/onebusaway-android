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
package com.joulespersecond.oba;

import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.BuildConfig;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class ObaContext {
    private static final String TAG = "ObaContext";
    private String mApiKey = "v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc=cGF1bGN3YXR0c0BnbWFpbC5jb20=";

    private int mAppVer = 0;
    private String mAppUid = null;
    private ObaConnectionFactory mConnectionFactory = ObaDefaultConnectionFactory.getInstance();

    private ObaRegion mRegion;

    public ObaContext() {
    }

    public void setAppInfo(int version, String uuid) {
        mAppVer = version;
        mAppUid = uuid;
    }

    public void setAppInfo(Uri.Builder builder) {
        if (mAppVer != 0) {
            builder.appendQueryParameter("app_ver", String.valueOf(mAppVer));
        }
        if (mAppUid != null) {
            builder.appendQueryParameter("app_uid", mAppUid);
        }
    }

    public void setApiKey(String apiKey) {
        mApiKey = apiKey;
    }

    public String getApiKey() {
        return mApiKey;
    }

    public void setRegion(ObaRegion region) {
        mRegion = region;
    }

    public ObaRegion getRegion() {
        return mRegion;
    }

    /**
     * Connection factory
     */
    public ObaConnectionFactory setConnectionFactory(ObaConnectionFactory factory) {
        ObaConnectionFactory prev = mConnectionFactory;
        mConnectionFactory = factory;
        return prev;
    }

    public ObaConnectionFactory getConnectionFactory() {
        return mConnectionFactory;
    }

    public void setBaseUrl(Context context, Uri.Builder builder) {
        // If there is a custom preference, then use that.
        String serverName = Application.get().getCustomApiUrl();
        
        if (!TextUtils.isEmpty(serverName)) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Using custom API URL set by user '" + serverName + "'."); }
            // Since the user-entered serverName might contain a partial path, we need to parse it
            Uri userEntered;
            if (Uri.parse(serverName).getScheme() == null) {
                // Add the scheme before parsing if one doesn't exist, since without a scheme the Uri won't parse the authority
                userEntered = Uri.parse("http://" + serverName);
            } else {
                userEntered = Uri.parse(serverName);
            }
            
            // Copy partial path that the user entered
            Uri.Builder path = new Uri.Builder();
            path.encodedPath(userEntered.getEncodedPath());
            
            // Then, tack on the rest of the REST API method path from the Uri.Builder that was passed in
            path.appendEncodedPath(builder.build().getPath());
                        
            // Finally, overwrite builder that was passed in with the full URL
            builder.scheme(userEntered.getScheme());
            builder.authority(userEntered.getAuthority());
            builder.encodedPath(path.build().getEncodedPath());
        } else if (mRegion != null) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Using region base URL '" + mRegion.getObaBaseUrl() + "'."); }
            Uri base = Uri.parse(mRegion.getObaBaseUrl() + builder.build().getPath());
            builder.scheme(base.getScheme());
            builder.authority(base.getAuthority());
            builder.encodedPath(base.getEncodedPath());
        } else {
            String fallBack = "api.onebusaway.org";
            Log.e(TAG, "Accessing default fallback '" + fallBack + "' ...this is wrong!!");
            // Current fallback for existing users?
            builder.scheme("http");
            builder.authority(fallBack);
        }
    }

    @Override
    public ObaContext clone() {
        ObaContext result = new ObaContext();
        result.setApiKey(mApiKey);
        result.setAppInfo(mAppVer, mAppUid);
        result.setConnectionFactory(mConnectionFactory);
        return result;
    }
}
