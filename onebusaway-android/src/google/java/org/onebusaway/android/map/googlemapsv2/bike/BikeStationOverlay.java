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
package org.onebusaway.android.map.googlemapsv2.bike;

import android.app.Activity;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.onebusaway.android.R;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.map.googlemapsv2.MapHelpV2;
import org.onebusaway.android.map.googlemapsv2.MarkerListeners;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import java.util.HashMap;
import java.util.List;

/**
 * Class to hold bike station markers.
 *
 * @author carvalhorr
 */
public class BikeStationOverlay implements MarkerListeners {

    private GoogleMap mMap;

    private static final int FUZZY_MAX_MARKER_COUNT = 200;

    private HashMap<Marker, BikeRentalStation> mStations;

    private BaseMapFragment.OnFocusChangedListener mOnFocusChangedListener;

    public BikeStationOverlay(Activity a, GoogleMap map) {
        mMap = map;
        mStations = new HashMap<>();
        mMap.setInfoWindowAdapter(new BikeInfoWindow(a));
    }

    private synchronized void addMarker(BikeRentalStation station) {
        MarkerOptions options = new MarkerOptions().position(MapHelpV2.makeLatLng(station.y, station.x));
        if (mMap.getCameraPosition().zoom > 13) {
            options.icon(BitmapDescriptorFactory.fromResource(R.drawable.bike_marker_big));
        } else {
            options.icon(BitmapDescriptorFactory.fromResource(R.drawable.bike_circle_icon));
        }
        Marker m = mMap.addMarker(options);

        m.setTag(station);
        mStations.put(m, station);
    }

    public void setOnFocusChangeListener(BaseMapFragment.OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }


    public void addBikeStations(List<BikeRentalStation> bikeStations) {
        /*if (mStations.size() >= FUZZY_MAX_MARKER_COUNT) {
            clearBikeStations();
        }*/
        clearBikeStations();
        for (BikeRentalStation bikeStation: bikeStations) {
            addMarker(bikeStation);
        }
    }

    public void clearBikeStations() {
        for (Marker marker: mStations.keySet()) {
            marker.remove();
        }
        mStations.clear();
    }

    @Override
    public boolean markerClicked(Marker marker) {

        if (marker.getTag() != null) {
            if (mOnFocusChangedListener != null) {
                BikeRentalStation bikeRentalStation = (BikeRentalStation) marker.getTag();
                marker.showInfoWindow();
                mOnFocusChangedListener.onFocusChanged(bikeRentalStation);
            }
            return true;
        }
        return false;
    }

    @Override
    public void removeMarkerClicked(LatLng latLng) {
        Log.d("", "remove selected bike station");
        mOnFocusChangedListener.onFocusChanged(null);
    }
}
