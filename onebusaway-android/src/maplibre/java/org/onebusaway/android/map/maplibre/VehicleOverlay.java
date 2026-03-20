/*
 * Copyright (C) 2014-2024 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.map.maplibre;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;
import androidx.core.content.ContextCompat;

import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.maps.MapLibreMap;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.OccupancyState;
import org.onebusaway.android.io.elements.Status;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.ui.TripDetailsActivity;
import org.onebusaway.android.ui.TripDetailsListFragment;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.MathUtils;
import org.onebusaway.android.util.UIUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A map overlay that shows vehicle positions on the MapLibre map.
 * Port of the Google Maps VehicleOverlay with the same icon/coloring logic.
 */
public class VehicleOverlay {

    interface Controller {
        String getFocusedStopId();
    }

    private static final String TAG = "VehicleOverlay";

    private final MapLibreMap mMap;

    private MarkerData mMarkerData;

    private final Activity mActivity;

    private ObaTripsForRouteResponse mLastResponse;

    private Controller mController;

    // Direction constants — clockwise, consistent with MathUtils
    private static final int NORTH = 0;
    private static final int NORTH_EAST = 1;
    private static final int EAST = 2;
    private static final int SOUTH_EAST = 3;
    private static final int SOUTH = 4;
    private static final int SOUTH_WEST = 5;
    private static final int WEST = 6;
    private static final int NORTH_WEST = 7;
    private static final int NO_DIRECTION = 8;
    private static final int NUM_DIRECTIONS = 9;

    private static final int DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS;

    // Icon caches
    private static LruCache<String, Bitmap> mVehicleUncoloredIcons;
    private static LruCache<String, Bitmap> mVehicleColoredIconCache;

    public VehicleOverlay(Activity activity, MapLibreMap map) {
        mActivity = activity;
        mMap = map;
        loadIcons();
        mMap.setInfoWindowAdapter(new CustomInfoWindowAdapter(mActivity));
    }

    public void setController(Controller controller) {
        mController = controller;
    }

    /**
     * Updates vehicles for the provided routeIds from the response.
     */
    public void updateVehicles(HashSet<String> routeIds, ObaTripsForRouteResponse response) {
        setupMarkerData();
        mLastResponse = response;
        mMarkerData.populate(routeIds, response);
    }

    public synchronized int size() {
        if (mMarkerData != null) {
            return mMarkerData.size();
        }
        return 0;
    }

    public synchronized void clear() {
        if (mMarkerData != null) {
            mMarkerData.clear();
            mMarkerData = null;
        }
    }

    /**
     * Called when a marker is clicked. Returns true if this overlay handled the click.
     */
    public boolean markerClicked(Marker marker) {
        if (mMarkerData == null) return false;
        ObaTripStatus status = mMarkerData.getStatusFromMarker(marker);
        if (status != null) {
            mMap.selectMarker(marker);
            return true;
        }
        return false;
    }

    /**
     * Called when the info window is clicked — opens TripDetailsActivity.
     * @return true if this overlay handled the click
     */
    public boolean onInfoWindowClick(Marker marker) {
        if (mMarkerData != null) {
            ObaTripStatus status = mMarkerData.getStatusFromMarker(marker);
            if (status != null) {
                TripDetailsActivity.Builder builder =
                        new TripDetailsActivity.Builder(mActivity, status.getActiveTripId());
                if (mController != null && mController.getFocusedStopId() != null) {
                    builder.setStopId(mController.getFocusedStopId());
                }
                builder.setScrollMode(TripDetailsListFragment.SCROLL_MODE_VEHICLE)
                        .setUpMode("back")
                        .start();
                return true;
            }
        }
        return false;
    }

    private void setupMarkerData() {
        if (mMarkerData == null) {
            mMarkerData = new MarkerData();
        }
    }

    // ========================================================================
    // Icon loading and coloring — pure Android, no map SDK dependency
    // ========================================================================

    private static void loadIcons() {
        final int MAX_CACHE_SIZE = 15;
        if (mVehicleUncoloredIcons == null) {
            mVehicleUncoloredIcons = new LruCache<>(MAX_CACHE_SIZE);
        }
        if (mVehicleColoredIconCache == null) {
            mVehicleColoredIconCache = new LruCache<>(MAX_CACHE_SIZE);
        }
    }

    private static Bitmap getIcon(int halfWind, int vehicleType) {
        if (!supportedVehicleType(vehicleType)) {
            vehicleType = DEFAULT_VEHICLE_TYPE;
        }
        String cacheKey = String.format("%d %d", halfWind, vehicleType);
        Bitmap b = mVehicleUncoloredIcons.get(cacheKey);
        if (b == null) {
            switch (vehicleType) {
                case ObaRoute.TYPE_BUS:
                    b = createBusIcon(halfWind);
                    break;
                case ObaRoute.TYPE_FERRY:
                    b = createFerryIcon(halfWind);
                    break;
                case ObaRoute.TYPE_TRAM:
                    b = createTramIcon(halfWind);
                    break;
                case ObaRoute.TYPE_SUBWAY:
                    b = createSubwayIcon(halfWind);
                    break;
                case ObaRoute.TYPE_RAIL:
                    b = createRailIcon(halfWind);
                    break;
            }
        }
        mVehicleUncoloredIcons.put(cacheKey, b);
        return b;
    }

    private static boolean supportedVehicleType(int vehicleType) {
        return vehicleType == ObaRoute.TYPE_BUS ||
                vehicleType == ObaRoute.TYPE_FERRY ||
                vehicleType == ObaRoute.TYPE_TRAM ||
                vehicleType == ObaRoute.TYPE_SUBWAY ||
                vehicleType == ObaRoute.TYPE_RAIL;
    }

    private static Bitmap createBusIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_north_inside);
            case NORTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_north_east_inside);
            case EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_east_inside);
            case SOUTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_south_east_inside);
            case SOUTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_south_inside);
            case SOUTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_south_west_inside);
            case WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_west_inside);
            case NORTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_north_west_inside);
            default: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_none_inside);
        }
    }

    private static Bitmap createTramIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_north_inside);
            case NORTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_north_east_inside);
            case EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_east_inside);
            case SOUTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_south_east_inside);
            case SOUTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_south_inside);
            case SOUTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_south_west_inside);
            case WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_west_inside);
            case NORTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_north_west_inside);
            default: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_none_inside);
        }
    }

    private static Bitmap createRailIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_north_inside);
            case NORTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_north_east_inside);
            case EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_east_inside);
            case SOUTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_south_east_inside);
            case SOUTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_south_inside);
            case SOUTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_south_west_inside);
            case WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_west_inside);
            case NORTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_north_west_inside);
            default: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_none_inside);
        }
    }

    private static Bitmap createFerryIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_north_inside);
            case NORTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_north_east_inside);
            case EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_east_inside);
            case SOUTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_south_east_inside);
            case SOUTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_south_inside);
            case SOUTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_south_west_inside);
            case WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_west_inside);
            case NORTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_north_west_inside);
            default: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_none_inside);
        }
    }

    private static Bitmap createSubwayIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_north_inside);
            case NORTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_north_east_inside);
            case EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_east_inside);
            case SOUTH_EAST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_south_east_inside);
            case SOUTH: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_south_inside);
            case SOUTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_south_west_inside);
            case WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_west_inside);
            case NORTH_WEST: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_north_west_inside);
            default: return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_none_inside);
        }
    }

    private void addBitmapToCache(String key, Bitmap bitmap) {
        if (getBitmapFromCache(key) == null) {
            mVehicleColoredIconCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromCache(String key) {
        return mVehicleColoredIconCache.get(key);
    }

    private String createBitmapCacheKey(int vehicleType, int halfWind, int colorResource) {
        if (!supportedVehicleType(vehicleType)) {
            vehicleType = DEFAULT_VEHICLE_TYPE;
        }
        return vehicleType + " " + halfWind + " " + colorResource;
    }

    private Bitmap getBitmap(int vehicleType, int colorResource, int halfWind) {
        int color = ContextCompat.getColor(mActivity, colorResource);
        if (vehicleType == ObaRoute.TYPE_CABLECAR) {
            vehicleType = ObaRoute.TYPE_TRAM;
        }
        String key = createBitmapCacheKey(vehicleType, halfWind, colorResource);
        Bitmap b = getBitmapFromCache(key);
        if (b == null) {
            b = UIUtils.colorBitmap(getIcon(halfWind, vehicleType), color);
            addBitmapToCache(key, b);
        }
        return b;
    }

    protected static boolean isLocationRealtime(ObaTripStatus status) {
        boolean isRealtime = true;
        Location l = status.getLastKnownLocation();
        if (l == null) {
            isRealtime = false;
        }
        if (!status.isPredicted()) {
            isRealtime = false;
        }
        return isRealtime;
    }

    // ========================================================================
    // MarkerData — tracks vehicle markers on the map
    // ========================================================================

    class MarkerData {

        private HashMap<Marker, ObaTripStatus> mVehicles;
        private HashMap<String, Marker> mVehicleMarkers;

        private static final int INITIAL_HASHMAP_SIZE = 5;

        MarkerData() {
            mVehicles = new HashMap<>(INITIAL_HASHMAP_SIZE);
            mVehicleMarkers = new HashMap<>(INITIAL_HASHMAP_SIZE);
        }

        synchronized void populate(HashSet<String> routeIds, ObaTripsForRouteResponse response) {
            int added = 0;
            int updated = 0;
            ObaTripDetails[] trips = response.getTrips();

            HashSet<String> activeTripIds = new HashSet<>();

            for (ObaTripDetails trip : trips) {
                ObaTripStatus status = trip.getStatus();
                if (status != null) {
                    String activeRoute = response.getTrip(status.getActiveTripId()).getRouteId();
                    if (routeIds.contains(activeRoute)
                            && !Status.CANCELED.equals(status.getStatus())) {
                        Location l = status.getLastKnownLocation();
                        boolean isRealtime = true;

                        if (l == null) {
                            l = status.getPosition();
                            isRealtime = false;
                        }
                        if (!status.isPredicted()) {
                            isRealtime = false;
                        }

                        Marker m = mVehicleMarkers.get(status.getActiveTripId());

                        if (m == null) {
                            addMarkerToMap(l, isRealtime, status, response);
                            added++;
                        } else {
                            updateMarker(m, l, isRealtime, status, response);
                            updated++;
                        }
                        activeTripIds.add(status.getActiveTripId());
                    }
                }
            }

            int removed = removeInactiveMarkers(activeTripIds);

            Log.d(TAG, "Added " + added + ", updated " + updated + ", removed " + removed
                    + ", total vehicle markers = " + mVehicleMarkers.size());
            Log.d(TAG, "Vehicle LRU cache size=" + mVehicleColoredIconCache.size() + ", hits="
                    + mVehicleColoredIconCache.hitCount() + ", misses="
                    + mVehicleColoredIconCache.missCount());
            Log.d(TAG, String.format("Raw uncolored vehicle LRU cache size=%d, hits=%d, misses=%d",
                    mVehicleUncoloredIcons.size(),
                    mVehicleUncoloredIcons.hitCount(),
                    mVehicleUncoloredIcons.missCount()));
        }

        private void addMarkerToMap(Location l, boolean isRealtime, ObaTripStatus status,
                                    ObaTripsForRouteResponse response) {
            Icon icon = getVehicleIcon(isRealtime, status, response);
            Marker m = mMap.addMarker(new MarkerOptions()
                    .position(MapHelpMapLibre.makeLatLng(l))
                    .title(status.getVehicleId())
                    .icon(icon)
            );
            mVehicleMarkers.put(status.getActiveTripId(), m);
            mVehicles.put(m, status);
        }

        private void updateMarker(Marker m, Location l, boolean isRealtime, ObaTripStatus status,
                                  ObaTripsForRouteResponse response) {
            m.setIcon(getVehicleIcon(isRealtime, status, response));
            mVehicles.put(m, status);
            // MapLibre deprecated annotations don't support marker animation — snap to new position
            m.setPosition(MapHelpMapLibre.makeLatLng(l));
        }

        private Icon getVehicleIcon(boolean isRealtime, ObaTripStatus status,
                                    ObaTripsForRouteResponse response) {
            String routeId = response.getTrip(status.getActiveTripId()).getRouteId();
            ObaRoute route = response.getRoute(routeId);
            int vehicleType = route.getType();

            int colorResource;
            if (isRealtime) {
                long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
                colorResource = ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
            } else {
                colorResource = R.color.stop_info_scheduled_time;
            }

            double direction = MathUtils.toDirection(status.getOrientation());
            int halfWind = MathUtils.getHalfWindIndex((float) direction, NUM_DIRECTIONS - 1);

            Bitmap b = getBitmap(vehicleType, colorResource, halfWind);
            return IconFactory.getInstance(mActivity).fromBitmap(b);
        }

        synchronized ObaTripStatus getStatusFromMarker(Marker marker) {
            return mVehicles.get(marker);
        }

        private int removeInactiveMarkers(HashSet<String> activeTripIds) {
            int removed = 0;
            try {
                Iterator<Map.Entry<String, Marker>> iterator =
                        mVehicleMarkers.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Marker> entry = iterator.next();
                    String tripId = entry.getKey();
                    Marker m = entry.getValue();
                    if (!activeTripIds.contains(tripId)) {
                        mMap.removeAnnotation(m);
                        mVehicles.remove(m);
                        iterator.remove();
                        removed++;
                    }
                }
            } catch (UnsupportedOperationException e) {
                Log.w(TAG, "Problem removing vehicle from HashMap using iterator: " + e);
                HashMap<String, Marker> copy = new HashMap<>(mVehicleMarkers);
                for (Map.Entry<String, Marker> entry : copy.entrySet()) {
                    String tripId = entry.getKey();
                    Marker m = entry.getValue();
                    if (!activeTripIds.contains(tripId)) {
                        mMap.removeAnnotation(m);
                        mVehicles.remove(m);
                        mVehicleMarkers.remove(tripId);
                        removed++;
                    }
                }
            }
            return removed;
        }

        private void removeMarkersFromMap() {
            for (Map.Entry<String, Marker> entry : mVehicleMarkers.entrySet()) {
                mMap.removeAnnotation(entry.getValue());
            }
        }

        synchronized void clear() {
            if (mVehicleMarkers != null) {
                removeMarkersFromMap();
                mVehicleMarkers.clear();
                mVehicleMarkers = null;
            }
            if (mVehicles != null) {
                mVehicles.clear();
                mVehicles = null;
            }
        }

        synchronized int size() {
            if (mVehicleMarkers != null) {
                return mVehicleMarkers.size();
            }
            return 0;
        }
    }

    // ========================================================================
    // CustomInfoWindowAdapter — rich info window when tapping vehicle markers
    // ========================================================================

    class CustomInfoWindowAdapter implements MapLibreMap.InfoWindowAdapter {

        private final LayoutInflater mInflater;
        private final Context mContext;

        CustomInfoWindowAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
            mContext = context;
        }

        @Nullable
        @Override
        public View getInfoWindow(@NonNull Marker marker) {
            if (mMarkerData == null) {
                return null;
            }
            ObaTripStatus status = mMarkerData.getStatusFromMarker(marker);
            if (status == null || mLastResponse == null) {
                return null;
            }

            View view = mInflater.inflate(R.layout.vehicle_info_window, null);
            Resources r = mContext.getResources();
            TextView routeView = view.findViewById(R.id.route_and_destination);
            TextView statusView = view.findViewById(R.id.status);
            TextView lastUpdatedView = view.findViewById(R.id.last_updated);
            ImageView moreView = view.findViewById(R.id.trip_more_info);
            moreView.setColorFilter(r.getColor(R.color.switch_thumb_normal_material_dark));
            ViewGroup occupancyView = view.findViewById(R.id.occupancy);

            ObaTrip trip = mLastResponse.getTrip(status.getActiveTripId());
            ObaRoute route = mLastResponse.getRoute(trip.getRouteId());

            routeView.setText(UIUtils.getRouteDisplayName(route) + " " +
                    mContext.getString(R.string.trip_info_separator) + " " +
                    UIUtils.formatDisplayText(trip.getHeadsign()));

            boolean isRealtime = isLocationRealtime(status);

            statusView.setBackgroundResource(R.drawable.round_corners_style_b_status);
            GradientDrawable d = (GradientDrawable) statusView.getBackground();

            int pSides = UIUtils.dpToPixels(mContext, 5);
            int pTopBottom = UIUtils.dpToPixels(mContext, 2);

            if (isRealtime) {
                long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
                String statusString = ArrivalInfoUtils.computeArrivalLabelFromDelay(r, deviationMin);
                statusView.setText(statusString);
                int statusColor = ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
                d.setColor(r.getColor(statusColor));
                statusView.setPadding(pSides, pTopBottom, pSides, pTopBottom);
            } else {
                statusView.setText(r.getString(R.string.stop_info_scheduled));
                d.setColor(r.getColor(R.color.stop_info_scheduled_time));
                lastUpdatedView.setText(r.getString(R.string.vehicle_last_updated_scheduled));
                statusView.setPadding(pSides, pTopBottom, pSides, pTopBottom);

                UIUtils.setOccupancyVisibilityAndColor(occupancyView, null, OccupancyState.HISTORICAL);
                UIUtils.setOccupancyContentDescription(occupancyView, null, OccupancyState.HISTORICAL);

                return view;
            }

            // Update last updated time (only for real-time info)
            long now = System.currentTimeMillis();
            long lastUpdateTime;
            if (status.getLastLocationUpdateTime() != 0) {
                lastUpdateTime = status.getLastLocationUpdateTime();
            } else {
                lastUpdateTime = status.getLastUpdateTime();
            }
            long elapsedSec = TimeUnit.MILLISECONDS.toSeconds(now - lastUpdateTime);
            long elapsedMin = TimeUnit.SECONDS.toMinutes(elapsedSec);
            long secMod60 = elapsedSec % 60;

            String lastUpdated;
            if (elapsedSec < 60) {
                lastUpdated = r.getString(R.string.vehicle_last_updated_sec, elapsedSec);
            } else {
                lastUpdated = r.getString(R.string.vehicle_last_updated_min_and_sec,
                        elapsedMin, secMod60);
            }
            lastUpdatedView.setText(lastUpdated);

            if (status.getOccupancyStatus() != null) {
                UIUtils.setOccupancyVisibilityAndColor(occupancyView, status.getOccupancyStatus(), OccupancyState.REALTIME);
                UIUtils.setOccupancyContentDescription(occupancyView, status.getOccupancyStatus(), OccupancyState.REALTIME);
            } else {
                UIUtils.setOccupancyVisibilityAndColor(occupancyView, null, OccupancyState.REALTIME);
                UIUtils.setOccupancyContentDescription(occupancyView, null, OccupancyState.REALTIME);
            }

            return view;
        }
    }
}
