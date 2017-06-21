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

    public BikeInfoWindow(Context content) {
        bikeInfoWindowView = LayoutInflater.from(content).inflate(R.layout.bike_info_window, null);
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        BikeRentalStation station = (BikeRentalStation) marker.getTag();
        setBikeStationName(station.name);
        setNumberBikesAvailable(station.bikesAvailable);
        setNumberSpacesAvailable(station.spacesAvailable);
        return bikeInfoWindowView;

    }

    private void setBikeStationName(String name) {
        TextView stationName = (TextView) bikeInfoWindowView.findViewById(R.id.bikeStationName);
        stationName.setText(name);
    }

    private void setNumberBikesAvailable(int numberBikes) {
        TextView bikesAvailable = (TextView) bikeInfoWindowView.findViewById(R.id.numberBikes);
        bikesAvailable.setText(String.valueOf(numberBikes));
    }

    private void setNumberSpacesAvailable(int numberSpaces) {
        TextView spacesAvailable = (TextView) bikeInfoWindowView.findViewById(R.id.numberRacks);
        spacesAvailable.setText(String.valueOf(numberSpaces));
    }

}
