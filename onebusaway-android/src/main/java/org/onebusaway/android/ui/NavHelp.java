/*
 * Copyright (C) 2013 Paul Watts (paulcwatts@gmail.com)
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

import org.onebusaway.android.util.ShowcaseViewUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

final class NavHelp {

    //
    // Up mode. This controls whether or not the logo (Up) button
    // goes back or goes home. Activity support is required:
    // the only activity that supports it now is the ArrivalsList.
    //
    public static final String UP_MODE = ".UpMode";

    //public static final String UP_MODE_HOME = "home";
    public static final String UP_MODE_BACK = "back";

    public static void goUp(Activity activity) {
        String mode = activity.getIntent().getStringExtra(UP_MODE);
        if (UP_MODE_BACK.equals(mode)) {
            activity.finish();
        } else {
            goHome(activity, false);
        }
    }

    /**
     * Go back to the HomeActivity
     *
     * @param showTutorial true if the welcome tutorial should be started, false if it should not
     */
    public static void goHome(Context context, boolean showTutorial) {
        Intent intent = new Intent(context, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (showTutorial) {
            intent.putExtra(ShowcaseViewUtils.TUTORIAL_WELCOME, true);
        }
        context.startActivity(intent);
    }
}
