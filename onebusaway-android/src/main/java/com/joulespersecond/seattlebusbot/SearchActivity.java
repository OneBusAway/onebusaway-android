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

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;


public class SearchActivity extends SherlockFragmentActivity {
    //private static final String TAG = "SearchActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        UIHelp.setupActionBar(this);
        handleIntent(getIntent());
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
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavHelp.goHome(this);
            return true;
        }
        return false;
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // handles a click on a search suggestion; launches activity to show word
            /*
            Intent wordIntent = new Intent(this, WordActivity.class);
            wordIntent.setData(intent.getData());
            startActivity(wordIntent);
            finish();
            */
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // handles a search query
            String query = intent.getStringExtra(SearchManager.QUERY);
            doSearch(query);
        }
    }

    private void doSearch(String query) {
        //Log.d(TAG, "Search: " + query);
        // Find both tabs and start a search for them...
        FragmentManager fm = getSupportFragmentManager();

        SearchResultsFragment list = (SearchResultsFragment) fm
                .findFragmentById(android.R.id.content);
        FragmentTransaction ft = fm.beginTransaction();
        // Create the list fragment and add it as our sole content.
        if (list != null) {
            // The only thing we can do is remove this fragment
            ft.remove(list);
        }

        // Create a new fragment
        list = new SearchResultsFragment();
        Bundle args = new Bundle();
        args.putString(SearchResultsFragment.QUERY_TEXT, query);
        list.setArguments(args);

        ft.add(android.R.id.content, list);
        ft.commit();
    }
}
