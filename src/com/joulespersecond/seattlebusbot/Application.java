package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.ObaApi;
import com.nullwire.trace.ExceptionHandler;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import java.util.UUID;

public class Application extends android.app.Application {
    public static final String BUG_REPORT_URL = "http://bugs.joulespersecond.com/bugs/";
    public static final String APP_UID = "app_uid";

    @Override
    public void onCreate() {
        ExceptionHandler.register(this, BUG_REPORT_URL);
        initOba();
    }

    private void initOba() {
        SharedPreferences settings = getSharedPreferences(UIHelp.PREFS_NAME, 0);
        String uuid = settings.getString(APP_UID, null);
        if (uuid == null) {
            // Generate one and save that.
            uuid = UUID.randomUUID().toString();
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
