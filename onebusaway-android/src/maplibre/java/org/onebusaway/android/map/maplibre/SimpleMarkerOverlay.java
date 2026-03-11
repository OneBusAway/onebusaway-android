/*
 * Copyright (C) Sean J. Barbeau (sjbarbeau@gmail.com)
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

import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.maps.MapLibreMap;

import android.location.Location;
import android.util.SparseArray;

/**
 * Manages simple generic markers added to the map by classes external to this package.
 * Uses a SparseArray internally, suitable for small numbers of markers.
 */
public class SimpleMarkerOverlay {

    private final MapLibreMap mMap;

    private final SparseArray<Marker> mMarkers = new SparseArray<>();

    private int mMarkerId = 0;

    public SimpleMarkerOverlay(MapLibreMap map) {
        mMap = map;
    }

    /**
     * Adds a generic marker to the map and returns the ID associated with that marker.
     *
     * @param l   Location at which the marker should be added
     * @param hue unused for MapLibre (no built-in hue-based default markers); marker uses default icon
     * @return the ID associated with the marker that was just added
     */
    public synchronized int addMarker(Location l, Float hue) {
        MarkerOptions options = new MarkerOptions()
                .position(MapHelpMapLibre.makeLatLng(l));
        Marker m = mMap.addMarker(options);
        mMarkers.put(mMarkerId, m);
        int temp = mMarkerId;
        mMarkerId++;
        return temp;
    }

    /**
     * Removes the marker with the given ID from the map.
     */
    public void removeMarker(int markerId) {
        Marker m = mMarkers.get(markerId);
        if (m != null) {
            mMap.removeAnnotation(m);
            mMarkers.remove(markerId);
        }
    }
}
