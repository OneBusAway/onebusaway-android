/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Benjamin Du (bendu@me.com)
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

import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class TripDetailsActivity extends AppCompatActivity {

    public static class Builder {

        private Context mContext;

        private Intent mIntent;

        public Builder(Context context, String tripId) {
            mContext = context;
            mIntent = new Intent(context, TripDetailsActivity.class);
            mIntent.putExtra(TripDetailsListFragment.TRIP_ID, tripId);
        }

        public Builder setStopId(String stopId) {
            mIntent.putExtra(TripDetailsListFragment.STOP_ID, stopId);
            return this;
        }

        public Builder setScrollMode(String mode) {
            mIntent.putExtra(TripDetailsListFragment.SCROLL_MODE, mode);
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

    public static void start(Context context, String tripId) {
        new Builder(context, tripId).start();
    }

    public static void start(Context context, String tripId, String mode) {
        new Builder(context, tripId).setScrollMode(mode).start();
    }

    public static void start(Context context, String tripId, String stopId, String mode) {
        new Builder(context, tripId).setStopId(stopId).setScrollMode(mode).start();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.setupActionBar(this);

        FragmentManager fm = getSupportFragmentManager();

        if (fm.findFragmentById(android.R.id.content) == null) {
            TripDetailsListFragment list = new TripDetailsListFragment();
            list.setArguments(FragmentUtils.getIntentArgs(getIntent()));

            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObaAnalytics.reportActivityStart(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavHelp.goUp(this);
            return true;
        }
        return false;
    }
}
