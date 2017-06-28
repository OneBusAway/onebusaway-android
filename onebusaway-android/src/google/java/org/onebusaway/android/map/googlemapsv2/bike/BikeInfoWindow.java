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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

import org.onebusaway.android.R;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

/**
 * InfoWindow displayed when a floating bike or a bike station is clicked on the map.
 *
 * Created by carvalhorr on 6/16/17.
 */
public class BikeInfoWindow implements GoogleMap.InfoWindowAdapter {

    private View bikeInfoWindowView;

    private BikeStationsInfo bikeStationsInfo;

    public BikeInfoWindow(Context content, BikeStationsInfo bikeStationsInfo) {
        bikeInfoWindowView = LayoutInflater.from(content).inflate(R.layout.bike_info_window, null);
        this.bikeStationsInfo = bikeStationsInfo;
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        BikeRentalStation station = bikeStationsInfo.getBikeStationOnMarker(marker);
        setBikeStationName(station.name);
        setNumberBikesAvailable(station.bikesAvailable);
        setNumberSpacesAvailable(station.spacesAvailable);
        return bikeInfoWindowView;

    }

    /**
     * Set the bike station name in the corresponding view.
     * @param name Name of the bike station
     */
    private void setBikeStationName(String name) {
        TextView stationName = (TextView) bikeInfoWindowView.findViewById(R.id.bikeStationName);
        stationName.setText(name);
    }

    /**
     * Set the  number of available bikes in the corresponding view
     * @param numberBikes number of bikes available
     */
    private void setNumberBikesAvailable(int numberBikes) {
        TextView bikesAvailable = (TextView) bikeInfoWindowView.findViewById(R.id.numberBikes);
        bikesAvailable.setText(String.valueOf(numberBikes));
    }

    /**
     * Set the number of spaces available to dropoff bikes
     * @param numberSpaces number of spaces available
     */
    private void setNumberSpacesAvailable(int numberSpaces) {
        TextView spacesAvailable = (TextView) bikeInfoWindowView.findViewById(R.id.numberRacks);
        spacesAvailable.setText(String.valueOf(numberSpaces));
    }

    /**
     * Interface to allow get the BikeRentalStation associated with a marker.
     */
    public interface BikeStationsInfo {
        BikeRentalStation getBikeStationOnMarker(Marker marker);
    }

}
