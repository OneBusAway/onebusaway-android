package com.joulespersecond.seattlebusbot;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;

public class MyStopsActivity extends TabActivity {
    //private static final String TAG = "MyStopsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String action = getIntent().getAction();
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            setTitle(R.string.app_name);
        }

        final TabHost tabHost = getTabHost();
        tabHost.addTab(tabHost.newTabSpec("recent")
                .setIndicator(getString(R.string.my_recent_title))
                .setContent(new Intent(this, MyRecentStopsActivity.class)
                                    .setAction(action)));
        tabHost.addTab(tabHost.newTabSpec("starred")
                .setIndicator(getString(R.string.my_starred_title))
                .setContent(new Intent(this, MyStarredStopsActivity.class)
                                    .setAction(action)));
        tabHost.addTab(tabHost.newTabSpec("search")
                .setIndicator(getString(R.string.my_search_title))
                .setContent(new Intent(this, MySearchStopsActivity.class)
                                    .setAction(action)));
    }
    void returnShortcut(Intent intent) {
        setResult(RESULT_OK, intent);
        finish();
    }
}
