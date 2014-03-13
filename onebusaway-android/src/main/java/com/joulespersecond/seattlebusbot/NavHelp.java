package com.joulespersecond.seattlebusbot;

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
            // goHome(activity);
        }
    }

//    public static void goHome(Context context) {
//        Intent intent = new Intent(context, HomeActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        context.startActivity(intent);
//    }
}
