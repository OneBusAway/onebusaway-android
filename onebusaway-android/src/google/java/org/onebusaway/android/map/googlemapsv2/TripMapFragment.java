/*
 * Copyright (C) 2024 Open Transit Software Foundation
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
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Choreographer;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.request.ObaShapeRequest;
import org.onebusaway.android.io.request.ObaShapeResponse;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.extrapolation.math.speed.VehicleTrajectoryTrackerKt;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.extrapolation.math.SpeedDistribution;
import org.onebusaway.android.extrapolation.data.TripDataManager;
import org.onebusaway.android.extrapolation.data.TripDataManager.ShapeData;
import org.onebusaway.android.extrapolation.math.speed.VehicleTrajectoryTracker;

import java.util.HashMap;
import java.util.List;

/**
 * Standalone map fragment for displaying a single trip's route, stops,
 * vehicle position, and speed estimate overlays within TripDetailsActivity.
 */
public class TripMapFragment extends SupportMapFragment
        implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    public static final String TAG = "TripMapFragment";

    public interface Callback {
        void onShowList();
    }

    /**
     * Creates a TripMapFragment with the initial camera centered on the trip's
     * cached shape bounds (if available), so the map appears in the right place
     * without an animated transition from the default position.
     */
    public static TripMapFragment newInstance(String tripId) {
        GoogleMapOptions options = new GoogleMapOptions();
        List<Location> shape = TripDataManager.getInstance().getShape(tripId);
        LatLngBounds bounds = MapHelpV2.getBounds(shape);
        if (bounds != null) {
            options.camera(new CameraPosition(bounds.getCenter(), DEFAULT_INITIAL_ZOOM, 0, 0));
        }
        Bundle args = new Bundle();
        // "MapOptions" is the internal key SupportMapFragment uses to read
        // GoogleMapOptions from arguments (see SupportMapFragment.newInstance()).
        args.putParcelable("MapOptions", options);
        TripMapFragment fragment = new TripMapFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private static final float DEFAULT_INITIAL_ZOOM = 12f;

    private GoogleMap mMap;
    private TripMapRenderer mTripRenderer;
    private ChevronPolylineHelper mChevronHelper;

    private Marker mVehicleMarker;
    private final Location mReusableLocation = new Location("extrapolated");

    private boolean mExtrapolationTicking;
    private final Choreographer.FrameCallback mFrameCallback = this::onExtrapolationFrame;

    private String mTripId;
    private String mSelectedStopId;
    private ObaTripDetailsResponse mPendingResponse;
    private int mDeviationColor;
    private boolean mPendingActivation;

    private Callback mCallback;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Callback) {
            mCallback = (Callback) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mChevronHelper = new ChevronPolylineHelper();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mTripRenderer = new TripMapRenderer(mMap, requireContext(), mChevronHelper);
        mMap.setOnMarkerClickListener(this);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        MapHelpV2.applyMapStyle(mMap, requireContext());

        if (mPendingActivation) {
            mPendingActivation = false;
            doActivateTrip();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startExtrapolationTicking();
    }

    @Override
    public void onPause() {
        stopExtrapolationTicking();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopExtrapolationTicking();
        if (mTripRenderer != null) {
            mTripRenderer.deactivate();
            mTripRenderer = null;
        }
        if (mVehicleMarker != null) {
            mVehicleMarker.remove();
            mVehicleMarker = null;
        }
        mMap = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback = null;
    }

    // --- Menu ---

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.trip_details_map, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.show_list) {
            if (mCallback != null) {
                mCallback.onShowList();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Public API ---

    public void activateTrip(String tripId, String stopId, ObaTripDetailsResponse response) {
        mTripId = tripId;
        mSelectedStopId = stopId;
        mPendingResponse = response;
        if (mMap != null && mTripRenderer != null) {
            doActivateTrip();
        } else {
            mPendingActivation = true;
        }
    }

    // --- Internal activation ---

    private void doActivateTrip() {
        if (mPendingResponse == null || mTripId == null) return;

        ObaTripDetailsResponse response = mPendingResponse;
        mPendingResponse = null; // release reference after extracting needed data

        ObaTripSchedule schedule = response.getSchedule();
        ObaTripStatus status = response.getStatus();
        ObaReferences refs = response.getRefs();
        if (schedule == null || refs == null) return;

        ObaTrip trip = refs.getTrip(mTripId);
        if (trip == null) return;
        String routeId = trip.getRouteId();
        ObaRoute route = refs.getRoute(routeId);

        // Route color
        int routeColor = ContextCompat.getColor(requireContext(), R.color.route_line_color_default);
        if (route != null && route.getColor() != null) {
            routeColor = route.getColor();
        }

        // Route type
        Integer routeType = route != null ? route.getType() : null;

        // Schedule deviation + deviation color (cached for per-frame use)
        long scheduleDeviation = 0;
        LatLng vehiclePosition = null;
        if (status != null && mTripId.equals(status.getActiveTripId())) {
            scheduleDeviation = status.getScheduleDeviation();
            Location vLoc = status.getLastKnownLocation();
            if (vLoc == null) vLoc = status.getPosition();
            if (vLoc != null) {
                vehiclePosition = MapHelpV2.makeLatLng(vLoc);
            }
        }

        // Cache deviation color for per-frame use
        if (status != null) {
            boolean realtime = VehicleOverlay.isLocationRealtime(status);
            int colorRes = VehicleOverlay.getDeviationColorResource(realtime, status);
            mDeviationColor = ContextCompat.getColor(requireContext(), colorRes);
        } else {
            mDeviationColor = ContextCompat.getColor(requireContext(),
                    R.color.stop_info_scheduled_time);
        }

        // Cache schedule + service date in data manager
        TripDataManager dataManager = TripDataManager.getInstance();
        dataManager.putSchedule(mTripId, schedule);
        if (status != null && status.getServiceDate() > 0) {
            dataManager.putServiceDate(mTripId, status.getServiceDate());
        }

        // Build stop name map from refs
        HashMap<String, String> stopNames = new HashMap<>();
        ObaTripSchedule.StopTime[] stopTimes = schedule.getStopTimes();
        if (stopTimes != null) {
            for (ObaTripSchedule.StopTime st : stopTimes) {
                ObaStop stop = refs.getStop(st.getStopId());
                if (stop != null) {
                    stopNames.put(st.getStopId(), stop.getName());
                }
            }
        }

        // Get or fetch shape
        List<Location> shape = dataManager.getShape(mTripId);
        double[] cumDist = dataManager.getShapeCumulativeDistances(mTripId);

        if (shape != null && cumDist != null) {
            activateRenderer(shape, cumDist, schedule, routeColor,
                    vehiclePosition, routeType, stopNames, scheduleDeviation);
        } else {
            // Fetch shape in background
            String shapeId = trip.getShapeId();
            final LatLng vp = vehiclePosition;
            final int rc = routeColor;
            final Integer rt = routeType;
            final long sd = scheduleDeviation;
            if (shapeId != null) {
                new Thread(() -> {
                    try {
                        Context ctx = Application.get().getApplicationContext();
                        ObaShapeResponse shapeResponse =
                                ObaShapeRequest.newRequest(ctx, shapeId).call();
                        if (shapeResponse != null) {
                            List<Location> points = shapeResponse.getPoints();
                            if (points != null && !points.isEmpty()) {
                                dataManager.putShape(mTripId, points);
                                Activity activity = getActivity();
                                if (activity != null) {
                                    activity.runOnUiThread(() -> {
                                        if (!isAdded()) return;
                                        List<Location> s = dataManager.getShape(mTripId);
                                        double[] cd = dataManager.getShapeCumulativeDistances(mTripId);
                                        if (s != null && cd != null) {
                                            activateRenderer(s, cd, schedule, rc,
                                                    vp, rt, stopNames, sd);
                                        }
                                    });
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to fetch shape for " + mTripId, e);
                    }
                }).start();
            }
        }
    }

    private void activateRenderer(List<Location> shape, double[] cumDist,
                                  ObaTripSchedule schedule, int routeColor,
                                  LatLng vehiclePosition, Integer routeType,
                                  HashMap<String, String> stopNames, long scheduleDeviation) {
        if (mTripRenderer == null || mMap == null) return;

        mTripRenderer.activate(mTripId, shape, cumDist, schedule, routeColor,
                vehiclePosition, routeType, stopNames, scheduleDeviation,
                mSelectedStopId);

        fitCameraToShape(shape);
        startExtrapolationTicking();
    }

    private void fitCameraToShape(List<Location> shape) {
        if (mMap == null) return;
        LatLngBounds bounds = MapHelpV2.getBounds(shape);
        if (bounds == null) return;
        try {
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80));
        } catch (Exception e) {
            Log.w(TAG, "Failed to fit camera to shape bounds", e);
        }
    }

    // --- Choreographer frame callback ---

    private void startExtrapolationTicking() {
        if (!mExtrapolationTicking && mMap != null && mTripId != null) {
            mExtrapolationTicking = true;
            Choreographer.getInstance().postFrameCallback(mFrameCallback);
        }
    }

    private void stopExtrapolationTicking() {
        mExtrapolationTicking = false;
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    private void onExtrapolationFrame(long frameTimeNanos) {
        if (!mExtrapolationTicking || mTripId == null || mMap == null) {
            mExtrapolationTicking = false;
            return;
        }

        TripDataManager dataManager = TripDataManager.getInstance();
        ShapeData sd = dataManager.getShapeWithDistances(mTripId);
        if (sd == null || sd.points.isEmpty()) {
            // No shape data yet; stop ticking until activateRenderer starts us
            mExtrapolationTicking = false;
            return;
        }
        List<Location> shape = sd.points;
        double[] cumDist = sd.cumulativeDistances;

        long now = System.currentTimeMillis();
        List<ObaTripStatus> history = dataManager.getHistory(mTripId);
        VehicleTrajectoryTracker tracker = VehicleTrajectoryTracker.getInstance();
        Double speed = tracker.getEstimatedSpeed(mTripId);

        // Extrapolate vehicle marker position
        if (history != null && !history.isEmpty() && speed != null) {
            Double extrapolatedDist = VehicleTrajectoryTrackerKt.extrapolateDistance(
                    history, speed, now);
            if (extrapolatedDist != null) {
                if (LocationUtils.interpolateAlongPolyline(
                        shape, cumDist, extrapolatedDist, mReusableLocation)) {
                    LatLng pos = MapHelpV2.makeLatLng(mReusableLocation);
                    if (mVehicleMarker == null) {
                        mVehicleMarker = mMap.addMarker(new MarkerOptions()
                                .position(pos)
                                .anchor(0.5f, 0.5f)
                                .flat(true)
                                .zIndex(2f));
                    } else {
                        mVehicleMarker.setPosition(pos);
                    }
                }
            }
        }

        // Update estimate overlays and data-received marker
        if (mTripRenderer != null) {
            SpeedDistribution distribution = tracker.getLastDistribution();
            mTripRenderer.updateEstimateOverlays(distribution, shape, cumDist, history,
                    now, mDeviationColor);
            mTripRenderer.showOrUpdateDataReceivedMarker(mTripId, shape, cumDist, history);
        }

        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    // --- Marker click handling ---

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        if (mTripRenderer != null) {
            if (mTripRenderer.handleDataReceivedClick(marker)) return true;
            if (mTripRenderer.handleEstimateLabelClick(marker)) return true;
            if (mTripRenderer.handleStopMarkerClick(marker)) return true;
        }
        return false;
    }
}
