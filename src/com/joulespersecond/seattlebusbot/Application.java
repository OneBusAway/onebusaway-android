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
package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.ObaApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.telephony.TelephonyManager;

import java.security.MessageDigest;
import java.util.UUID;

public class Application extends android.app.Application {
    //public static final String BUG_REPORT_URL = "http://bugs.joulespersecond.com/bugs/";
    public static final String APP_UID = "app_uid";

    @Override
    public void onCreate() {
        //ExceptionHandler.register(this, BUG_REPORT_URL);
        initOba();
    }

    private static final String HEXES = "0123456789abcdef";
    private static String getHex(byte[] raw) {
        final StringBuilder hex = new StringBuilder(2*raw.length);
        for (byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4))
               .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    private String getAppUid() {
        try {
            final TelephonyManager telephony =
                (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
            final String id = telephony.getDeviceId();
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(id.getBytes());
            return getHex(digest.digest());
        }
        catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private void initOba() {
        SharedPreferences settings = getSharedPreferences(UIHelp.PREFS_NAME, 0);
        String uuid = settings.getString(APP_UID, null);
        if (uuid == null) {
            // Generate one and save that.
            uuid = getAppUid();
            SharedPreferences.Editor edit = settings.edit();
            edit.putString(APP_UID, uuid);
            edit.commit();
        }

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        ObaApi.setAppInfo(appInfo.versionCode, uuid);
    }
}
