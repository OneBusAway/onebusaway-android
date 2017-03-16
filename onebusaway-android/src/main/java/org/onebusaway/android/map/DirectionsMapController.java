/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.map;

import org.onebusaway.android.io.elements.ObaShape;
import org.onebusaway.android.io.elements.ObaShapeElement;
import org.onebusaway.android.util.LocationUtils;
import org.opentripplanner.api.model.EncodedPolylineBean;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.routing.core.TraverseMode;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used to show trip plan results on the map
 */
public class DirectionsMapController implements MapModeController {

    private static final String TAG = "DirectionsMapController";

    private final Callback mFragment;

    private Itinerary mItinerary;

    private boolean mHasRoute = false;

    private Location mCenter;

    private Set<Integer> mMarkerIds;

    public DirectionsMapController(Callback callback) {
        mFragment = callback;
        mMarkerIds = new HashSet<>();
    }

    @Override
    public void setState(Bundle args) {
        if (args != null) {
            mItinerary = (Itinerary) args.getSerializable(MapParams.ITINERARY);
        }
        setMapState();
    }

    private void setMapState() {
        clearCurrentState();
        if (mItinerary == null) {
            return;
        }

        // Set route overlays for map. If there are no routes (ie start and end are same location)
        // zoom to origin.

        Leg firstLeg = mItinerary.legs.get(0);
        Leg lastLeg = mItinerary.legs.get(mItinerary.legs.size() - 1);
        Location start = LocationUtils.makeLocation(firstLeg.from.getLat(), firstLeg.from.getLon());
        Location end = LocationUtils.makeLocation(lastLeg.to.getLat(), lastLeg.to.getLon());
        mCenter = start;

        for (Leg leg : mItinerary.legs) {
            LegShape shape = new LegShape(leg.legGeometry);

            if (shape.getLength() > 0) {
                mHasRoute = true;
                int color = resolveColor(leg);
                mFragment.getMapView().setRouteOverlay(color, new LegShape[]{shape}, false);
            }
        }

        // Colors from https://developers.google.com/android/reference/com/google/android/gms/maps/model/BitmapDescriptorFactory.html
        // but we can't use the constants directly because we can't import Google Maps classes here
        float HUE_GREEN = 120.0f;
        float HUE_RED = 0.0f;

        // Add beginning marker
        int markerId = mFragment.getMapView().addMarker(start, HUE_GREEN);
        if (markerId != -1) {
            // If marker was successfully added, keep track of ID so we can clear it later
            mMarkerIds.add(markerId);
        }
        // Add end marker
        markerId = mFragment.getMapView().addMarker(end, HUE_RED);
        if (markerId != -1) {
            // If marker was successfully added, keep track of ID so we can clear it later
            mMarkerIds.add(markerId);
        }

        zoom();
    }

    /**
     * Clears the current state of the controller, so a new route can be loaded
     */
    private void clearCurrentState() {
        // Clear the existing route and vehicle overlays
        mFragment.getMapView().removeRouteOverlay();
        mFragment.getMapView().removeVehicleOverlay();
        mFragment.getMapView().removeStopOverlay(false);
        // Clear start/end markers
        for (int i : mMarkerIds) {
            mFragment.getMapView().removeMarker(i);
        }
        mMarkerIds.clear();
    }

    @Override
    public String getMode() {
        return MapParams.MODE_DIRECTIONS;
    }

    @Override
    public void destroy() {
        clearCurrentState();
    }

    @Override
    public void onPause() {
        // Don't care
    }

    /**
     * This is called when fm.beginTransaction().hide() or fm.beginTransaction().show() is called
     *
     * @param hidden True if the fragment is now hidden, false if it is not visible.
     */
    @Override
    public void onHidden(boolean hidden) {
        // Don't care
    }

    @Override
    public void onResume() {
        setMapState();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Don't care
    }

    @Override
    public void onLocation() {
        // Don't care
    }

    @Override
    public void onNoLocation() {
        // Don't care
    }

    @Override
    public void notifyMapChanged() {
        // Don't care
    }

    @Override
    public void onViewStateRestored(Bundle bundle) {
        // Don't care
    }

    private void zoom() {
        ObaMapView view = mFragment.getMapView();

        if (mHasRoute) {
            view.zoomToItinerary();
        } else {
            view.setMapCenter(mCenter, false, false);
            view.setZoom(MapParams.DEFAULT_ZOOM);
        }
    }

    private static int resolveColor(Leg leg) {
        if (leg.routeColor != null) {
            try {
                return Long.decode("0xFF" + leg.routeColor).intValue();
            } catch (Exception ex) {
                Log.e(TAG, "Error parsing color=" + leg.routeColor + ": " + ex.getMessage());
            }
        }

        if (TraverseMode.valueOf(leg.mode).isTransit()) {
            return Color.BLUE;
        }

        return Color.GRAY;
    }

    class LegShape implements ObaShape {

        private EncodedPolylineBean bean;

        LegShape(EncodedPolylineBean bean) {
            this.bean = bean;
        }

        @Override
        public int getLength() {
            return bean.getLength();
        }

        @Override
        public String getRawLevels() {
            return bean.getLevels();
        }

        @Override
        public List<Integer> getLevels() {
            return ObaShapeElement.decodeLevels(bean.getLevels(), bean.getLength());
        }

        @Override
        public List<Location> getPoints() {
            return ObaShapeElement.decodeLine(bean.getPoints(), bean.getLength());
        }

        @Override
        public String getRawPoints() {
            return bean.getPoints();
        }

    }
}
