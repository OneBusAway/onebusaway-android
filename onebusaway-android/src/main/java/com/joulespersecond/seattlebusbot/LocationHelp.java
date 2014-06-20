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
package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.region.RegionUtils;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import java.util.Iterator;
import java.util.List;

/**
 * Utilities to help obtain and process location data
 *
 * @author barbeau
 */
public class LocationHelp {

    public static final int DEFAULT_SEARCH_RADIUS = 15000;

    public static Location getDefaultSearchCenter() {
        ObaRegion region = Application.get().getCurrentRegion();
        if (region != null) {
            double results[] = new double[4];
            RegionUtils.getRegionSpan(region, results);
            return LocationHelp.makeLocation(results[2], results[3]);
        } else {
            return null;
        }
    }

    // We need to provide the API for a location used to disambiguate
    // stop IDs in case of collision, or to provide multiple results
    // in the case multiple agencies. But we really don't need it to be very
    // accurate.
    public static Location getLocation(Context cxt) {
        Location last = getLocation2(cxt);
        if (last != null) {
            return last;
        } else {
            return getDefaultSearchCenter();
        }
    }

    public static Location getLocation2(Context cxt) {
        LocationManager mgr = (LocationManager) cxt.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mgr.getProviders(true);
        Location last = null;
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            Location loc = mgr.getLastKnownLocation(i.next());
            // If this provider has a last location, and either:
            // 1. We don't have a last location,
            // 2. Our last location is older than this location.
            if (loc != null && (last == null || loc.getTime() > last.getTime())) {
                last = loc;
            }
        }
        return last;
    }

    /**
     * Converts a latitude/longitude to a Location.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A Location representing this latitude/longitude.
     */
    public static final Location makeLocation(double lat, double lon) {
        Location l = new Location("");
        l.setLatitude(lat);
        l.setLongitude(lon);
        return l;
    }
}
