package com.joulespersecond.seattlebusbot;

import com.joulespersecond.seattlebusbot.map.MapFragment;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentMapActivity;
import android.support.v4.view.Window;

public class HomeActivity extends FragmentMapActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            MapFragment map = new MapFragment();
            // TODO: Set arguments to the map (focus ID, route mode, etc)

            fm.beginTransaction().add(android.R.id.content, map).commit();
        }
    }

    @Override
    protected boolean isRouteDisplayed() {
        // TODO: This should be true if RouteMode is on
        return false;
    }

}
