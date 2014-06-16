package com.joulespersecond.seattlebusbot;

import android.app.Activity;
import android.content.Context;
import android.support.v4.app.Fragment;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Logger;
import com.google.android.gms.analytics.Tracker;

public class Analytics extends GoogleAnalytics {
    private static final boolean ANALYTICS_IS_DRY_RUN = BuildConfig.DEBUG ? true : false;

    public static GoogleAnalytics analytics;
    public static Tracker tracker;

    protected Analytics(Context context) {
        super(context);
        getTracker(context);
    }

    synchronized Tracker getTracker(Context context) {
        if (tracker==null) {
            analytics = GoogleAnalytics.getInstance(context);
            if(BuildConfig.DEBUG){
                analytics.getLogger().setLogLevel(Logger.LogLevel.VERBOSE);
            }
            analytics.setDryRun(ANALYTICS_IS_DRY_RUN);
            tracker = analytics.newTracker(R.xml.analytics);
        }
        return tracker;
    }

    private void reportScreenView(String screenName) {
        if (screenName != null) {
            tracker.setScreenName(screenName);
            tracker.send(new HitBuilders.AppViewBuilder().build());
        }
    }

    public void reportScreenView(Fragment fragment) {
        if (fragment != null && fragment.getActivity() != null) {
            reportScreenView(fragment.getActivity().getLocalClassName() + "/"
                    + fragment.getClass().getSimpleName());
        }
    }

    public void activityStart(Activity activity) {
        if (activity != null) {
            analytics.reportActivityStart(activity);

            reportScreenView(activity.getLocalClassName());
        }
    }

    public void activityStop(Activity activity) {
        if (activity != null) {
            analytics.reportActivityStop(activity);
        }
    }

    public void reportEvent(String category, String action, String label) {
        if (category != null) {
            tracker.send(new HitBuilders.EventBuilder()
                    .setCategory(category)
                    .setAction(action)
                    .setLabel(label)
                    .build());
        }
    }
}
