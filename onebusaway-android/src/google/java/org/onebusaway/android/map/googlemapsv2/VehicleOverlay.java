/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.collection.LruCache;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.OccupancyState;
import org.onebusaway.android.io.elements.Status;
import org.onebusaway.android.io.request.ObaShapeRequest;
import org.onebusaway.android.io.request.ObaShapeResponse;
import org.onebusaway.android.io.request.ObaTripDetailsRequest;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.extrapolation.math.speed.VehicleTrajectoryTrackerKt;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.extrapolation.math.SpeedDistribution;
import org.onebusaway.android.extrapolation.data.TripDataManager;
import org.onebusaway.android.extrapolation.math.speed.VehicleTrajectoryTracker;
import org.onebusaway.android.ui.TripDetailsActivity;
import org.onebusaway.android.ui.TripDetailsListFragment;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.MathUtils;
import org.onebusaway.android.util.UIUtils;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A map overlay that shows vehicle positions on the map
 */
public class VehicleOverlay implements MarkerListeners  {

    interface Controller {
        String getFocusedStopId();
        void onVehicleSelected(String tripId, LatLng vehiclePosition, Integer routeType,
                               long scheduleDeviation);
        void onVehicleDeselected();
    }

    private static final String TAG = "VehicleOverlay";

    private GoogleMap mMap;

    private MarkerData mMarkerData;

    private final Activity mActivity;

    private ObaTripsForRouteResponse mLastResponse;

    private View mVehicleInfoCard;
    private TextView mCardRouteView;
    private TextView mCardStatusView;
    private ViewGroup mCardOccupancyView;
    private String mCardTripId;

    private Controller mController;

    private TripMapRenderer mTripRenderer;

    private boolean mExtrapolationTicking;
    private final Choreographer.FrameCallback mFrameCallback = this::onExtrapolationFrame;

    private static final int NORTH = 0;  // directions are clockwise, consistent with MathUtils class

    private static final int NORTH_EAST = 1;

    private static final int EAST = 2;

    private static final int SOUTH_EAST = 3;

    private static final int SOUTH = 4;

    private static final int SOUTH_WEST = 5;

    private static final int WEST = 6;

    private static final int NORTH_WEST = 7;

    private static final int NO_DIRECTION = 8;

    private static final int NUM_DIRECTIONS = 9; // 8 directions + undirected mVehicles

    private static final int DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS; // fall back on bus

    /**
     * Directional icon resource IDs indexed by [vehicleType][halfWind].
     * Last entry in each row is the "no direction" fallback.
     */
    private static final int[][] VEHICLE_ICON_RES = {
            // TYPE_TRAM (0)
            {
                    R.drawable.ic_marker_with_tram_smaller_north_inside,
                    R.drawable.ic_marker_with_tram_smaller_north_east_inside,
                    R.drawable.ic_marker_with_tram_smaller_east_inside,
                    R.drawable.ic_marker_with_tram_smaller_south_east_inside,
                    R.drawable.ic_marker_with_tram_smaller_south_inside,
                    R.drawable.ic_marker_with_tram_smaller_south_west_inside,
                    R.drawable.ic_marker_with_tram_smaller_west_inside,
                    R.drawable.ic_marker_with_tram_smaller_north_west_inside,
                    R.drawable.ic_marker_with_tram_smaller_none_inside,
            },
            // TYPE_SUBWAY (1)
            {
                    R.drawable.ic_marker_with_subway_smaller_north_inside,
                    R.drawable.ic_marker_with_subway_smaller_north_east_inside,
                    R.drawable.ic_marker_with_subway_smaller_east_inside,
                    R.drawable.ic_marker_with_subway_smaller_south_east_inside,
                    R.drawable.ic_marker_with_subway_smaller_south_inside,
                    R.drawable.ic_marker_with_subway_smaller_south_west_inside,
                    R.drawable.ic_marker_with_subway_smaller_west_inside,
                    R.drawable.ic_marker_with_subway_smaller_north_west_inside,
                    R.drawable.ic_marker_with_subway_smaller_none_inside,
            },
            // TYPE_RAIL (2)
            {
                    R.drawable.ic_marker_with_train_smaller_north_inside,
                    R.drawable.ic_marker_with_train_smaller_north_east_inside,
                    R.drawable.ic_marker_with_train_smaller_east_inside,
                    R.drawable.ic_marker_with_train_smaller_south_east_inside,
                    R.drawable.ic_marker_with_train_smaller_south_inside,
                    R.drawable.ic_marker_with_train_smaller_south_west_inside,
                    R.drawable.ic_marker_with_train_smaller_west_inside,
                    R.drawable.ic_marker_with_train_smaller_north_west_inside,
                    R.drawable.ic_marker_with_train_smaller_none_inside,
            },
            // TYPE_BUS (3)
            {
                    R.drawable.ic_marker_with_bus_smaller_north_inside,
                    R.drawable.ic_marker_with_bus_smaller_north_east_inside,
                    R.drawable.ic_marker_with_bus_smaller_east_inside,
                    R.drawable.ic_marker_with_bus_smaller_south_east_inside,
                    R.drawable.ic_marker_with_bus_smaller_south_inside,
                    R.drawable.ic_marker_with_bus_smaller_south_west_inside,
                    R.drawable.ic_marker_with_bus_smaller_west_inside,
                    R.drawable.ic_marker_with_bus_smaller_north_west_inside,
                    R.drawable.ic_marker_with_bus_smaller_none_inside,
            },
            // TYPE_FERRY (4)
            {
                    R.drawable.ic_marker_with_boat_smaller_north_inside,
                    R.drawable.ic_marker_with_boat_smaller_north_east_inside,
                    R.drawable.ic_marker_with_boat_smaller_east_inside,
                    R.drawable.ic_marker_with_boat_smaller_south_east_inside,
                    R.drawable.ic_marker_with_boat_smaller_south_inside,
                    R.drawable.ic_marker_with_boat_smaller_south_west_inside,
                    R.drawable.ic_marker_with_boat_smaller_west_inside,
                    R.drawable.ic_marker_with_boat_smaller_north_west_inside,
                    R.drawable.ic_marker_with_boat_smaller_none_inside,
            },
    };

    // Vehicle type (if available) -> icon set
    private static LruCache<String, Bitmap> mVehicleUncoloredIcons;

    private static LruCache<String, Bitmap> mVehicleColoredIconCache;
    // Colored versions of vehicle_icons

    /**
     * If a vehicle moves less than this distance (in meters), it will be animated, otherwise it
     * will just disappear and then re-appear
     */
    private static final double MAX_VEHICLE_ANIMATION_DISTANCE = 400;

    /**
     * z-index used to show vehicle markers on top of stop markers (default marker z-index is 0)
     */
    private static final float VEHICLE_MARKER_Z_INDEX = 1;

    public VehicleOverlay(Activity activity, GoogleMap map) {
        mActivity = activity;
        mMap = map;
        loadIcons();
    }

    public void setController(Controller controller) {
        mController = controller;
    }

    void setTripRenderer(TripMapRenderer renderer) {
        mTripRenderer = renderer;
    }

    /**
     * Updates vehicles for the provided routeIds from the status info from the given
     * ObaTripsForRouteResponse
     *
     * @param routeIds routeIds for which to add vehicle markers to the map.  If a vehicle is
     *                 running a route that is not contained in this list, the vehicle won't be
     *                 shown on the map.
     * @param response response that contains the real-time status info
     */
    public void updateVehicles(HashSet<String> routeIds, ObaTripsForRouteResponse response) {
        // Make sure that the MarkerData has been initialized
        setupMarkerData();
        // Cache the response, so when a marker is tapped we can look up route names from routeIds, etc.
        mLastResponse = response;
        // Show the markers on the map
        mMarkerData.populate(routeIds, response);
        startExtrapolationTicking();
    }

    public synchronized int size() {
        if (mMarkerData != null) {
            return mMarkerData.size();
        } else {
            return 0;
        }
    }

    /**
     * Clears any vehicle markers from the map
     */
    public synchronized void clear() {
        stopExtrapolationTicking();
        if (mTripRenderer != null) {
            mTripRenderer.deactivate();
        }
        if (mMarkerData != null) {
            mMarkerData.clear();
            mMarkerData = null;
        }
        hideVehicleInfoCard();
    }

    /**
     * Cache the core black template Bitmaps used for vehicle icons
     */
    private static final void loadIcons() {
        /**
         * Cache for colored versions of the vehicle icons.  Total possible number of entries is
         * 9 directions * 4 color types (early, ontime, delayed, scheduled) = 36.  In a test,
         * the RouteMapController used around 15 bitmaps over a 30 min period for 4 vehicles on the
         * map at 10 sec refresh rate.  This can be more depending on the route configuration (if
         * the route has lots of curves) and number of vehicles.  To conserve memory, we'll set the
         * max cache size at 15.
         */
        final int MAX_CACHE_SIZE = 15;

        if (mVehicleUncoloredIcons == null) {
            mVehicleUncoloredIcons = new LruCache<>(MAX_CACHE_SIZE);
        }

        if (mVehicleColoredIconCache == null) {
            mVehicleColoredIconCache = new LruCache<>(MAX_CACHE_SIZE);
        }
    }

    /**
     * Gets the icon, ready to color for the given direction and vehicle type
     *
     * @param halfWind    an index between 0 and numHalfWinds-1 that can be used to retrieve
     *                    the direction name for that heading (known as "boxing the compass", down to the half-wind
     *                    level).
     * @param vehicleType type as defined by GTFS spec. Acceptable values contained in OBARoute.TYPE_*
     *
     * @return the icon ready to color
     */
    private static Bitmap getIcon(int halfWind, int vehicleType) {
        if (!supportedVehicleType(vehicleType)) {
            vehicleType = DEFAULT_VEHICLE_TYPE;
        }

        String cacheKey = String.format("%d %d", halfWind, vehicleType);

        Bitmap b = mVehicleUncoloredIcons.get(cacheKey);

        if (b == null) {  // cache miss
            int[] res = VEHICLE_ICON_RES[vehicleType];
            int idx = (halfWind >= 0 && halfWind < res.length - 1) ? halfWind : res.length - 1;
            b = BitmapFactory.decodeResource(Application.get().getResources(), res[idx]);
        }

        mVehicleUncoloredIcons.put(cacheKey, b);

        return b;
    }

    private static boolean supportedVehicleType(int vehicleType) {
        return vehicleType >= 0 && vehicleType < VEHICLE_ICON_RES.length;
    }

    /**
     * Add a Bitmap for a colored vehicle icon to the cache
     *
     * @param key    Key for the Bitmap to be added, created by createBitmapCacheKey(halfWind, colorResource)
     * @param bitmap Bitmap to be added that is a colored version of the core black vehicle icons
     */
    private void addBitmapToCache(String key, Bitmap bitmap) {
        // Only add if its not already in the cache
        if (getBitmapFromCache(key) == null) {
            mVehicleColoredIconCache.put(key, bitmap);
        }
    }

    /**
     * Get a Bitmap for a colored vehicle icon from the cache
     *
     * @param key Key for the Bitmap, created by createBitmapCacheKey(halfWind, colorResource)
     * @return Bitmap that is a colored version of the core black vehicle icons corresponding to the given key
     */
    private Bitmap getBitmapFromCache(String key) {
        return mVehicleColoredIconCache.get(key);
    }

    /**
     * Creates a key for the vehicle colored icons cache, based on the halfWind (direction) and
     * colorResource
     *
     * @param vehicleType   The type of vehicle based on the GTFS value
     *
     * @param halfWind      an index between 0 and numHalfWinds-1 that can be used to retrieve
     *                      the direction name for that heading (known as "boxing the compass", down to the half-wind
     *                      level).
     * @param colorResource the color resource ID for the schedule deviation
     * @return a String key for this direction and color vehicle bitmap icon
     */
    private String createBitmapCacheKey(int vehicleType, int halfWind, int colorResource) {
        if (!supportedVehicleType(vehicleType)) {
            vehicleType = DEFAULT_VEHICLE_TYPE;
        }

        return String.valueOf(vehicleType) + " " + String.valueOf(halfWind) + " " + String.valueOf(colorResource);
    }

    /**
     * Get the bitmap, using the cache where possible
     * @param vehicleType the vehicle type, as defined by the GTFS value
     * @param colorResource color resource ID for schedule deviation
     * @param halfWind the direction pointed for the icon
     * @return The bitmap representing the vehicle type with the color and direction
     */
    private Bitmap getBitmap(int vehicleType, int colorResource, int halfWind) {
        int color = ContextCompat.getColor(mActivity, colorResource);

        // Use tram icon for cablecar
        if (vehicleType == ObaRoute.TYPE_CABLECAR) {
            vehicleType = ObaRoute.TYPE_TRAM;
        }

        String key = createBitmapCacheKey(vehicleType, halfWind, colorResource);
        Bitmap b = getBitmapFromCache(key);
        if (b == null) {
            // Cache miss - create Bitmap and add to cache
            b = UIUtils.colorBitmap(getIcon(halfWind, vehicleType), color);
            addBitmapToCache(key, b);
        }
        return b;
    }

    private void initVehicleInfoCard() {
        mVehicleInfoCard = mActivity.findViewById(R.id.vehicleInfoCard);
        if (mVehicleInfoCard == null) return;
        mCardRouteView = mVehicleInfoCard.findViewById(R.id.route_and_destination);
        mCardStatusView = mVehicleInfoCard.findViewById(R.id.status);
        mCardOccupancyView = mVehicleInfoCard.findViewById(R.id.occupancy);
        ImageView moreView = mVehicleInfoCard.findViewById(R.id.trip_more_info);
        moreView.setColorFilter(
                mActivity.getResources().getColor(R.color.switch_thumb_normal_material_dark));
        mVehicleInfoCard.setOnClickListener(v -> onVehicleInfoCardClick());
    }

    private void onVehicleInfoCardClick() {
        if (mCardTripId == null) return;
        TripDetailsActivity.Builder builder =
                new TripDetailsActivity.Builder(mActivity, mCardTripId)
                        .setScrollMode(TripDetailsListFragment.SCROLL_MODE_VEHICLE)
                        .setUpMode("back");
        if (mController != null && mController.getFocusedStopId() != null) {
            builder.setStopId(mController.getFocusedStopId());
        }
        builder.start();
    }

    private void showVehicleInfoCard(ObaTripStatus status) {
        if (mVehicleInfoCard == null) initVehicleInfoCard();
        if (mVehicleInfoCard == null || mLastResponse == null) return;

        mCardTripId = status.getActiveTripId();
        ObaTrip trip = mLastResponse.getTrip(mCardTripId);
        ObaRoute route = mLastResponse.getRoute(trip.getRouteId());

        mCardRouteView.setText(UIUtils.getRouteDisplayName(route) + " "
                + mActivity.getString(R.string.trip_info_separator) + " "
                + UIUtils.formatDisplayText(trip.getHeadsign()));

        boolean isRealtime = isLocationRealtime(status);
        Resources r = mActivity.getResources();
        int statusColor = getDeviationColorResource(isRealtime, status);

        mCardStatusView.setBackgroundResource(R.drawable.round_corners_style_b_status);
        GradientDrawable d = (GradientDrawable) mCardStatusView.getBackground();
        d.setColor(r.getColor(statusColor));
        int pSides = UIUtils.dpToPixels(mActivity, 5);
        int pTopBottom = UIUtils.dpToPixels(mActivity, 2);
        mCardStatusView.setPadding(pSides, pTopBottom, pSides, pTopBottom);

        if (isRealtime) {
            long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
            mCardStatusView.setText(
                    ArrivalInfoUtils.computeArrivalLabelFromDelay(r, deviationMin));
            UIUtils.setOccupancyVisibilityAndColor(mCardOccupancyView,
                    status.getOccupancyStatus(), OccupancyState.REALTIME);
            UIUtils.setOccupancyContentDescription(mCardOccupancyView,
                    status.getOccupancyStatus(), OccupancyState.REALTIME);
        } else {
            mCardStatusView.setText(r.getString(R.string.stop_info_scheduled));
            UIUtils.setOccupancyVisibilityAndColor(mCardOccupancyView,
                    null, OccupancyState.HISTORICAL);
            UIUtils.setOccupancyContentDescription(mCardOccupancyView,
                    null, OccupancyState.HISTORICAL);
        }

        mVehicleInfoCard.setVisibility(View.VISIBLE);
    }

    private void hideVehicleInfoCard() {
        if (mVehicleInfoCard != null) {
            mVehicleInfoCard.setVisibility(View.GONE);
        }
    }

    private void startExtrapolationTicking() {
        if (!mExtrapolationTicking) {
            mExtrapolationTicking = true;
            Choreographer.getInstance().postFrameCallback(mFrameCallback);
        }
    }

    private void stopExtrapolationTicking() {
        mExtrapolationTicking = false;
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    private void onExtrapolationFrame(long frameTimeNanos) {
        if (!mExtrapolationTicking || mMarkerData == null) {
            mExtrapolationTicking = false;
            return;
        }
        mMarkerData.extrapolatePositions();
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    private void setupMarkerData() {
        if (mMarkerData == null) {
            mMarkerData = new MarkerData();
        }
    }


    /**
     * Programmatically selects a vehicle by trip ID, as if the user tapped it.
     */
    public void selectTrip(String tripId) {
        if (mMarkerData == null || tripId == null) return;
        Marker marker = mMarkerData.mVehicleMarkers.get(tripId);
        if (marker == null) return;
        ObaTripStatus status = mMarkerData.mVehicles.get(marker);
        if (status != null) {
            mMarkerData.setSelectedTripId(tripId);
            showVehicleInfoCard(status);
        }
    }

    @Override
    public boolean markerClicked(Marker marker) {
        if(mMarkerData == null) return false;
        if (mTripRenderer != null && mTripRenderer.handleEstimateLabelClick(marker)) {
            return true;
        }
        ObaTripStatus status = mMarkerData.getStatusFromMarker(marker);
        if (status != null) {
            mMarkerData.setSelectedTripId(status.getActiveTripId());
            showVehicleInfoCard(status);
            return true;
        }
        return false;
    }

    @Override
    public void removeMarkerClicked(LatLng latLng) {
        if (mMarkerData != null) {
            mMarkerData.clearSelectedTripId();
        }
        hideVehicleInfoCard();
    }

    /**
     * Data structures to track what vehicles are currently shown on the map
     */
    class MarkerData {

        /**
         * A cached set of vehicles that are currently shown on the map.  Since onMarkerClick()
         * provides a marker, we need a mapping of that marker to a vehicle/trip.
         * Marker that represents a vehicle is the key, and value is the status for the vehicle.
         */
        private HashMap<Marker, ObaTripStatus> mVehicles;

        /**
         * A cached set of vehicle markers currently shown on the map.  This is needed to
         * add/remove markers from the map.  activeTripId is the key - we can't use vehicleId
         * because we want to show an interpolated position (based on schedule data) for trips
         * without real-time data, and those statuses do not have vehicleIds associated with them,
         * but do have activeTripIds.
         */
        private HashMap<String, Marker> mVehicleMarkers;

        /** The activeTripId of the currently-selected (info-window-open) vehicle, or null. */
        private volatile String mSelectedTripId;

        /** Tracks trip IDs with in-flight schedule fetches to avoid duplicate requests. */
        private final HashSet<String> mPendingScheduleFetches = new HashSet<>();

        private static final int INITIAL_HASHMAP_SIZE = 5;

        MarkerData() {
            mVehicles = new HashMap<>(INITIAL_HASHMAP_SIZE);
            mVehicleMarkers = new HashMap<>(INITIAL_HASHMAP_SIZE);
        }

        /**
         * Updates markers for the provided routeIds from the status info from the given
         * ObaTripsForRouteResponse
         *
         * @param routeIds markers representing real-time positions for the provided routeIds will
         *                 be
         *                 added to the map.  The response may contain status info for other routes
         *                 as well - we'll only show markers for the routeIds in this HashSet.
         * @param response response that contains the real-time status info
         */
        synchronized void populate(HashSet<String> routeIds, ObaTripsForRouteResponse response) {
            int added = 0;
            int updated = 0;
            ObaTripDetails[] trips = response.getTrips();

            // Keep track of the activeTripIds that should be shown on the map, so we don't need
            // to iterate again later for this same info
            HashSet<String> activeTripIds = new HashSet<>();

            // Add or move markers for vehicles included in response
            for (ObaTripDetails trip : trips) {
                ObaTripStatus status = trip.getStatus();
                if (status != null) {
                    // Check if this vehicle is running a route we're interested in and isn't CANCELED
                    String activeRoute = response.getTrip(status.getActiveTripId()).getRouteId();
                    if (routeIds.contains(activeRoute) && !Status.CANCELED.equals(status.getStatus())) {
                        Location l = status.getLastKnownLocation();
                        boolean isRealtime = true;

                        if (l == null) {
                            // If a potentially extrapolated location isn't available, use last position
                            l = status.getPosition();
                            isRealtime = false;
                        }
                        if (!status.isPredicted()) {
                            isRealtime = false;
                        }

                        Marker m = mVehicleMarkers.get(status.getActiveTripId());

                        if (m == null) {
                            // New activeTripId
                            addMarkerToMap(l, isRealtime, status, response);
                            added++;
                        } else {
                            updateMarker(m, l, isRealtime, status, response);
                            updated++;
                        }
                        activeTripIds.add(status.getActiveTripId());

                        recordTrajectoryState(status, response);
                        fetchScheduleAndShapeIfNeeded(status, response);
                    }
                }
            }
            // Remove markers for any previously added tripIds that aren't in the current response
            int removed = removeInactiveMarkers(activeTripIds);

            // Update the data-received marker to reflect the latest AVL position
            if (mTripRenderer != null && mSelectedTripId != null) {
                TripDataManager dm = TripDataManager.getInstance();
                TripDataManager.ShapeData sd = dm.getShapeWithDistances(mSelectedTripId);
                if (sd != null) {
                    mTripRenderer.showOrUpdateDataReceivedMarker(mSelectedTripId,
                            sd.points, sd.cumulativeDistances,
                            dm.getHistory(mSelectedTripId));
                }
            }

            Log.d(TAG,
                    "Added " + added + ", updated " + updated + ", removed " + removed
                            + ", total vehicle markers = "
                            + mVehicleMarkers.size());
            Log.d(TAG, "Vehicle LRU cache size=" + mVehicleColoredIconCache.size() + ", hits="
                    + mVehicleColoredIconCache.hitCount() + ", misses=" + mVehicleColoredIconCache
                    .missCount());

            Log.d(TAG, String.format("Raw uncolored vehicle LRU cache size=%d, hits=%d, misses=%d",
                    mVehicleUncoloredIcons.size(),
                    mVehicleUncoloredIcons.hitCount(),
                    mVehicleUncoloredIcons.missCount()));
        }

        /**
         * Records a vehicle state snapshot and route type into the trajectory tracker.
         */
        private void recordTrajectoryState(ObaTripStatus status,
                                            ObaTripsForRouteResponse response) {
            TripDataManager dm = TripDataManager.getInstance();
            ObaTrip activeTripObj = response.getTrip(status.getActiveTripId());
            dm.recordStatus(status);
            if (dm.getRouteType(status.getActiveTripId()) == null) {
                String routeId = activeTripObj != null ? activeTripObj.getRouteId() : null;
                ObaRoute route = routeId != null ? response.getRoute(routeId) : null;
                if (route != null) {
                    dm.putRouteType(status.getActiveTripId(), route.getType());
                }
            }
        }

        /**
         * Fetches schedule and/or shape data in a background thread if not already cached.
         */
        private void fetchScheduleAndShapeIfNeeded(ObaTripStatus status,
                                                    ObaTripsForRouteResponse response) {
            TripDataManager dm = TripDataManager.getInstance();
            String tripId = status.getActiveTripId();
            ObaTrip activeTripObj = response.getTrip(tripId);
            String shapeId = activeTripObj != null ? activeTripObj.getShapeId() : null;
            boolean needSchedule = tripId != null
                    && !dm.isScheduleCached(tripId)
                    && !mPendingScheduleFetches.contains(tripId);
            boolean needShape = tripId != null && shapeId != null
                    && dm.getShape(tripId) == null;
            if (!needSchedule && !needShape) return;

            if (needSchedule) {
                mPendingScheduleFetches.add(tripId);
            }
            final Context ctx = Application.get().getApplicationContext();
            final boolean fetchSchedule = needSchedule;
            final boolean fetchShape = needShape;
            new Thread(() -> {
                try {
                    if (fetchSchedule) {
                        ObaTripDetailsResponse detailsResponse =
                                new ObaTripDetailsRequest.Builder(ctx, tripId)
                                        .setIncludeSchedule(true)
                                        .setIncludeStatus(false)
                                        .setIncludeTrip(false)
                                        .build()
                                        .call();
                        if (detailsResponse != null) {
                            ObaTripSchedule schedule = detailsResponse.getSchedule();
                            if (schedule != null) {
                                dm.putSchedule(tripId, schedule);
                            }
                        }
                        mPendingScheduleFetches.remove(tripId);
                    }
                    if (fetchShape) {
                        ObaShapeResponse shapeResponse =
                                ObaShapeRequest.newRequest(ctx, shapeId).call();
                        if (shapeResponse != null) {
                            List<Location> points = shapeResponse.getPoints();
                            if (points != null && !points.isEmpty()) {
                                dm.putShape(tripId, points);
                            }
                        }
                    }
                    if (tripId.equals(mSelectedTripId) && mController != null) {
                        mActivity.runOnUiThread(() -> {
                            if (tripId.equals(mSelectedTripId) && mController != null) {
                                Marker vm = mVehicleMarkers.get(tripId);
                                LatLng vPos = vm != null ? vm.getPosition() : null;
                                Integer rType = dm.getRouteType(tripId);
                                ObaTripStatus st = vm != null ? mVehicles.get(vm) : null;
                                long dev = st != null ? st.getScheduleDeviation() : 0;
                                mController.onVehicleSelected(tripId, vPos, rType, dev);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to fetch schedule/shape for " + tripId, e);
                    if (fetchSchedule) {
                        mPendingScheduleFetches.remove(tripId);
                    }
                }
            }).start();
        }

        /**
         * Places a marker on the map for this vehicle, and adds it to our marker HashMap
         *
         * @param l          Location to add the marker at
         * @param isRealtime true if the marker shown indicate real-time info, false if it should indicate schedule
         * @param status     the vehicles status to add to the map
         * @param response   the response which contained the provided status
         */
        private void addMarkerToMap(Location l, boolean isRealtime, ObaTripStatus status,
                                    ObaTripsForRouteResponse response) {

            Marker m = mMap.addMarker(new MarkerOptions()
                    .position(MapHelpV2.makeLatLng(l))
                    .title(status.getVehicleId())
                    .icon(getVehicleIcon(isRealtime, status, response))
            );
            ProprietaryMapHelpV2.setZIndex(m, VEHICLE_MARKER_Z_INDEX);
            mVehicleMarkers.put(status.getActiveTripId(), m);
            mVehicles.put(m, status);
            // Hide non-selected markers when a vehicle is selected
            if (mSelectedTripId != null
                    && !mSelectedTripId.equals(status.getActiveTripId())) {
                m.setVisible(false);
            }
        }

        /**
         * Update an existing marker on the map with the current vehicle status
         *
         * @param m          Marker to update
         * @param l          Location to add the marker at
         * @param isRealtime true if the marker shown indicate real-time info, false if it should
         *                   indicate schedule
         * @param status     real-time status of the vehicle
         * @param response   response containing the provided status
         */
        private void updateMarker(Marker m, Location l, boolean isRealtime, ObaTripStatus status,
                                  ObaTripsForRouteResponse response) {
            boolean showInfo = m.isInfoWindowShown();
            m.setIcon(getVehicleIcon(isRealtime, status, response));
            // Update Hashmap with newest status - needed to show info when tapping on marker
            mVehicles.put(m, status);
            // Only update position from server if extrapolation isn't active —
            // the frame callback handles smooth movement along the polyline
            String tripId = status.getActiveTripId();
            if (tripId == null || TripDataManager.getInstance().getShape(tripId) == null
                    || VehicleTrajectoryTracker.getInstance().getEstimatedSpeed(tripId) == null) {
                Location markerLoc = MapHelpV2.makeLocation(m.getPosition());
                if (l.distanceTo(markerLoc) < MAX_VEHICLE_ANIMATION_DISTANCE) {
                    AnimationUtil.animateMarkerTo(m, MapHelpV2.makeLatLng(l));
                } else {
                    m.setPosition(MapHelpV2.makeLatLng(l));
                }
            }
            // If the info window was shown, make sure its open (changing the icon could have closed it)
            if (showInfo) {
                m.showInfoWindow();
            }
        }

        /**
         * Removes any markers that don't currently represent active vehicles running a route
         *
         * @param activeTripIds a set of active tripIds that are currently running the routes.  Any
         *                      markers for tripIds that aren't in this set will be removed
         *                      from the map.
         * @return the number of removed markers
         */
        private int removeInactiveMarkers(HashSet<String> activeTripIds) {
            int removed = 0;
            // Loop using an Iterator, since per Oracle Iterator.remove() is the only safe way
            // to remove an item from a Collection during iteration:
            // http://docs.oracle.com/javase/tutorial/collections/interfaces/collection.html
            try {
                Iterator<Map.Entry<String, Marker>> iterator = mVehicleMarkers.entrySet()
                        .iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Marker> entry = iterator.next();
                    String tripId = entry.getKey();
                    Marker m = entry.getValue();
                    if (!activeTripIds.contains(tripId)) {
                        entry.getValue().remove();
                        mVehicles.remove(m);
                        iterator.remove();
                        removed++;
                    }
                }
            } catch (UnsupportedOperationException e) {
                Log.w(TAG, "Problem removing vehicle from HashMap using iterator: " + e);
                //The platform apparently didn't like the "efficient" way to do this, so we'll just
                //loop through a copy and remove what we don't want from the original
                HashMap<String, Marker> copy = new HashMap<>(mVehicleMarkers);
                for (Map.Entry<String, Marker> entry : copy.entrySet()) {
                    String tripId = entry.getKey();
                    Marker m = entry.getValue();
                    if (!activeTripIds.contains(tripId)) {
                        entry.getValue().remove();
                        mVehicles.remove(m);
                        mVehicleMarkers.remove(tripId);
                        removed++;
                    }
                }
            }
            return removed;
        }

        /**
         * Returns an icon for the vehicle that should be shown on the map
         *
         * @param isRealtime true if the marker shown indicate real-time info, false if it should
         *                   indicate schedule
         * @param status     the vehicles status to add to the map
         * @param response   the response which contained the provided status
         * @return an icon for the vehicle that should be shown on the map
         */
        private BitmapDescriptor getVehicleIcon(boolean isRealtime, ObaTripStatus status,
                                                ObaTripsForRouteResponse response) {
            String routeId = response.getTrip(status.getActiveTripId()).getRouteId();
            ObaRoute route = response.getRoute(routeId);
            int vehicleType = route.getType();
            int colorResource = getDeviationColorResource(isRealtime, status);
            double direction = MathUtils.toDirection(status.getOrientation());
            int halfWind = MathUtils.getHalfWindIndex((float) direction, NUM_DIRECTIONS - 1);

            Bitmap b = getBitmap(vehicleType, colorResource, halfWind);
            return BitmapDescriptorFactory.fromBitmap(b);
        }


        /**
         * Sets the selected trip and hides all other vehicle markers.
         */
        synchronized void setSelectedTripId(String tripId) {
            if (tripId != null && tripId.equals(mSelectedTripId)) return;
            String previousTripId = mSelectedTripId;
            mSelectedTripId = tripId;
            // Immediately restore the newly selected marker to its full icon
            if (tripId != null) {
                restoreMarkerIcon(mVehicleMarkers.get(tripId));
            }
            animateChangedIcons(previousTripId);
            if (mController != null && tripId != null) {
                Marker vehicleMarker = mVehicleMarkers.get(tripId);
                LatLng vehiclePos = vehicleMarker != null ? vehicleMarker.getPosition() : null;
                Integer routeType = TripDataManager.getInstance().getRouteType(tripId);
                ObaTripStatus status = vehicleMarker != null ? mVehicles.get(vehicleMarker) : null;
                long deviation = status != null ? status.getScheduleDeviation() : 0;
                mController.onVehicleSelected(tripId, vehiclePos, routeType, deviation);
            }
        }

        /**
         * Clears the selection, restoring all markers to full vehicle icons.
         */
        synchronized void clearSelectedTripId() {
            if (mSelectedTripId == null) return;
            String previousTripId = mSelectedTripId;
            mSelectedTripId = null;
            animateChangedIcons(previousTripId);
            if (mController != null) {
                mController.onVehicleDeselected();
            }
        }

        private void restoreMarkerIcon(Marker marker) {
            if (marker == null) return;
            ObaTripStatus status = mVehicles.get(marker);
            if (status == null) return;
            marker.setIcon(getVehicleIcon(
                    isLocationRealtime(status), status, mLastResponse));
        }

        /**
         * Toggles visibility for markers whose state changed due to selection.
         * @param previousTripId the previously selected trip, or null if none
         */
        private void animateChangedIcons(String previousTripId) {
            for (Map.Entry<String, Marker> entry : mVehicleMarkers.entrySet()) {
                String tripId = entry.getKey();
                // Skip the currently selected marker — already handled
                if (mSelectedTripId != null && mSelectedTripId.equals(tripId)) continue;

                boolean wasHidden = previousTripId != null && !previousTripId.equals(tripId);
                boolean shouldHide = mSelectedTripId != null && !mSelectedTripId.equals(tripId);
                if (wasHidden == shouldHide) continue;

                entry.getValue().setVisible(!shouldHide);
            }
        }

        synchronized ObaTripStatus getStatusFromMarker(Marker marker) {
            return mVehicles.get(marker);
        }

        private void removeMarkersFromMap() {
            for (Map.Entry<String, Marker> entry : mVehicleMarkers.entrySet()) {
                entry.getValue().remove();
            }
        }

        /** Reusable Location to avoid per-frame allocation in extrapolation. */
        private final Location mReusableLocation = new Location("extrapolated");

        /**
         * Extrapolates vehicle positions using trajectory data and moves markers
         * along their route polylines. Called every frame via Choreographer.
         */
        void extrapolatePositions() {
            if (mVehicleMarkers == null || mVehicleMarkers.isEmpty()) return;

            TripDataManager dm = TripDataManager.getInstance();
            VehicleTrajectoryTracker tracker = VehicleTrajectoryTracker.getInstance();
            long now = System.currentTimeMillis();

            // Capture selected-trip data for estimate markers
            SpeedDistribution selectedDistribution = null;
            List<Location> selectedShape = null;
            double[] selectedCumDist = null;
            List<ObaTripStatus> selectedHistory = null;
            int selectedColor = 0;

            for (Map.Entry<String, Marker> entry : mVehicleMarkers.entrySet()) {
                String tripId = entry.getKey();
                Marker marker = entry.getValue();

                TripDataManager.ShapeData sd = dm.getShapeWithDistances(tripId);
                if (sd == null || sd.points.isEmpty()) continue;
                List<Location> shape = sd.points;
                double[] cumDist = sd.cumulativeDistances;

                List<ObaTripStatus> history = dm.getHistory(tripId);
                Double speed = tracker.getEstimatedSpeed(tripId);

                // Capture data for estimate markers regardless of speed availability
                if (tripId.equals(mSelectedTripId)) {
                    selectedDistribution = tracker.getLastDistribution();
                    selectedShape = shape;
                    selectedCumDist = cumDist;
                    selectedHistory = history;
                    ObaTripStatus status = mVehicles.get(marker);
                    if (status != null) {
                        int colorRes = getDeviationColorResource(
                                isLocationRealtime(status), status);
                        selectedColor = ContextCompat.getColor(mActivity, colorRes);
                    }
                }

                if (history == null || history.isEmpty() || speed == null) continue;

                Double extrapolatedDist = VehicleTrajectoryTrackerKt.extrapolateDistance(
                        history, speed, now);
                if (extrapolatedDist == null) continue;

                if (!LocationUtils.interpolateAlongPolyline(
                        shape, cumDist, extrapolatedDist, mReusableLocation)) {
                    continue;
                }

                marker.setPosition(new LatLng(
                        mReusableLocation.getLatitude(), mReusableLocation.getLongitude()));
            }

            // Update estimate overlays for the selected vehicle
            if (mTripRenderer != null) {
                mTripRenderer.showOrUpdateDataReceivedMarker(mSelectedTripId,
                        selectedShape, selectedCumDist, selectedHistory);
                mTripRenderer.updateEstimateOverlays(selectedDistribution, selectedShape,
                        selectedCumDist, selectedHistory, now, selectedColor);
            }
        }


        /**
         * Clears any stop markers from the map
         */
        synchronized void clear() {
            if (mVehicleMarkers != null) {
                // Clear all markers from the map
                removeMarkersFromMap();

                // Clear the data structures
                mVehicleMarkers.clear();
                mVehicleMarkers = null;
            }
            if (mVehicles != null) {
                mVehicles.clear();
                mVehicles = null;
            }
        }

        synchronized int size() {
            return mVehicleMarkers.size();
        }
    }

    /**
     * Returns true if there is real-time location information for the given status, false if there
     * is not
     *
     * @param status The trip status information that includes location information
     * @return true if there is real-time location information for the given status, false if there
     * is not
     */
    protected static boolean isLocationRealtime(ObaTripStatus status) {
        return status.getLastKnownLocation() != null && status.isPredicted();
    }

    /**
     * Returns the color resource for a vehicle's schedule deviation status.
     */
    static int getDeviationColorResource(boolean isRealtime, ObaTripStatus status) {
        if (isRealtime) {
            long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
            return ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
        }
        return R.color.stop_info_scheduled_time;
    }

}