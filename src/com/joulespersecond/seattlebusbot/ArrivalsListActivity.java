/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;


public class ArrivalsListActivity extends SherlockFragmentActivity {
    //private static final String TAG = "ArrivalInfoActivity";

    public static final String STOP_NAME = ".StopName";
    public static final String STOP_DIRECTION = ".StopDir";

    public static class Builder {
        private Context mContext;
        private Intent mIntent;

        public Builder(Context context, String stopId) {
            mContext = context;
            mIntent = new Intent(context, ArrivalsListActivity.class);
            mIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        }

        public Builder(Context context, ObaStop stop) {
            mContext = context;
            mIntent = new Intent(context, ArrivalsListActivity.class);
            mIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
            setStopName(stop.getName());
            setStopDirection(stop.getDirection());
        }

        public Builder setStopName(String stopName) {
            mIntent.putExtra(STOP_NAME, stopName);
            return this;
        }

        public Builder setStopDirection(String stopDir) {
            mIntent.putExtra(STOP_DIRECTION, stopDir);
            return this;
        }

        public Builder setUpMode(String mode) {
            mIntent.putExtra(NavHelp.UP_MODE, mode);
            return this;
        }

        public Intent getIntent() {
            return mIntent;
        }

        public void start() {
            mContext.startActivity(mIntent);
        }
    }

    //
    // Two of the most common methods of starting this activity.
    //
    public static void start(Context context, String stopId) {
        new Builder(context, stopId).start();
    }

    public static void start(Context context, ObaStop stop) {
        new Builder(context, stop).start();
    }

    private boolean mNewFragment = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        UIHelp.setupActionBar(this);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            ArrivalsListFragment list = new ArrivalsListFragment();
            list.setArguments(FragmentUtils.getIntentArgs(getIntent()));
            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        //
        // We can't modify the fragment state here
        // because the activity manager still thinks we're not saved.
        // So we have to put it off.
        //
        mNewFragment = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean newFrag = mNewFragment;
        mNewFragment = false;
        if (newFrag) {
            FragmentManager fm = getSupportFragmentManager();

            ArrivalsListFragment list = new ArrivalsListFragment();
            list.setArguments(FragmentUtils.getIntentArgs(getIntent()));

            FragmentTransaction ft = fm.beginTransaction();
            ft.replace(android.R.id.content, list);
            // This is a bit of a hack, but if there's a backstack
            // it means people navigated away from this activity while
            // in a report problem fragment.
            // In this case, we *want* to be a part of the backstack;
            // otherwise we just want to clear everything out.
            if (fm.getBackStackEntryCount() > 0) {
                ft.addToBackStack(null);
            }
            ft.commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavHelp.goUp(this);
            return true;
        }
        return false;
    }

    public ArrivalsListFragment getArrivalsListFragment() {
        FragmentManager fm = getSupportFragmentManager();
        return (ArrivalsListFragment)fm.findFragmentById(android.R.id.content);
    }
}
