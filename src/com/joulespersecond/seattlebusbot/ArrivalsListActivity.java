/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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


public class ArrivalsListActivity extends SherlockFragmentActivity {
    //private static final String TAG = "ArrivalInfoActivity";

    public static final String STOP_NAME = ".StopName";
    public static final String STOP_DIRECTION = ".StopDir";

    public static void start(Context context, String stopId) {
        context.startActivity(makeIntent(context, stopId));
    }

    public static void start(Context context, String stopId, String stopName) {
        context.startActivity(makeIntent(context, stopId, stopName));
    }

    public static void start(Context context, String stopId, String stopName, String stopDir) {
        context.startActivity(makeIntent(context, stopId, stopName, stopDir));
    }

    public static void start(Context context, ObaStop stop) {
        context.startActivity(makeIntent(context, stop));
    }

    public static Intent makeIntent(Context context, String stopId) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        return myIntent;
    }

    public static Intent makeIntent(Context context, String stopId, String stopName) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        myIntent.putExtra(STOP_NAME, stopName);
        return myIntent;
    }

    public static Intent makeIntent(Context context, String stopId, String stopName, String stopDir) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        myIntent.putExtra(STOP_NAME, stopName);
        myIntent.putExtra(STOP_DIRECTION, stopDir);
        return myIntent;
    }

    public static Intent makeIntent(Context context, ObaStop stop) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
        myIntent.putExtra(STOP_NAME, stop.getName());
        myIntent.putExtra(STOP_DIRECTION, stop.getDirection());
        return myIntent;
    }

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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            UIHelp.goHome(this);
            return true;
        }
        return false;
    }

    public ArrivalsListFragment getArrivalsListFragment() {
        FragmentManager fm = getSupportFragmentManager();
        return (ArrivalsListFragment)fm.findFragmentById(android.R.id.content);
    }
}
