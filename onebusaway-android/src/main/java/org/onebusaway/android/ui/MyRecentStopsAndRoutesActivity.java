/*
 * Copyright (C) 2010-2015 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.util.UIHelp;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;

public class MyRecentStopsAndRoutesActivity extends MyTabActivityBase {
    //private static final String TAG = "RecentRoutesStopsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Resources res = getResources();
        final ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        bar.addTab(bar.newTab()
                .setTag(MyRecentStopsFragment.TAB_NAME)
                .setText(res.getString(R.string.my_recent_stops))
                .setIcon(ContextCompat.getDrawable(this, R.drawable.ic_tab_recent))
                .setTabListener(new TabListener<MyRecentStopsFragment>(
                        this,
                        MyRecentStopsFragment.TAB_NAME,
                        MyRecentStopsFragment.class)));
        bar.addTab(bar.newTab()
                .setTag(MyRecentRoutesFragment.TAB_NAME)
                .setText(res.getString(R.string.my_recent_routes))
                .setIcon(ContextCompat.getDrawable(this, R.drawable.ic_tab_recent))
                .setTabListener(new TabListener<MyRecentRoutesFragment>(
                        this,
                        MyRecentRoutesFragment.TAB_NAME,
                        MyRecentRoutesFragment.class)));

        restoreDefaultTab();

        UIHelp.setupActionBar(this);
        setTitle(R.string.my_recent_title);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObaAnalytics.reportActivityStart(this);
    }

    @Override
    protected String getLastTabPref() {
        return "RecentRoutesStopsActivity.LastTab";
    }
}
