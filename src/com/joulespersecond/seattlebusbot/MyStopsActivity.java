package com.joulespersecond.seattlebusbot;

import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
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

        // A hack inspired mostly by:
        // http://stackoverflow.com/questions/1906314/android-tabwidget-in-light-theme
        // (Question by David Hedlund, answer by yanoka)
        //
        // This doesn't change any of the font sizes or colors, since those are fine for me.
        //
        tabHost.getTabWidget().setBackgroundColor(res.getColor(R.color.tab_widget_bg));
    }
    void returnShortcut(Intent intent) {
        setResult(RESULT_OK, intent);
        finish();
    }
}
