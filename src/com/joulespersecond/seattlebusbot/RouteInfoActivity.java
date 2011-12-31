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

import com.joulespersecond.oba.provider.ObaContract;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

public class RouteInfoActivity extends FragmentActivity {

    public static void start(Context context, String routeId) {
        context.startActivity(makeIntent(context, routeId));
    }

    public static Intent makeIntent(Context context, String routeId) {
        Intent myIntent = new Intent(context, RouteInfoActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, routeId));
        return myIntent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            RouteInfoListFragment list = new RouteInfoListFragment();
            list.setArguments(FragmentUtils.getIntentArgs(getIntent()));

            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
    }
}
