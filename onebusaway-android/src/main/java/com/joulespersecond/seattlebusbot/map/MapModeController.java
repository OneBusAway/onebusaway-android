/*
 * Copyright (C) 2011-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), and individual contributors.
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

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.oba.elements.ObaStop;

import java.util.List;

public interface MapModeController {

    /**
     * The percentage of the map that the bottom sliding overlay will cover when expanded,
     * from 0 to 1
     */
    public static final float OVERLAY_PERCENTAGE = 0.7f;

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

        ObaMapView getMapView();

        void showStops(List<ObaStop> stops, ObaReferences refs);

        void setMyLocation(boolean useDefaultZoom, boolean animateToLocation);

        void notifyOutOfRange();
    }

    /**
     * Interface used to abstract the ObaMapView class, to allow multiple implementations
     * (e.g., Google Maps API v1, v2)
     *
     * @author barbeau
     */
    interface ObaMapView {

        // Sets the current zoom level of the map
        void setZoom(float zoomLevel);

        // Returns the current center-point position of the map
        Location getMapCenterAsLocation();

        // Sets the map center, taking into account whether the overlay is expanded
        void setMapCenter(Location location, boolean overlayExpanded);

        // The current latitude span (from the top edge to the bottom edge of the map) in decimal degrees
        double getLatitudeSpanInDecDegrees();

        // The current longitude span (from the left edge to the right edge of the map) in decimal degrees
        double getLongitudeSpanInDecDegrees();

        // Returns the current zoom level of the map.
        float getZoomLevelAsFloat();

        // Set lines to be shown on the map view
        void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes);

        // Zoom to line overlay of route
        void zoomToRoute();

        // Post invalidate
        void postInvalidate();

        // Removes the route from the map
        void removeRouteOverlay();

        // Returns true if the map is capable of watching itself, false if it needs an external watcher
        boolean canWatchMapChanges();
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

    /**
     * For maps that can watch themselves for changes in zoom/center, this is after a change
     */
    void notifyMapChanged();
}
