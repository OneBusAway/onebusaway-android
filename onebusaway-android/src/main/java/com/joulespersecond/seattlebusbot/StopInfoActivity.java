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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * This activity is deprecated, but we need to keep it around
 * because it is used by existing shortcuts.
 *
 * @author paulw
 */
@Deprecated
public class StopInfoActivity extends Activity {

    private static final String TAG = "StopInfoActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final Uri data = intent.getData();
        if (data != null) {
            ArrivalsListActivity.start(this, data.getLastPathSegment());
        } else {
            Log.e(TAG, "No stop ID!");
            finish();
        }

        finish();
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
}
