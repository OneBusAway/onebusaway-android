/*
 * Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com)
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

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

import org.onebusaway.android.R;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/**
 * InfoWindow displayed when a floating bike or a bike station is clicked on the map.
 */
public class BikeInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

    private View bikeStationInfoWindowView;

    private View floatingBikeInfoWindowView;

    private BikeStationsInfo bikeStationsInfo;

    public BikeInfoWindowAdapter(Context content, BikeStationsInfo bikeStationsInfo) {
        bikeStationInfoWindowView = LayoutInflater.from(content)
                .inflate(R.layout.bike_station_info_window, null);
        floatingBikeInfoWindowView = LayoutInflater.from(content)
                .inflate(R.layout.floating_bike_info_window, null);
        this.bikeStationsInfo = bikeStationsInfo;
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        BikeRentalStation station = bikeStationsInfo.getBikeStationOnMarker(marker);
        if (station != null) {
            View returnView = null;
            if (station.isFloatingBike) {
                setBikeName(floatingBikeInfoWindowView, station.name);
                returnView = floatingBikeInfoWindowView;
            } else {
                setBikeName(bikeStationInfoWindowView, station.name);
                setNumberBikesAvailable(station.bikesAvailable);
                setNumberSpacesAvailable(station.spacesAvailable);
                returnView = bikeStationInfoWindowView;
            }
            return returnView;
        } else {
            return null;
        }
    }

    /**
     * Set the bike name in the corresponding view.
     *
     * @param name Name of the bike station/floating ike
     */
    private void setBikeName(View parentView, String name) {
        TextView stationName = (TextView) parentView.findViewById(R.id.bikeStationName);
        stationName.setText(name);
    }

    /**
     * Set the  number of available bikes in the corresponding view
     *
     * @param numberBikes number of bikes available
     */
    private void setNumberBikesAvailable(int numberBikes) {
        TextView bikesAvailable = (TextView) bikeStationInfoWindowView
                .findViewById(R.id.numberBikes);
        bikesAvailable.setText(String.valueOf(numberBikes));
    }

    /**
     * Set the number of spaces available to dropoff bikes
     *
     * @param numberSpaces number of spaces available
     */
    private void setNumberSpacesAvailable(int numberSpaces) {
        TextView spacesAvailable = (TextView) bikeStationInfoWindowView
                .findViewById(R.id.numberRacks);
        spacesAvailable.setText(String.valueOf(numberSpaces));
    }

    /**
     * Interface to allow get the BikeRentalStation associated with a marker.
     */
    public interface BikeStationsInfo {

        BikeRentalStation getBikeStationOnMarker(Marker marker);
    }

}
