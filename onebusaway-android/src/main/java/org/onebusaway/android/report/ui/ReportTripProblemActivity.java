/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
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
package org.onebusaway.android.report.ui;

import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

/**
 * An activity to hold the ReportTripProblemFragment
 */
public class ReportTripProblemActivity extends AppCompatActivity {
//private static final String TAG = "ReportTripProblemActivity";

    public static class Builder {

        private Context mContext;

        private Intent mIntent;

        public Builder(Context context, ObaArrivalInfo arrival) {
            mContext = context;
            mIntent = new Intent(context, ReportTripProblemActivity.class);
            mIntent.putExtra(ReportTripProblemFragment.TRIP_ID, arrival.getTripId());
            mIntent.putExtra(ReportTripProblemFragment.STOP_ID, arrival.getStopId());
            // We don't use the stop name map here...we want the actual stop name.
            mIntent.putExtra(ReportTripProblemFragment.TRIP_NAME, arrival.getHeadsign());
            mIntent.putExtra(ReportTripProblemFragment.TRIP_SERVICE_DATE, arrival.getServiceDate());
            mIntent.putExtra(ReportTripProblemFragment.TRIP_VEHICLE_ID, arrival.getVehicleId());
        }

        public Intent getIntent() {
            return mIntent;
        }

        public void start() {
            mContext.startActivity(mIntent);
        }
    }

    public static void start(Context context, ObaArrivalInfo arrival) {
        new Builder(context, arrival).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        UIUtils.setupActionBar(this);

        FragmentManager fm = getSupportFragmentManager();

        // Create the fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            ReportTripProblemFragment list = new ReportTripProblemFragment();
            list.setArguments(FragmentUtils.getIntentArgs(getIntent()));
            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
    }

    @Override
    protected void onStart() {
        ObaAnalytics.reportActivityStart(this);
        super.onStart();
    }
}

