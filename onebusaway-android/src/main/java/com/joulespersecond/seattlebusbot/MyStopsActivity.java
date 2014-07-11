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

import com.actionbarsherlock.app.ActionBar;

import android.content.res.Resources;
import android.os.Bundle;

public class MyStopsActivity extends MyTabActivityBase {
    //private static final String TAG = "MyStopsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //ensureSupportActionBarAttached();
        final Resources res = getResources();
        final ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

        bar.addTab(bar.newTab()
                .setTag(MyRecentStopsFragment.TAB_NAME)
                .setText(res.getString(R.string.my_recent_title))
                .setIcon(res.getDrawable(R.drawable.ic_tab_recent))
                .setTabListener(new TabListener<MyRecentStopsFragment>(
                        this,
                        MyRecentStopsFragment.TAB_NAME,
                        MyRecentStopsFragment.class)));
        bar.addTab(bar.newTab()
                .setTag(MyStarredStopsFragment.TAB_NAME)
                .setText(res.getString(R.string.my_starred_title))
                .setIcon(res.getDrawable(R.drawable.ic_tab_starred))
                .setTabListener(new TabListener<MyStarredStopsFragment>(
                        this,
                        MyStarredStopsFragment.TAB_NAME,
                        MyStarredStopsFragment.class)));
        bar.addTab(bar.newTab()
                .setTag(MySearchStopsFragment.TAB_NAME)
                .setText(res.getString(R.string.my_search_title))
                .setIcon(res.getDrawable(R.drawable.ic_tab_search))
                .setTabListener(new TabListener<MySearchStopsFragment>(
                        this,
                        MySearchStopsFragment.TAB_NAME,
                        MySearchStopsFragment.class)));

        restoreDefaultTab();
    }

    @Override
    public void onResume() {
        super.onResume();

        Application.getAnalytics().activityStart(this);
    }

    @Override
    public void onPause() {
        Application.getAnalytics().activityStop(this);

        super.onPause();
    }

    @Override
    protected String getLastTabPref() {
        return "MyStopsActivity.LastTab";
    }
}
