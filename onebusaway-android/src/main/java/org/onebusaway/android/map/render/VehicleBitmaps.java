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
package org.onebusaway.android.map.render;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.VisibleForTesting;
import androidx.collection.LruCache;
import androidx.core.content.ContextCompat;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.BitmapUtils;
import org.onebusaway.android.util.MathUtils;

import java.util.concurrent.TimeUnit;

/**
 * Flavor-neutral generation of vehicle marker <b>bitmaps</b> (the black direction-arrow template
 * icons, tinted by schedule deviation). Lives in {@code src/main} so both the Google flavor
 * (wrapping each Bitmap in a {@code BitmapDescriptor}) and the maplibre flavor (wrapping it in an
 * {@code Icon}) share one implementation + the two static LRU caches. This is the icon half of the
 * old {@code VehicleOverlay}.
 */
public final class VehicleBitmaps {

    private VehicleBitmaps() {
    }

    private static final int NORTH = 0;  // directions are clockwise, consistent with MathUtils class

    private static final int NORTH_EAST = 1;

    private static final int EAST = 2;

    private static final int SOUTH_EAST = 3;

    private static final int SOUTH = 4;

    private static final int SOUTH_WEST = 5;

    private static final int WEST = 6;

    private static final int NORTH_WEST = 7;

    private static final int NUM_DIRECTIONS = 9; // 8 directions + undirected vehicles

    private static final int DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS; // fall back on bus

    private static final int MAX_CACHE_SIZE = 15;

    private static final LruCache<String, Bitmap> sUncoloredIcons = new LruCache<>(MAX_CACHE_SIZE);

    private static final LruCache<String, Bitmap> sColoredIconCache = new LruCache<>(MAX_CACHE_SIZE);

    /**
     * Returns the tinted, direction-arrow vehicle bitmap for [vehicle] (the legacy getVehicleIcon body).
     *
     * The arrow points along the vehicle's live movement bearing on the route shape
     * ({@link VehicleMarker#getBearing()}, compass degrees, 0°=N clockwise) when known — so it follows
     * the extrapolation glide — and falls back to the status's reported orientation off-shape (NaN bearing).
     */
    public static Bitmap vehicleBitmap(Context context, VehicleMarker vehicle,
                                       ObaTripsForRouteResponse response) {
        return getBitmap(context, vehicleType(vehicle, response), colorResource(vehicle),
                directionIndex(vehicle));
    }

    /**
     * A stable key identifying the icon {@link #vehicleBitmap} returns for this vehicle — its type,
     * heading octant, and schedule-deviation color, the only inputs that change the bitmap. A renderer
     * caches one wrapper (a Google {@code BitmapDescriptor}) per key so it reuses it across frames even
     * when the bounded bitmap LRU evicts and recreates the underlying {@link Bitmap} on a busy route.
     */
    public static String iconKey(VehicleMarker vehicle, ObaTripsForRouteResponse response) {
        return "veh:" + createBitmapCacheKey(vehicleType(vehicle, response), directionIndex(vehicle),
                colorResource(vehicle));
    }

    /** The vehicle's route type, normalizing cablecar to tram so both the bitmap and key paths agree. */
    private static int vehicleType(VehicleMarker vehicle, ObaTripsForRouteResponse response) {
        ObaTripStatus status = vehicle.getStatus();
        int type = response.getRoute(response.getTrip(status.getActiveTripId()).getRouteId()).getType();
        return normalizeVehicleType(type);
    }

    /**
     * Collapses cablecar onto tram so a cablecar route and the equivalent tram route resolve to the same
     * icon (and therefore the same {@link #iconKey}); every other type passes through unchanged.
     */
    @VisibleForTesting
    static int normalizeVehicleType(int routeType) {
        return routeType == ObaRoute.TYPE_CABLECAR ? ObaRoute.TYPE_TRAM : routeType;
    }

    /** The schedule-deviation color (realtime) or the scheduled color — constant between polls. */
    private static int colorResource(VehicleMarker vehicle) {
        if (vehicle.isRealtime()) {
            long deviationMin = TimeUnit.SECONDS.toMinutes(vehicle.getStatus().getScheduleDeviation());
            return ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
        }
        return R.color.stop_info_scheduled_time;
    }

    /**
     * The 8-way heading slot (0..7) the icon for [vehicle] uses. Exposed so the renderer can cheaply
     * detect when a gliding vehicle's direction arrow needs re-stamping — the tinted bitmap only changes
     * when this index does (the color is constant between polls). A live vehicle always has a heading, so
     * the undirected slot ({@code NUM_DIRECTIONS - 1}) isn't reachable from here.
     */
    public static int directionIndex(VehicleMarker vehicle) {
        // The path bearing is already a compass direction; the server orientation needs converting.
        float pathBearing = vehicle.getBearing();
        double direction = Float.isNaN(pathBearing)
                ? MathUtils.toDirection(vehicle.getStatus().getOrientation())
                : pathBearing;
        return MathUtils.getHalfWindIndex((float) direction, NUM_DIRECTIONS - 1);
    }

    /** True if there is real-time location info for the status (last-known location + predicted). */
    public static boolean isLocationRealtime(ObaTripStatus status) {
        boolean isRealtime = status.getLastKnownLocation() != null;
        if (!status.isPredicted()) {
            isRealtime = false;
        }
        return isRealtime;
    }

    private static Bitmap getBitmap(Context context, int vehicleType, int colorResource, int halfWind) {
        int color = ContextCompat.getColor(context, colorResource);

        String key = createBitmapCacheKey(vehicleType, halfWind, colorResource);
        Bitmap b = sColoredIconCache.get(key);
        if (b == null) {
            b = BitmapUtils.colorBitmap(getIcon(halfWind, vehicleType), color);
            if (sColoredIconCache.get(key) == null) {
                sColoredIconCache.put(key, b);
            }
        }
        return b;
    }

    private static String createBitmapCacheKey(int vehicleType, int halfWind, int colorResource) {
        if (!supportedVehicleType(vehicleType)) {
            vehicleType = DEFAULT_VEHICLE_TYPE;
        }
        return vehicleType + " " + halfWind + " " + colorResource;
    }

    private static Bitmap getIcon(int halfWind, int vehicleType) {
        if (!supportedVehicleType(vehicleType)) {
            vehicleType = DEFAULT_VEHICLE_TYPE;
        }

        String cacheKey = String.format("%d %d", halfWind, vehicleType);
        Bitmap b = sUncoloredIcons.get(cacheKey);
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
                // default: not needed, since supported vehicles are checked prior
            }
        }
        sUncoloredIcons.put(cacheKey, b);
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
            case NORTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_north_inside);
            case NORTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_north_east_inside);
            case EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_east_inside);
            case SOUTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_south_east_inside);
            case SOUTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_south_inside);
            case SOUTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_south_west_inside);
            case WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_west_inside);
            case NORTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_north_west_inside);
            default:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_bus_smaller_none_inside);
        }
    }

    private static Bitmap createTramIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_north_inside);
            case NORTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_north_east_inside);
            case EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_east_inside);
            case SOUTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_south_east_inside);
            case SOUTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_south_inside);
            case SOUTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_south_west_inside);
            case WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_west_inside);
            case NORTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_north_west_inside);
            default:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_tram_smaller_none_inside);
        }
    }

    private static Bitmap createRailIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_north_inside);
            case NORTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_north_east_inside);
            case EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_east_inside);
            case SOUTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_south_east_inside);
            case SOUTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_south_inside);
            case SOUTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_south_west_inside);
            case WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_west_inside);
            case NORTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_north_west_inside);
            default:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_train_smaller_none_inside);
        }
    }

    private static Bitmap createFerryIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_north_inside);
            case NORTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_north_east_inside);
            case EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_east_inside);
            case SOUTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_south_east_inside);
            case SOUTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_south_inside);
            case SOUTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_south_west_inside);
            case WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_west_inside);
            case NORTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_north_west_inside);
            default:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_boat_smaller_none_inside);
        }
    }

    private static Bitmap createSubwayIcon(int halfWind) {
        Resources r = Application.get().getResources();
        switch (halfWind) {
            case NORTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_north_inside);
            case NORTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_north_east_inside);
            case EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_east_inside);
            case SOUTH_EAST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_south_east_inside);
            case SOUTH:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_south_inside);
            case SOUTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_south_west_inside);
            case WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_west_inside);
            case NORTH_WEST:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_north_west_inside);
            default:
                return BitmapFactory.decodeResource(r, R.drawable.ic_marker_with_subway_smaller_none_inside);
        }
    }
}
