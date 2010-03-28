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

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;

public class MyRoutesActivity extends MyTabActivityBase {
    //private static final String TAG = "MyRoutesActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Resources res = getResources();
        final TabHost tabHost = getTabHost();
        tabHost.addTab(tabHost.newTabSpec(MyRecentRoutesActivity.TAB_NAME)
                .setIndicator(res.getString(R.string.my_recent_title),
                              res.getDrawable(R.drawable.ic_tab_recent))
                .setContent(new Intent(this, MyRecentRoutesActivity.class)
                                    .putExtra(EXTRA_SHORTCUTMODE, mShortcutMode)));
        /*
        tabHost.addTab(tabHost.newTabSpec("starred")
                .setIndicator(getString(R.string.my_starred_title))
                .setContent(new Intent(this, MyStarredRoutesActivity.class)
                                    .setAction(action)));
        */
        tabHost.addTab(tabHost.newTabSpec(MySearchRoutesActivity.TAB_NAME)
                .setIndicator(res.getString(R.string.my_search_title),
                                res.getDrawable(R.drawable.ic_tab_search))
                .setContent(new Intent(this, MySearchRoutesActivity.class)
                                    .putExtra(EXTRA_SHORTCUTMODE, mShortcutMode)));


        restoreDefaultTab();
    }

    @Override
    protected String getLastTabPref() {
        return "MyRoutesActivity.LastTab";
    }

}
