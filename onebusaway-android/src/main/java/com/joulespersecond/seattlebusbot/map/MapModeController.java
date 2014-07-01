/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com) and individual contributors.
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
package com.joulespersecond.seattlebusbot.map;

import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaStop;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

import java.util.List;

public interface MapModeController {

    /**
     * Controllers should make every attempt to communicate through
     * the Callback interface rather than accessing the MapFragment
     * directly, even if it means duplicating some functionality,
     * just to keep the separation between them clean.
     *
     * @author paulw
     */
    interface Callback {

        // Used by the controller to tell the Fragment what to do.
        Activity getActivity();

        View getView();

        // Can't use a LoaderManager with a SherlockMapActivity
        //LoaderManager getLoaderManager();

        void showProgress(boolean show);

        String getMapMode();

        void setMapMode(String mode, Bundle args);

        MapView getMapView();

        void showStops(List<ObaStop> stops, ObaReferences refs);

        void setMyLocation();

        void notifyOutOfRange();
    }

    /**
     * Interface used to abstract the MapView class, to allow multiple implementations
     * (e.g., Google Maps API v1, v2)
     *
     * @author barbeau
     */
    interface MapView {

        // Sets the current zoom level of the map
        void setZoom(float zoomLevel);

        // Returns the current center-point position of the map
        Location getMapCenter();

        void setMapCenter(Location location);

        // The current latitude span (from the top edge to the bottom edge of the map) in decimal degrees
        double getLatitudeSpan();

        // The current longitude span (from the left edge to the right edge of the map) in decimal degrees
        double getLongitudeSpan();

        // Returns the current zoom level of the map.
        float getZoomLevel();

        // Enables or disables hardware acceleration (needed for Maps API v1 workaround)
        void enableHWAccel(boolean enable);

        // Access the overlay list.
        List<Overlay> getOverlays();
    }

    // Interface used to abstract the Overlay class to allow multiple implementations
    interface Overlay {

    }

    String getMode();

    void setState(Bundle args);

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
