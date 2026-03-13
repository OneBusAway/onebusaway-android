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
package org.onebusaway.android.ui;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;

import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;


abstract class MyTabActivityBase extends AppCompatActivity {

    public static final String EXTRA_SHORTCUTMODE = ".ShortcutMode";

    public static final String EXTRA_SEARCHCENTER = ".SearchCenter"; //int[]

    public static final String RESULT_ROUTE_ID = ".RouteId";

    protected boolean mShortcutMode;

    protected Location mSearchCenter;

    protected String mDefaultTab;

    protected static class TabInfo {
        final String tag;
        final String title;
        final int iconResId;
        final Class<? extends Fragment> fragmentClass;

        TabInfo(String tag, String title, int iconResId, Class<? extends Fragment> fragmentClass) {
            this.tag = tag;
            this.title = title;
            this.iconResId = iconResId;
            this.fragmentClass = fragmentClass;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tabs);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UIUtils.setupActionBar(this);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        mShortcutMode = Intent.ACTION_CREATE_SHORTCUT.equals(action);
        if (!mShortcutMode) {
            setTitle(R.string.app_name);
        }

        mSearchCenter = getSearchCenter(intent);

        if (savedInstanceState != null) {
            mDefaultTab = savedInstanceState.getString("tab");
        }
        final Uri data = intent.getData();
        if (data != null && mDefaultTab == null) {
            mDefaultTab = getDefaultTabFromUri(data);
        }

        setupTabs();
    }

    private void setupTabs() {
        TabLayout tabLayout = findViewById(R.id.tabs);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        TabInfo[] tabInfos = getTabInfos();
        viewPager.setAdapter(new TabPagerAdapter(this, tabInfos));
        viewPager.setOffscreenPageLimit(tabInfos.length);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(tabInfos[position].title);
            tab.setIcon(tabInfos[position].iconResId);
            tab.setTag(tabInfos[position].tag);
        }).attach();

        restoreDefaultTab();
    }

    @Override
    public void onDestroy() {
        if (mDefaultTab == null) {
            TabLayout tabLayout = findViewById(R.id.tabs);
            int selectedPosition = tabLayout.getSelectedTabPosition();
            if (selectedPosition >= 0) {
                TabLayout.Tab tab = tabLayout.getTabAt(selectedPosition);
                if (tab != null) {
                    PreferenceUtils.saveString(getLastTabPref(), (String) tab.getTag());
                }
            }
        }

        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavHelp.goHome(this, false);
            return true;
        }
        return false;
    }

    protected void restoreDefaultTab() {
        final String def;
        if (mDefaultTab != null) {
            def = mDefaultTab;
        } else {
            SharedPreferences settings = Application.getPrefs();
            def = settings.getString(getLastTabPref(), null);
        }
        if (def != null) {
            TabLayout tabLayout = findViewById(R.id.tabs);
            for (int i = 0; i < tabLayout.getTabCount(); i++) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null && def.equals(tab.getTag())) {
                    tab.select();
                    break;
                }
            }
        }
    }

    //
    // Accessors for tabs
    //
    boolean isShortcutMode() {
        return mShortcutMode;
    }

    Location getSearchCenter() {
        return mSearchCenter;
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

    protected abstract TabInfo[] getTabInfos();

    //
    // Helper for getting the search center from the intent
    //
    public static final Intent putSearchCenter(Intent intent, Location pt) {
        if (pt != null) {
            double[] p = {pt.getLatitude(), pt.getLongitude()};
            intent.putExtra(EXTRA_SEARCHCENTER, p);
        }
        return intent;
    }

    private static final Location getSearchCenter(Intent intent) {
        double[] p = intent.getDoubleArrayExtra(EXTRA_SEARCHCENTER);
        if (p != null && p.length == 2) {
            return LocationUtils.makeLocation(p[0], p[1]);
        }
        return null;
    }

    private static class TabPagerAdapter extends FragmentStateAdapter {
        private final TabInfo[] tabInfos;

        TabPagerAdapter(FragmentActivity activity, TabInfo[] tabInfos) {
            super(activity);
            this.tabInfos = tabInfos;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            try {
                return tabInfos[position].fragmentClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to instantiate " + tabInfos[position].fragmentClass.getName()
                                + ". Ensure it has a public no-arg constructor.", e);
            }
        }

        @Override
        public int getItemCount() {
            return tabInfos.length;
        }
    }
}
