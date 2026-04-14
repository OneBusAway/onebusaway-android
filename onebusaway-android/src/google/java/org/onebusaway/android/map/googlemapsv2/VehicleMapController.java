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

import android.content.Context;
import android.location.Location;
import android.util.Log;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.onebusaway.android.R;
import org.onebusaway.android.extrapolation.ExtrapolationResult;
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution;
import org.onebusaway.android.extrapolation.data.Trip;
import org.onebusaway.android.extrapolation.data.TripDataManager;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.ObaElementExtensionsKt;
import org.onebusaway.android.io.elements.Status;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.util.MathUtils;
import org.onebusaway.android.util.Polyline;
import org.onebusaway.android.util.UIUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages all vehicle markers on the Google Map: creation, position updates
 * (including extrapolation), selection, data-received markers, and cleanup.
 * Reads/writes {@link VehicleMarkerState} as pure data; all map API calls live
 * here.
 */
class VehicleMapController {

    private static final String TAG = "VehicleMapController";
    private static final float VEHICLE_MARKER_Z_INDEX = 1;
    private static final float DATA_RECEIVED_MARKER_Z_INDEX = 3.1f;

    private final GoogleMap mMap;
    private final Context mContext;
    private final VehicleIconFactory mIconFactory;
    private final TripDataManager mDataManager;
    private final int mAnimateDurationMs;

    private final HashMap<String, VehicleMarkerState> mStates = new HashMap<>();

    private BitmapDescriptor mDataReceivedIcon;

    VehicleMapController(GoogleMap map, Context context, VehicleIconFactory iconFactory,
            int animateDurationMs) {
        mMap = map;
        mContext = context.getApplicationContext();
        mIconFactory = iconFactory;
        mDataManager = TripDataManager.INSTANCE;
        mAnimateDurationMs = animateDurationMs;
    }

    // --- Populate from API response ---

    void populate(HashSet<String> routeIds, ObaTripsForRouteResponse response, long now) {
        HashSet<String> activeTripIds = new HashSet<>();
        HashMap<String, String> vehicleToTrip = new HashMap<>();

        for (ObaTripDetails trip : response.getTrips()) {
            ObaTripStatus status = trip.getStatus();
            if (status == null)
                continue;

            ObaTrip activeTrip = response.getTrip(status.getActiveTripId());
            if (activeTrip == null)
                continue;
            String activeRoute = activeTrip.getRouteId();
            if (!routeIds.contains(activeRoute) || Status.CANCELED.equals(status.getStatus()))
                continue;

            if (status.getPosition() == null)
                continue;

            boolean isRealtime = ObaElementExtensionsKt.isLocationRealtime(status);

            String tripId = status.getActiveTripId();
            String vehicleId = status.getVehicleId();

            // A vehicle that switches trips (e.g. finishing one run and starting
            // the next) keeps the same vehicleId but gets a new tripId. Remove
            // the stale marker for the old trip so it doesn't linger on the map.
            if (vehicleId != null) {
                String prevTrip = vehicleToTrip.put(vehicleId, tripId);
                if (prevTrip != null && !prevTrip.equals(tripId)) {
                    removeVehicleMarker(prevTrip);
                    activeTripIds.remove(prevTrip);
                }
            }

            VehicleMarkerState existing = mStates.get(tripId);
            if (existing == null) {
                addVehicle(tripId, isRealtime, status, response);
            } else {
                updateVehicle(existing, isRealtime, status, response);
            }
            activeTripIds.add(tripId);
        }

        removeInactiveMarkers(activeTripIds);
    }

    // --- Vehicle marker lifecycle ---

    private void addVehicle(String tripId, boolean isRealtime,
            ObaTripStatus status, ObaTripsForRouteResponse response) {
        Location location = status.getPosition();
        if (location == null)
            return;
        VehicleIconParams params = buildIconParams(isRealtime, status, response);
        Marker m = mMap.addMarker(new MarkerOptions()
                .position(MapHelpV2.makeLatLng(location))
                .title(status.getVehicleId())
                .icon(mIconFactory.getIcon(params))
                .zIndex(VEHICLE_MARKER_Z_INDEX));
        VehicleMarkerState vehicle = new VehicleMarkerState(
                mDataManager.getOrCreateTrip(tripId), status);
        vehicle.vehicleMarker = m;
        vehicle.iconParams = params;
        m.setTag(vehicle);
        mStates.put(tripId, vehicle);
    }

    private void updateVehicle(VehicleMarkerState vehicle, boolean isRealtime,
            ObaTripStatus status, ObaTripsForRouteResponse response) {
        Marker m = vehicle.vehicleMarker;
        boolean showInfo = m.isInfoWindowShown();
        VehicleIconParams params = buildIconParams(isRealtime, status, response);
        m.setIcon(mIconFactory.getIcon(params));
        vehicle.status = status;
        vehicle.iconParams = params;
        if (showInfo) {
            m.showInfoWindow();
        }
    }

    private static VehicleIconParams buildIconParams(boolean isRealtime, ObaTripStatus status,
            ObaTripsForRouteResponse response) {
        ObaTrip trip = response.getTrip(status.getActiveTripId());
        ObaRoute route = trip != null ? response.getRoute(trip.getRouteId()) : null;
        int vehicleType = route != null ? route.getType() : ObaRoute.TYPE_BUS;
        int colorResource = VehicleIconFactory.getDeviationColorResource(isRealtime, status);
        int halfWind = MathUtils.getHalfWindIndex(
                (float) MathUtils.toDirection(status.getOrientation()),
                VehicleIconFactory.NUM_DIRECTIONS - 1);
        return new VehicleIconParams(vehicleType, colorResource, halfWind);
    }

    private void removeInactiveMarkers(HashSet<String> activeTripIds) {
        Iterator<Map.Entry<String, VehicleMarkerState>> iterator = mStates.entrySet().iterator();
        while (iterator.hasNext()) {
            VehicleMarkerState vehicle = iterator.next().getValue();
            if (!activeTripIds.contains(vehicle.trip.getTripId())) {
                destroyVehicleMarker(vehicle);
                iterator.remove();
            }
        }
    }

    private void removeVehicleMarker(String tripId) {
        VehicleMarkerState vehicle = mStates.remove(tripId);
        if (vehicle != null) {
            destroyVehicleMarker(vehicle);
        }
    }

    private void destroyVehicleMarker(VehicleMarkerState vehicle) {
        vehicle.vehicleMarker.remove();
        removeDataReceivedMarker(vehicle);
    }

    // --- Data-received marker lifecycle ---

    private void showDataReceivedMarker(VehicleMarkerState vehicle, ObaTripStatus anchor) {
        removeDataReceivedMarker(vehicle);
        Location loc = anchor.getPosition();
        if (loc == null)
            return;
        Marker m = mMap.addMarker(new MarkerOptions()
                .position(MapHelpV2.makeLatLng(loc))
                .icon(getOrCreateDataReceivedIcon())
                .title(mContext.getString(R.string.marker_most_recent_data))
                .anchor(0.5f, 0.5f)
                .flat(true)
                .zIndex(DATA_RECEIVED_MARKER_Z_INDEX));
        vehicle.dataReceivedMarker = m;
        vehicle.dataReceivedFixTime = anchor.getLastUpdateTime();
        m.setTag(vehicle);
    }

    private void updateDataReceivedMarker(VehicleMarkerState vehicle, ObaTripStatus anchor) {
        if (anchor == null)
            return;
        if (vehicle.dataReceivedMarker == null) {
            showDataReceivedMarker(vehicle, anchor);
            return;
        }
        long fixTime = anchor.getLastUpdateTime();
        if (fixTime != vehicle.dataReceivedFixTime) {
            vehicle.dataReceivedFixTime = fixTime;
            Location loc = anchor.getPosition();
            if (loc != null) {
                AnimationUtil.animateMarkerTo(vehicle.dataReceivedMarker,
                        MapHelpV2.makeLatLng(loc), mAnimateDurationMs);
            }
        }
    }

    private void removeDataReceivedMarker(VehicleMarkerState vehicle) {
        if (vehicle.dataReceivedMarker != null) {
            vehicle.dataReceivedMarker.remove();
            vehicle.dataReceivedMarker = null;
        }
        vehicle.dataReceivedFixTime = 0;
    }

    private BitmapDescriptor getOrCreateDataReceivedIcon() {
        if (mDataReceivedIcon == null) {
            mDataReceivedIcon = MapIconUtils.createDataReceivedIcon(mContext);
        }
        return mDataReceivedIcon;
    }

    private static VehicleMarkerState stateOf(Marker marker) {
        Object tag = marker.getTag();
        return tag instanceof VehicleMarkerState ? (VehicleMarkerState) tag : null;
    }

    // --- Selection ---

    boolean handleMarkerClick(Marker marker) {
        VehicleMarkerState vehicle = stateOf(marker);
        if (vehicle == null)
            return false;
        if (marker.equals(vehicle.dataReceivedMarker)) {
            marker.showInfoWindow();
        } else {
            selectVehicleMarker(vehicle);
        }
        return true;
    }

    void selectVehicle(String tripId) {
        VehicleMarkerState vehicle = mStates.get(tripId);
        if (vehicle != null)
            selectVehicleMarker(vehicle);
    }

    private void selectVehicleMarker(VehicleMarkerState vehicle) {
        deselectAll();
        vehicle.selected = true;
        vehicle.vehicleMarker.showInfoWindow();
    }

    void deselectAll() {
        for (VehicleMarkerState vehicle : mStates.values()) {
            vehicle.selected = false;
            removeDataReceivedMarker(vehicle);
        }
    }

    // --- Queries ---

    ObaTripStatus getStatusFromMarker(Marker marker) {
        VehicleMarkerState vs = stateOf(marker);
        return vs != null ? vs.status : null;
    }

    boolean isExtrapolating(Marker marker) {
        VehicleMarkerState vs = stateOf(marker);
        return vs != null && vs.trip.getAnchor() != null;
    }

    boolean isDataReceivedMarker(Marker marker) {
        VehicleMarkerState vs = stateOf(marker);
        return vs != null && marker.equals(vs.dataReceivedMarker);
    }

    String getTripIdForDataReceivedMarker(Marker marker) {
        VehicleMarkerState vs = stateOf(marker);
        if (vs != null && marker.equals(vs.dataReceivedMarker))
            return vs.trip.getTripId();
        return null;
    }

    // --- Per-frame position updates ---

    void updatePositions(long now) {
        if (mStates.isEmpty())
            return;
        for (VehicleMarkerState vehicle : mStates.values()) {
            try {
                updatePosition(vehicle, now);
            } catch (RuntimeException e) {
                // Programming-error path (e.g. require() failure in the gamma model on a
                // degenerate schedule). Log so it surfaces, then degrade to the raw position.
                Log.w(TAG, "updatePosition failed for trip " + vehicle.trip.getTripId(), e);
                animateToRawPosition(vehicle);
            }
            Trip trip = vehicle.trip;
            updateSelectedMarker(vehicle, trip.getAnchor());
        }
    }

    private void updatePosition(VehicleMarkerState vehicle, long now) {
        Trip trip = vehicle.trip;
        ExtrapolationResult result = trip.extrapolate(now);
        if (!(result instanceof ExtrapolationResult.Success)) {
            animateToRawPosition(vehicle);
            return;
        }

        ProbDistribution dist = ((ExtrapolationResult.Success) result).getDistribution();
        Polyline polyline = trip.getPolyline();
        if (polyline == null) {
            animateToRawPosition(vehicle);
            return;
        }

        double medianDist = dist.median();
        // Bisect can return NaN on pathological CDFs; fall back to the raw position
        // rather than propagating NaN into Polyline.segmentIndex/interpolate.
        if (!Double.isFinite(medianDist)) {
            animateToRawPosition(vehicle);
            return;
        }
        int seg = polyline.segmentIndex(medianDist);
        updateDirectionIcon(vehicle, polyline, seg);

        Location loc = polyline.interpolate(medianDist, seg);
        if (loc == null) {
            animateToRawPosition(vehicle);
            return;
        }

        LatLng target = MapHelpV2.makeLatLng(loc);
        // "Fresh data arrived" iff Trip.anchor has been reassigned since the previous
        // frame. Reference equality on the anchor itself is the natural signal — it
        // changes exactly when recordStatus accepts a non-duplicate, newer status.
        ObaTripStatus currentAnchor = trip.getAnchor();
        boolean freshData = currentAnchor != vehicle.lastAnimatedAnchor;
        vehicle.lastAnimatedAnchor = currentAnchor;

        if (!vehicle.animating && positionChanged(vehicle, target)) {
            if (freshData) {
                startTransitionAnimation(vehicle, target);
            } else {
                vehicle.vehicleMarker.setPosition(target);
            }
        }
    }

    private void updateDirectionIcon(VehicleMarkerState vehicle, Polyline polyline, int seg) {
        int hw = halfWindAt(polyline, seg);
        if (hw >= 0 && hw != vehicle.iconParams.halfWind) {
            vehicle.iconParams.halfWind = hw;
            vehicle.vehicleMarker.setIcon(mIconFactory.getIcon(vehicle.iconParams));
        }
    }

    private void updateSelectedMarker(VehicleMarkerState vehicle, ObaTripStatus anchor) {
        if (!vehicle.selected)
            return;
        if (anchor != null) {
            updateDataReceivedMarker(vehicle, anchor);
        } else {
            removeDataReceivedMarker(vehicle);
        }
    }

    // --- Extrapolation helpers ---

    /**
     * Returns the half-wind direction index for the given segment, or -1 if
     * unavailable.
     */
    private static int halfWindAt(Polyline polyline, int seg) {
        float bearing = polyline.bearingAt(seg);
        if (Float.isNaN(bearing))
            return -1;
        return MathUtils.getHalfWindIndex(bearing, VehicleIconFactory.NUM_DIRECTIONS - 1);
    }

    private void startTransitionAnimation(VehicleMarkerState vehicle, LatLng target) {
        vehicle.animating = true;
        AnimationUtil.animateMarkerTo(vehicle.vehicleMarker, target, mAnimateDurationMs,
                () -> vehicle.animating = false);
    }

    private void animateToRawPosition(VehicleMarkerState vehicle) {
        Location loc = vehicle.status.getPosition();
        if (loc == null || vehicle.animating)
            return;
        LatLng target = MapHelpV2.makeLatLng(loc);
        if (positionChanged(vehicle, target)) {
            startTransitionAnimation(vehicle, target);
        }
    }

    private static boolean positionChanged(VehicleMarkerState vehicle, LatLng target) {
        LatLng current = vehicle.vehicleMarker.getPosition();
        return current.latitude != target.latitude || current.longitude != target.longitude;
    }

    // --- Lifecycle ---

    void clear() {
        for (VehicleMarkerState vehicle : mStates.values()) {
            destroyVehicleMarker(vehicle);
        }
        mStates.clear();
        mDataReceivedIcon = null;
    }

    int size() {
        return mStates.size();
    }
}
