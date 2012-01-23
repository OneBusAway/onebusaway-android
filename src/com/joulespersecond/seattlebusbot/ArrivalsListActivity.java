package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.Window;


public class ArrivalsListActivity extends FragmentActivity {
    //private static final String TAG = "ArrivalInfoActivity";

    public static final String STOP_NAME = ".StopName";
    public static final String STOP_DIRECTION = ".StopDir";

    public static void start(Context context, String stopId) {
        context.startActivity(makeIntent(context, stopId));
    }

    public static void start(Context context, String stopId, String stopName) {
        context.startActivity(makeIntent(context, stopId, stopName));
    }

    public static void start(Context context, String stopId, String stopName, String stopDir) {
        context.startActivity(makeIntent(context, stopId, stopName, stopDir));
    }

    public static void start(Context context, ObaStop stop) {
        context.startActivity(makeIntent(context, stop));
    }

    public static Intent makeIntent(Context context, String stopId) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        return myIntent;
    }

    public static Intent makeIntent(Context context, String stopId, String stopName) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        myIntent.putExtra(STOP_NAME, stopName);
        return myIntent;
    }

    public static Intent makeIntent(Context context, String stopId, String stopName, String stopDir) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        myIntent.putExtra(STOP_NAME, stopName);
        myIntent.putExtra(STOP_DIRECTION, stopDir);
        return myIntent;
    }

    public static Intent makeIntent(Context context, ObaStop stop) {
        Intent myIntent = new Intent(context, ArrivalsListActivity.class);
        myIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
        myIntent.putExtra(STOP_NAME, stop.getName());
        myIntent.putExtra(STOP_DIRECTION, stop.getDirection());
        return myIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            ArrivalsListFragment list = new ArrivalsListFragment();
            list.setArguments(FragmentUtils.getIntentArgs(getIntent()));

            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
    }

    public ArrivalsListFragment getArrivalsListFragment() {
        FragmentManager fm = getSupportFragmentManager();
        return (ArrivalsListFragment)fm.findFragmentById(android.R.id.content);
    }
}
