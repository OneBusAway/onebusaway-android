/*
 * Copyright (C) 2016 University of South Florida
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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

/**
 * Activity package names were refactored from com.joulespersecond.seattlebusbot to
 * org.onebusaway.android.ui after application version 1.7.9. When a user creates a shortcut with an
 * older version of OBA, the legacy shortcuts won't run when they upgrade the app to version 2.
 *
 * See issue #562 (https://github.com/OneBusAway/onebusaway-android/issues/562#issuecomment-229048016)
 * for more details.
 */
@Deprecated
public class RouteInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        intent.setClass(this, org.onebusaway.android.ui.RouteInfoActivity.class);
        startActivity(intent);
        finish();
    }
}
