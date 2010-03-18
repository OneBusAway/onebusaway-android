package com.joulespersecond.seattlebusbot;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;

public class MyRoutesActivity extends TabActivity {
    //private static final String TAG = "MyRoutesActivity";

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
                .setContent(new Intent(this, MyRecentRoutesActivity.class)
                                    .setAction(action)));
        /*
        tabHost.addTab(tabHost.newTabSpec("starred")
                .setIndicator(getString(R.string.my_starred_title))
                .setContent(new Intent(this, MyStarredRoutesActivity.class)
                                    .setAction(action)));
        */
        tabHost.addTab(tabHost.newTabSpec("search")
                .setIndicator(getString(R.string.my_search_title))
                .setContent(new Intent(this, MySearchRoutesActivity.class)
                                    .setAction(action)));
    }
    void returnShortcut(Intent intent) {
        setResult(RESULT_OK, intent);
        finish();
    }
    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.find_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_favorites) {
            clearFavorites();
            requery();
            return true;
        }
        return false;
    }
     */
}
