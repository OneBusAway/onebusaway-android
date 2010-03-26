package com.joulespersecond.seattlebusbot;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;

public class MyStopsActivity extends MyTabActivityBase {
    //private static final String TAG = "MyStopsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String action = getIntent().getAction();
        final Resources res = getResources();

        final TabHost tabHost = getTabHost();
        tabHost.addTab(tabHost.newTabSpec("recent")
                .setIndicator(res.getString(R.string.my_recent_title),
                              res.getDrawable(R.drawable.ic_tab_recent))
                .setContent(new Intent(this, MyRecentStopsActivity.class)
                                    .setAction(action)));
        tabHost.addTab(tabHost.newTabSpec("starred")
                .setIndicator(res.getString(R.string.my_starred_title),
                              res.getDrawable(R.drawable.ic_tab_starred))
                .setContent(new Intent(this, MyStarredStopsActivity.class)
                                    .setAction(action)));
        tabHost.addTab(tabHost.newTabSpec("search")
                .setIndicator(res.getString(R.string.my_search_title),
                              res.getDrawable(R.drawable.ic_tab_search))
                .setContent(new Intent(this, MySearchStopsActivity.class)
                                    .setAction(action)));

        restoreDefaultTab();
    }
    @Override
    protected String getLastTabPref() {
        return "MyStopsActivity.LastTab";
    }
}
