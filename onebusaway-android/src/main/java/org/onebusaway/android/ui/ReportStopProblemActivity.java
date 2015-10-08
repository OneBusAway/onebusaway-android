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
package org.onebusaway.android.ui;

import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIHelp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

/**
 * An activity to hold the ReportStopProblemFragment
 */
public class ReportStopProblemActivity extends AppCompatActivity {
//private static final String TAG = "ReportStopProblemActivity";

    public static class Builder {

        private Context mContext;

        private Intent mIntent;

        public Builder(Context context, ObaStop stop) {
            mContext = context;
            mIntent = new Intent(context, ReportStopProblemActivity.class);
            mIntent.putExtra(ReportStopProblemFragment.STOP_ID, stop.getId());
            mIntent.putExtra(ReportStopProblemFragment.STOP_NAME, stop.getName());
        }

        public Intent getIntent() {
            return mIntent;
        }

        public void start() {
            mContext.startActivity(mIntent);
        }
    }

    public static void start(Context context, ObaStop stop) {
        new Builder(context, stop).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        UIHelp.setupActionBar(this);

        FragmentManager fm = getSupportFragmentManager();

        // Create the fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            ReportStopProblemFragment list = new ReportStopProblemFragment();
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

