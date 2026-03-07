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

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class MyRecentStopsAndRoutesActivity extends MyTabActivityBase {
    //private static final String TAG = "RecentRoutesStopsActivity";

    private ViewPager2 mViewPager2;

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

        setContentView(R.layout.activity_recent_stops_routes);
        setTitle(R.string.my_recent_title);

        TabLayout tabLayout = findViewById(R.id.tabs);
        mViewPager2 = findViewById(R.id.view_pager);

        // FragmentStateAdapter handles Fragments
        RecentStopsPagerAdapter adapter =
                new RecentStopsPagerAdapter(this);
        mViewPager2.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, mViewPager2, (tab, position) -> {
            if (position == 0) {
                tab.setText(R.string.my_recent_stops);
                tab.setIcon(R.drawable.ic_menu_stop);
            } else {
                tab.setText(R.string.my_recent_routes);
                tab.setIcon(R.drawable.ic_bus);
            }
        }).attach();
        restoreDefaultTab();
    }

    private static class RecentStopsPagerAdapter extends FragmentStateAdapter {

        public RecentStopsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new MyRecentStopsFragment();
            } else {
                return new MyRecentRoutesFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
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

        if (mViewPager2 == null) return;

        if (mDefaultTab != null) {
            def = mDefaultTab;
        } else {
            SharedPreferences settings = Application.getPrefs();
            def = settings.getString(getLastTabPref(), null);
        }

        if (def != null) {
            // Map the saved String tag back to the ViewPager integer position
            if (def.equals(MyRecentStopsFragment.TAB_NAME)) {
                mViewPager2.setCurrentItem(0, false);
            } else if (def.equals(MyRecentRoutesFragment.TAB_NAME)) {
                mViewPager2.setCurrentItem(1, false);
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
            if (mViewPager2 != null) {
                int currentTab = mViewPager2.getCurrentItem();
                String tabName = (currentTab == 0)
                        ? MyRecentStopsFragment.TAB_NAME
                        : MyRecentRoutesFragment.TAB_NAME;
                PreferenceUtils.saveString(getLastTabPref(), tabName);
            }

            // Prevent parent classes from overwriting our save
            mDefaultTab = "saved";
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
