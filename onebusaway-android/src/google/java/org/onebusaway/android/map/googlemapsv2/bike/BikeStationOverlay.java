/*
* Copyright (C) Sean J. Barbeau (sjbarbeau@gmail.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.map.googlemapsv2.LayerInfo;
import org.onebusaway.android.map.googlemapsv2.MapHelpV2;
import org.onebusaway.android.map.googlemapsv2.MarkerListeners;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import java.util.HashMap;
import java.util.List;

/**
 * Class to hold bike stations and control their display on the map.
 */
public class BikeStationOverlay
        implements MarkerListeners, BikeInfoWindow.BikeStationsInfo {

    private GoogleMap mMap;

    private HashMap<Marker, BikeRentalStation> mStations;

    private BaseMapFragment.OnFocusChangedListener mOnFocusChangedListener;

    private BitmapDescriptor mSmallBikeStationIcon;
    private BitmapDescriptor mBigBikeStationIcon;
    private BitmapDescriptor mBigFloatingBikeIcon;

    private Context context;

    /**
     * Information necessary to create Speed Dial menu on the Layers FAB.
     * @return
     */
    public static final LayerInfo layerInfo = new LayerInfo() {
            @Override
            public String getLayerlabel() {
                return Application.get().getString(R.string.layers_speedial_bikeshare_label);
            }

            @Override
            public int getIconDrawableId() {
                return R.drawable.ic_directions_bike_white;
            }

            @Override
            public int getLayerColor() {
                return Color.parseColor("#3a4677");
            }

            @Override
            public String getSharedPreferenceKey() {
                return Application.get().getString(R.string.preference_key_layer_bikeshare_visible);
            }
        };

    public BikeStationOverlay(Activity activity, GoogleMap map) {
        context = activity;
        mMap = map;
        mStations = new HashMap<>();
        mMap.setInfoWindowAdapter(new BikeInfoWindow(activity, this));

        mSmallBikeStationIcon = BitmapDescriptorFactory.fromBitmap(createBitmapFromShape());
        mBigBikeStationIcon = BitmapDescriptorFactory.fromResource(R.drawable.bike_station_marker_big);
        mBigFloatingBikeIcon = BitmapDescriptorFactory.fromResource(R.drawable.bike_floating_marker_big);
    }

    private synchronized void addMarker(BikeRentalStation station) {
        if (mMap.getCameraPosition().zoom > 12) {
            MarkerOptions options = new MarkerOptions().position(MapHelpV2.makeLatLng(station.y, station.x));
            if (mMap.getCameraPosition().zoom > 15) {
                if (station.isFloatingBike) {
                    options.icon(mBigFloatingBikeIcon);
                } else {
                    options.icon(mBigBikeStationIcon);
                }
            } else {
                options.icon(mSmallBikeStationIcon);
            }
            Marker m = mMap.addMarker(options);
            mStations.put(m, station);
        }
    }

    public void setOnFocusChangeListener(BaseMapFragment.OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }


    public void addBikeStations(List<BikeRentalStation> bikeStations) {
        clearBikeStations();
        for (BikeRentalStation bikeStation : bikeStations) {
            addMarker(bikeStation);
        }
    }

    public void clearBikeStations() {
        for (Marker marker : mStations.keySet()) {
            marker.remove();
        }
        mStations.clear();
    }

    @Override
    public boolean markerClicked(Marker marker) {

        if (mStations.containsKey(marker)) {
            if (mOnFocusChangedListener != null) {
                BikeRentalStation bikeRentalStation = mStations.get(marker);
                marker.showInfoWindow();
                mOnFocusChangedListener.onFocusChanged(bikeRentalStation);
            }

            BikeRentalStation bikeStation = mStations.get(marker);

            ObaAnalytics.reportEventWithCategory(
                    ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    context.getString(R.string.analytics_action_button_press),
                    context.getString(bikeStation.isFloatingBike?
                            R.string.analytics_label_bike_station_marker_clicked:
                            R.string.analytics_label_floating_bike_marker_clicked));

            return true;
        }
        return false;
    }

    @Override
    public void removeMarkerClicked(LatLng latLng) {
        mOnFocusChangedListener.onFocusChanged(null);
    }

    private Bitmap createBitmapFromShape() {
        int px = Application.get().getResources().getDimensionPixelSize(R.dimen.map_stop_shadow_size_6);

        Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);

        Canvas c = new Canvas(bitmap);
        Drawable shape = ContextCompat.getDrawable(Application.get(), R.drawable.bike_marker_small);
        shape.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());

        shape.draw(c);

        return bitmap;
    }

    @Override
    public BikeRentalStation getBikeStationOnMarker(Marker marker) {
        return mStations.get(marker);
    }
}
