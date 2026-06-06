/*
 * Copyright (C) 2014-2026 University of South Florida, Open Transit Software Foundation
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
package org.onebusaway.android.map.googlemapsv2;

import android.app.Activity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.ui.TripDetailsActivity;
import org.onebusaway.android.ui.TripDetailsListFragment;

import java.util.HashSet;

/**
 * Bridge between map listener interfaces and {@link VehicleMapController}.
 * Owns the choreographer frame loop and delegates all marker management
 * to the controller.
 */
public class VehicleOverlay implements GoogleMap.OnInfoWindowClickListener, MarkerListeners {

    interface Controller {
        String getFocusedStopId();

        /** Whether the map UI hosting this overlay is currently shown to the user. */
        boolean isShown();
    }

    private static final int ANIMATE_DURATION_MS = 600;

    private final Activity mActivity;
    private final GoogleMap mMap;
    private VehicleMapController mController;
    private ObaTripsForRouteResponse mLastResponse;
    private Controller mAppController;
    private final ThrottledFrameLoop mFrameLoop;

    public VehicleOverlay(Activity activity, GoogleMap map) {
        mActivity = activity;
        mMap = map;
        mFrameLoop = new ThrottledFrameLoop(this::onExtrapolationTick);
        VehicleIconFactory iconFactory = new VehicleIconFactory(activity);
        VehicleInfoWindowAdapter adapter = new VehicleInfoWindowAdapter(activity,
                new VehicleInfoWindowAdapter.InfoSource() {
                    @Override
                    public ObaTripStatus getStatusFromMarker(Marker marker) {
                        return mController != null ? mController.getStatusFromMarker(marker) : null;
                    }

                    @Override
                    public boolean isDataReceivedMarker(Marker marker) {
                        return mController != null && mController.isDataReceivedMarker(marker);
                    }

                    @Override
                    public boolean isExtrapolating(Marker marker) {
                        return mController != null && mController.isExtrapolating(marker);
                    }

                    @Override
                    public ObaTripsForRouteResponse getLastResponse() {
                        return mLastResponse;
                    }
                });
        mMap.setInfoWindowAdapter(adapter);
        mMap.setOnInfoWindowClickListener(this);
        mController = new VehicleMapController(map, activity, iconFactory, ANIMATE_DURATION_MS);
    }

    public void setController(Controller controller) {
        mAppController = controller;
    }

    public void updateVehicles(HashSet<String> routeIds, ObaTripsForRouteResponse response) {
        mLastResponse = response;
        mController.populate(routeIds, response, System.currentTimeMillis());
        syncFrameLoop();
    }

    /** The host answers visibility through {@link Controller}; no host wired up means not shown. */
    private boolean isHostShown() {
        return mAppController != null && mAppController.isShown();
    }

    /**
     * Reconciles the frame loop with reality: it runs exactly while the host is shown and there
     * are markers to animate. Both inputs are derived on the spot, so this is safe to call from
     * any lifecycle edge or data update; each tick also re-derives them, so a missed call can
     * only delay a restart (until the next poll), never leak a running loop.
     */
    public void syncFrameLoop() {
        if (mController != null && mController.size() > 0 && isHostShown()) {
            mFrameLoop.start();
        } else {
            mFrameLoop.stop();
        }
    }

    public int size() {
        return mController != null ? mController.size() : 0;
    }

    public void clear() {
        mFrameLoop.stop();
        if (mController != null) {
            mController.clear();
        }
    }

    // --- Info window click ---

    @Override
    public void onInfoWindowClick(Marker marker) {
        if (mController == null) return;
        String tripId = mController.getTripIdForDataReceivedMarker(marker);
        if (tripId != null) {
            navigateToTripDetails(tripId);
            return;
        }
        ObaTripStatus status = mController.getStatusFromMarker(marker);
        if (status != null) {
            navigateToTripDetails(status.getActiveTripId());
        }
    }

    private void navigateToTripDetails(String tripId) {
        TripDetailsActivity.Builder builder = new TripDetailsActivity.Builder(mActivity, tripId);
        if (mAppController != null && mAppController.getFocusedStopId() != null) {
            builder.setStopId(mAppController.getFocusedStopId());
        }
        builder.setScrollMode(TripDetailsListFragment.SCROLL_MODE_VEHICLE)
                .setUpMode("back")
                .start();
    }

    // --- Marker click delegation ---

    public void selectTrip(String tripId) {
        if (mController != null && tripId != null) {
            mController.selectVehicle(tripId);
        }
    }

    @Override
    public boolean markerClicked(Marker marker) {
        if (mController == null) return false;
        return mController.handleMarkerClick(marker);
    }

    @Override
    public void removeMarkerClicked(LatLng latLng) {
        if (mController != null) mController.deselectAll();
    }

    private void onExtrapolationTick(long nowMs) {
        if (mController == null || !isHostShown()) {
            mFrameLoop.stop();
            return;
        }
        mController.updateVehicleMarkers(nowMs);
    }
}
