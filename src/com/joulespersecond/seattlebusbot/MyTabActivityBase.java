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
package com.joulespersecond.seattlebusbot;

import com.google.android.maps.GeoPoint;

import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

abstract class MyTabActivityBase extends TabActivity {
    public static final String EXTRA_SHORTCUTMODE = ".ShortcutMode";
    public static final String EXTRA_SEARCHMODE = ".SearchMode";
    public static final String EXTRA_SEARCHCENTER = ".SearchCenter"; //int[]

    protected boolean mShortcutMode;
    protected boolean mSearchMode;
    protected GeoPoint mSearchCenter;
    private String mDefaultTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        mShortcutMode = Intent.ACTION_CREATE_SHORTCUT.equals(action);
        if (!mShortcutMode) {
            setTitle(R.string.app_name);
        }

        mSearchMode = intent.getBooleanExtra(EXTRA_SEARCHMODE, false);
        mSearchCenter = getSearchCenter(intent);

        // Determine what tab we're supposed to show by default
        final Uri data = intent.getData();
        if (data != null) {
            mDefaultTab = getDefaultTabFromUri(data);
        }

        // A hack inspired mostly by:
        // http://stackoverflow.com/questions/1906314/android-tabwidget-in-light-theme
        // (Question by David Hedlund, answer by yanoka)
        //
        // This doesn't change any of the font sizes or colors, since those are fine for me.
        //
        getTabHost().getTabWidget().setBackgroundColor(
                getResources().getColor(R.color.tab_widget_bg));
    }
    @Override
    public void onDestroy() {
        // If there was a tab in the intent, don't save it
        if (mDefaultTab == null) {
            SharedPreferences.Editor settings = getSharedPreferences(UIHelp.PREFS_NAME, 0).edit();
            settings.putString(getLastTabPref(), getTabHost().getCurrentTabTag());
            settings.commit();
        }

        super.onDestroy();
    }

    protected void restoreDefaultTab() {
        final String def;
        if (mDefaultTab != null) {
            def = mDefaultTab;
        }
        else {
            SharedPreferences settings = getSharedPreferences(UIHelp.PREFS_NAME, 0);
            def = settings.getString(getLastTabPref(), null);
        }
        if (def != null) {
            getTabHost().setCurrentTabByTag(def);
        }
    }

    void returnResult(Intent intent) {
        setResult(RESULT_OK, intent);
        finish();
    }

    //
    // Helpers for constructing/parsing the default tab URL.
    //
    private static final String TAB_SCHEME = "tab";

    public static final Uri getDefaultTabUri(String tab) {
        return Uri.fromParts(TAB_SCHEME, tab, null);
    }
    public static String getDefaultTabFromUri(Uri uri) {
        if (TAB_SCHEME.equals(uri.getScheme())) {
            return uri.getSchemeSpecificPart();
        }
        return null;
    }

    protected abstract String getLastTabPref();

    //
    // Helper for getting the search center from the intent
    //
    public static final Intent putSearchCenter(Intent intent, GeoPoint pt) {
        if (pt != null) {
            int[] p = { pt.getLatitudeE6(), pt.getLongitudeE6() };
            intent.putExtra(EXTRA_SEARCHCENTER, p);
        }
        return intent;
    }

    public static final GeoPoint getSearchCenter(Intent intent) {
        int[] p = intent.getIntArrayExtra(EXTRA_SEARCHCENTER);
        if (p != null && p.length == 2) {
            return new GeoPoint(p[0], p[1]);
        }
        return null;
    }
}
