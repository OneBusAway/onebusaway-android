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
package org.onebusaway.android.ui;

import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class RegionsActivity extends AppCompatActivity {
    /*
    public static final String RETURN_MODE = ".Return";
    public static final String RESULT_LAT = ".lat";
    public static final String RESULT_LON = ".lon";
    */

    public static void start(Context context) {
        Intent intent = new Intent(context, RegionsActivity.class);
        context.startActivity(intent);
    }

    /*
    public static void startForResult(Activity activity, int code) {
        Intent intent = new Intent(activity, AgenciesActivity.class);
        intent.putExtra(RETURN_MODE, true);
        activity.startActivityForResult(intent, code);
    }
    */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.setupActionBar(this);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            RegionsFragment list = new RegionsFragment();
            //list.setArguments(getIntent().getExtras());

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
            NavHelp.goHome(this, false);
            return true;
        }
        return false;
    }
}
