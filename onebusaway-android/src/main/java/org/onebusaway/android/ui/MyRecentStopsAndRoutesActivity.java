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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v7.app.ActionBar;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;

public class MyRecentStopsAndRoutesActivity extends MyTabActivityBase {
    //private static final String TAG = "RecentRoutesStopsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent myIntent = getIntent();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
            ShortcutInfoCompat shortcut = getShortcut();
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null);
            setResult(RESULT_OK, shortcut.getIntent());
            finish();
        }

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

        UIUtils.setupActionBar(this);
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

    /**
     * Override default tab handling behavior of MyTabActivityBase - use tab text instead of tag
     * for saving to preference. See #585.
     */
    @Override
    protected void restoreDefaultTab() {
        final String def;
        if (mDefaultTab != null) {
            def = mDefaultTab;
            if (def != null) {
                // Find this tab...
                final ActionBar bar = getSupportActionBar();
                for (int i = 0; i < bar.getTabCount(); ++i) {
                    ActionBar.Tab tab = bar.getTabAt(i);
                    // Still use tab.getTag() here, as its driven by intent or saved instance state
                    if (def.equals(tab.getTag())) {
                        tab.select();
                    }
                }
            }
        } else {
            SharedPreferences settings = Application.getPrefs();
            def = settings.getString(getLastTabPref(), null);
            if (def != null) {
                // Find this tab...
                final ActionBar bar = getSupportActionBar();
                for (int i = 0; i < bar.getTabCount(); ++i) {
                    ActionBar.Tab tab = bar.getTabAt(i);
                    if (def.equals(tab.getText())) {
                        tab.select();
                    }
                }
            }
        }

    }

    /**
     * Override default tab handling behavior of MyTabActivityBase - use tab text instead of tag
     * for saving to preference. See #585.
     */
    @Override
    public void onDestroy() {
        // If there was a tab in the intent, don't save it
        if (mDefaultTab == null) {
            final ActionBar bar = getSupportActionBar();
            final ActionBar.Tab tab = bar.getSelectedTab();
            PreferenceUtils.saveString(getLastTabPref(), (String) tab.getText());

            // Assign a value to mDefaultTab so that super().onDestroy() doesn't overwrite the
            // preference we set above.  FIXME - this is a total hack.
            mDefaultTab = "hack";
        }

        super.onDestroy();
    }

    private ShortcutInfoCompat getShortcut() {
        return UIUtils.makeShortcutInfo(this,
                getString(R.string.my_recent_title),
                new Intent(this, MyRecentStopsAndRoutesActivity.class),
                R.drawable.ic_history);
    }
}
