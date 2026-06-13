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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.collection.LruCache;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.UIUtils;

import java.util.concurrent.TimeUnit;

/**
 * Creates and caches vehicle marker icons for the map, handling direction,
 * vehicle type, and schedule deviation coloring.
 */
public class VehicleIconFactory {

    private static final int NORTH = 0;
    private static final int NORTH_EAST = 1;
    private static final int EAST = 2;
    private static final int SOUTH_EAST = 3;
    private static final int SOUTH = 4;
    private static final int SOUTH_WEST = 5;
    private static final int WEST = 6;
    private static final int NORTH_WEST = 7;
    private static final int NO_DIRECTION = 8;
    static final int NUM_DIRECTIONS = 9;

    private static final int DEFAULT_VEHICLE_TYPE = ObaRoute.TYPE_BUS;

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

    // Uncolored bitmap cache: one entry per (vehicleType, halfWind) pair. The
    // theoretical working set is VEHICLE_ICON_RES.length * NUM_DIRECTIONS (5 * 9 =
    // 45).
    // Anything smaller thrashes and runs BitmapFactory.decodeResource on the UI
    // thread
    // every time a vehicle changes direction.
    private static final int UNCOLORED_CACHE_SIZE = 64;

    // Colored descriptor cache: one entry per (vehicleType, halfWind,
    // colorResource) triple.
    // Working set is VEHICLE_ICON_RES.length * NUM_DIRECTIONS * 4 deviation colors
    // (delayed / early / ontime / scheduled) = 180. Headroom absorbs transient
    // churn
    // when the user switches between routes with different vehicle types.
    private static final int DESCRIPTOR_CACHE_SIZE = 256;

    private static LruCache<String, Bitmap> sUncoloredIcons;
    private static LruCache<String, BitmapDescriptor> sIconCache;

    private final Context mContext;

    public VehicleIconFactory(Context context) {
        mContext = context;
        ensureCachesInitialized();
    }

    private static synchronized void ensureCachesInitialized() {
        if (sUncoloredIcons == null) {
            sUncoloredIcons = new LruCache<>(UNCOLORED_CACHE_SIZE);
        }
        if (sIconCache == null) {
            sIconCache = new LruCache<>(DESCRIPTOR_CACHE_SIZE);
        }
    }

    /**
     * Returns the color resource for a vehicle's schedule deviation status.
     */
    public static int getDeviationColorResource(boolean isRealtime, ObaTripStatus status) {
        if (isRealtime) {
            long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
            return ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
        }
        return R.color.stop_info_scheduled_time;
    }

    /**
     * Returns the cached icon for the given icon parameters.
     */
    BitmapDescriptor getIcon(VehicleIconParams params) {
        int vehicleType = params.vehicleType;
        int colorResource = params.colorResource;
        int halfWind = params.halfWind;
        if (vehicleType == ObaRoute.TYPE_CABLECAR) {
            vehicleType = ObaRoute.TYPE_TRAM;
        }

        String key = vehicleType + " " + halfWind + " " + colorResource;
        BitmapDescriptor icon = sIconCache.get(key);
        if (icon == null) {
            int color = ContextCompat.getColor(mContext, colorResource);
            Bitmap b = UIUtils.colorBitmap(getUncoloredIcon(halfWind, vehicleType), color);
            icon = BitmapDescriptorFactory.fromBitmap(b);
            sIconCache.put(key, icon);
        }
        return icon;
    }

    private static Bitmap getUncoloredIcon(int halfWind, int vehicleType) {
        if (!supportedVehicleType(vehicleType)) {
            vehicleType = DEFAULT_VEHICLE_TYPE;
        }

        String cacheKey = halfWind + " " + vehicleType;
        Bitmap b = sUncoloredIcons.get(cacheKey);
        if (b == null) {
            int[] res = VEHICLE_ICON_RES[vehicleType];
            int idx = (halfWind >= 0 && halfWind < res.length - 1) ? halfWind : res.length - 1;
            b = BitmapFactory.decodeResource(Application.get().getResources(), res[idx]);
            sUncoloredIcons.put(cacheKey, b);
        }
        return b;
    }

    private static boolean supportedVehicleType(int vehicleType) {
        return vehicleType >= 0 && vehicleType < VEHICLE_ICON_RES.length;
    }

}
