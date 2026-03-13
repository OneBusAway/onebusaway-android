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

import android.content.Intent;
import android.os.Bundle;

import org.onebusaway.android.R;
import org.onebusaway.android.util.UIUtils;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;

public class MyRecentStopsAndRoutesActivity extends MyTabActivityBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent myIntent = getIntent();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
            ShortcutInfoCompat shortcut = getShortcut();
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null);
            setResult(RESULT_OK, shortcut.getIntent());
            finish();
            return;
        }
        setTitle(R.string.my_recent_title);
    }

    @Override
    protected TabInfo[] getTabInfos() {
        return new TabInfo[]{
                new TabInfo(MyRecentStopsFragment.TAB_NAME,
                        getString(R.string.my_recent_stops),
                        R.drawable.ic_menu_stop,
                        MyRecentStopsFragment.class),
                new TabInfo(MyRecentRoutesFragment.TAB_NAME,
                        getString(R.string.my_recent_routes),
                        R.drawable.ic_bus,
                        MyRecentRoutesFragment.class)
        };
    }

    @Override
    protected String getLastTabPref() {
        return "RecentRoutesStopsActivity.LastTab";
    }

    private ShortcutInfoCompat getShortcut() {
        return UIUtils.makeShortcutInfo(this,
                getString(R.string.my_recent_title),
                new Intent(this, MyRecentStopsAndRoutesActivity.class),
                R.drawable.ic_history);
    }
}
