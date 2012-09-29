package com.joulespersecond.oba;

import com.joulespersecond.oba.region.ObaRegion;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

public class ObaContext {
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
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        String serverName = preferences.getString("preferences_oba_api_servername", null);
        if (serverName != null) {
            builder.encodedAuthority(serverName);
            builder.scheme("http");
        } else if (mRegion != null) {
            Uri base = mRegion.getObaBaseUri();
            builder.scheme(base.getScheme());
            builder.encodedAuthority(base.getAuthority());
        } else {
            // Current fallback for existing users?
            builder.scheme("http");
            builder.authority("api.onebusaway.org");
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
