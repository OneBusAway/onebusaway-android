package com.joulespersecond.seattlebusbot.map;

import com.google.android.maps.MapView;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;

public interface MapFragmentController {
    /**
     * Controllers should make every attempt to communicate through
     * the Callback interface rather than accessing the MapFragment
     * directly, even if it means duplicating some functionality,
     * just to keep the separation between them clean.
     *
     * @author paulw
     *
     */
    interface FragmentCallback {
        // Used by the controller to tell the Fragment what to do.
        Activity getActivity();
        LoaderManager getLoaderManager();

        MapView getMapView();

        void setMyLocation();
        void notifyOutOfRange();
    }

    void initialize(Bundle savedInstanceState);
    void destroy();

    void onPause();
    void onResume();

    void onSaveInstanceState(Bundle outState);

    /**
     * Called when we have the user's location,
     * or when they explicitly said to go to their location.
     */
    void onLocation();

    /**
     * Called when we don't know the user's location.
     */
    void onNoLocation();
}
