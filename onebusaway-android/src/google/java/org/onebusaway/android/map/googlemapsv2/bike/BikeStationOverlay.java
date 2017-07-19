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
import android.graphics.drawable.Drawable;
import android.os.Build;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to hold bike stations and control their display on the map.
 */
public class BikeStationOverlay
        implements MarkerListeners, BikeInfoWindow.BikeStationsInfo {

    private GoogleMap mMap;

    private BikeStationData mBikeStationData;

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
            public int getLabelBackgroundDrawableId() {
                return R.drawable.speed_dial_bikeshare_item_label;
            }

            @Override
            public int getIconDrawableId() {
                return R.drawable.ic_directions_bike_white;
            }


            @Override
            public int getLayerColor() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return Application.get().getColor(R.color.layer_bikeshare_color);
                } else {
                    //noinspection deprecation
                    return Application.get().getResources().getColor(R.color.layer_bikeshare_color);
                }
            }

            @Override
            public String getSharedPreferenceKey() {
                return Application.get().getString(R.string.preference_key_layer_bikeshare_visible);
            }
        };

    public BikeStationOverlay(Activity activity, GoogleMap map) {
        context = activity;
        mMap = map;
        mBikeStationData = new BikeStationData();
        mMap.setInfoWindowAdapter(new BikeInfoWindow(activity, this));

        mSmallBikeStationIcon = BitmapDescriptorFactory.fromBitmap(createBitmapFromShape());
        mBigBikeStationIcon = BitmapDescriptorFactory.fromResource(R.drawable.bike_station_marker_big);
        mBigFloatingBikeIcon = BitmapDescriptorFactory.fromResource(R.drawable.bike_floating_marker_big);
    }

    public void setOnFocusChangeListener(BaseMapFragment.OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }


    /**
     * Add the bike stations to the map keeping the currently selected marker.
     * @param bikeStations list of bikeStations to display on the map
     */
    public void addBikeStations(List<BikeRentalStation> bikeStations) {
        // bike station associated with the selected marker (if any)
        BikeRentalStation selectedBikeStation = getBikeStationForSelectedMarker();
        mBikeStationData.addBikeStations(bikeStations);
        // show the info window again if a marker was previously selected
        if (selectedBikeStation != null) {
            // Add the selected marker to the map. Since the method to add the markers has already
            // been called, there is already a marker in the position. But addMarker will replace
            // it with a new one and it its info window needs to be displayed and also added as
            // selected in the bikeStationData.
            Marker selectedMarker = mBikeStationData.addMarker(selectedBikeStation);
            mBikeStationData.updateMarkerView(selectedMarker, selectedBikeStation);
            if (selectedMarker != null) {
                if (selectedMarker.isVisible()) {
                    selectedMarker.showInfoWindow();
                }
                mBikeStationData.selectMaker(selectedMarker);
            }
        }
    }

    /**
     *
     * @return the bike station associated with the selected bike marker if a marker is selected
     */
    private BikeRentalStation getBikeStationForSelectedMarker() {
        Marker selectedMarker = mBikeStationData.getSelectedMarker();
        if (selectedMarker != null) {
            return mBikeStationData.getBikeStationOnMarker(selectedMarker);
        } else {
            return null;
        }
    }

    public void clearBikeStations() {
        mBikeStationData.clearBikeStationMarkers();
    }

    @Override
    public boolean markerClicked(Marker marker) {

        if (mBikeStationData.containsMaker(marker)) {
            BikeRentalStation bikeRentalStation = mBikeStationData.getBikeStationOnMarker(marker);
            if (mOnFocusChangedListener != null) {
                marker.showInfoWindow();
                mOnFocusChangedListener.onFocusChanged(bikeRentalStation);
            }

            mBikeStationData.selectMaker(marker);

            ObaAnalytics.reportEventWithCategory(
                    ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    context.getString(R.string.analytics_action_button_press),
                    context.getString(bikeRentalStation.isFloatingBike?
                            R.string.analytics_label_bike_station_marker_clicked:
                            R.string.analytics_label_floating_bike_marker_clicked));

            return true;
        } else {
            mBikeStationData.removeMarkerSelection();
        }
        return false;
    }

    @Override
    public void removeMarkerClicked(LatLng latLng) {
        mOnFocusChangedListener.onFocusChanged(null);
        mBikeStationData.removeMarkerSelection();
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
        return mBikeStationData.getBikeStationOnMarker(marker);
    }

    class BikeStationData {

        /*
        Store the current map zoom level to detect zoom level band changes. The bands are used to
        show bike markers in different formats. Currently there are three bands:
        . <= 12
        . 12 to 15
        . > 15
         */
        private float mCurrentMapZoomLevel = 0;

        // Limit of bike markers to keep on memory to avoid markers to flick on screen
        private static final int FUZZY_MAX_MARKER_COUNT = 200;

        // Store the selected marker in order to continue displaying the info window when markers
        // are added/ removed from map
        private Marker mSelectedMarker = null;

        // Keep track of markers displayed on map and associated BikeRentalStation
        private HashMap<Marker, BikeRentalStation> mMarkers;

        // Keep track of existing bike stations displayed on the map. This is used to verify if a
        // bike station is already on the map
        private List<String> mBikeStationKeys;

        public BikeStationData() {
            mMarkers = new HashMap<>();
            mBikeStationKeys = new ArrayList<>();
        }

        public synchronized void addBikeStations(List<BikeRentalStation> bikeStations) {
            // clear cache of markers if maximum number has been reached
            if (mMarkers.size() > FUZZY_MAX_MARKER_COUNT) {
                clearBikeStationMarkers();
            }
            if (hasZoomLevelChangedBands()) {
                // Update existing markers according to new zoom band and bike station type
                for(Map.Entry<Marker, BikeRentalStation> entry: mMarkers.entrySet()) {
                    updateMarkerView(entry.getKey(), entry.getValue());
                }
            }
            //Add markers for the bike stations that are not already visible on the map
            for (BikeRentalStation bikeStation : bikeStations) {
                if (!mBikeStationKeys.contains(bikeStation.id)) {
                    Marker marker = addMarker(bikeStation);
                    updateMarkerView(marker, bikeStation);
                }
            }
            // Store the new zoom level in order to detect when the zoom level bands change
            mCurrentMapZoomLevel = mMap.getCameraPosition().zoom;
        }

        // detect map zoom level changes between bands <= 12 | 12 - 15 | > 15
        private boolean hasZoomLevelChangedBands() {
            return (mCurrentMapZoomLevel <= 12 && mMap.getCameraPosition().zoom > 12) ||
                    (mCurrentMapZoomLevel > 15 && mMap.getCameraPosition().zoom <= 15) ||
                    (mCurrentMapZoomLevel > 12 && mCurrentMapZoomLevel <= 15 &&
                            (mMap.getCameraPosition().zoom <= 12 || mMap.getCameraPosition().zoom > 15));
        }

        /**
         * Add a marker on the map for a bike staton. The default marker is added. The method
         * updateMarkerView needs to be called to update it's appearance.
         * @param bikeStation bike station to be added to the map
         * @return
         */
        private synchronized Marker addMarker(BikeRentalStation bikeStation) {
            MarkerOptions options = new MarkerOptions().position(MapHelpV2.makeLatLng(bikeStation.y, bikeStation.x));
            Marker m = mMap.addMarker(options);
            mMarkers.put(m, bikeStation);
            mBikeStationKeys.add(bikeStation.id);
            return m;
        }

        /**
         * Change marker appearance according to the zoom level and type of bike station is represents
         * @param marker
         */
        private synchronized void updateMarkerView(Marker marker, BikeRentalStation station) {
            if (mMap.getCameraPosition().zoom > 12) {
                marker.setVisible(true);
                if (mMap.getCameraPosition().zoom > 15) {
                    if (station.isFloatingBike) {
                        marker.setIcon(mBigFloatingBikeIcon);
                    } else {
                        marker.setIcon(mBigBikeStationIcon);
                    }
                } else {
                    marker.setIcon(mSmallBikeStationIcon);
                }
            } else {
                marker.setVisible(false);
            }
        }

        /**
         * Remove all bike markers from map and clear the list of markers in memory.
         */
        private synchronized void clearBikeStationMarkers() {
            for (Marker marker : mMarkers.keySet()) {
                marker.remove();
            }
            mMarkers.clear();
            mBikeStationKeys.clear();
        }

        public BikeRentalStation getBikeStationOnMarker(Marker marker) {
            return mMarkers.get(marker);
        }

        public boolean containsMaker(Marker marker) {
            return mMarkers.containsKey(marker);
        }

        public void selectMaker(Marker marker) {
            mSelectedMarker = marker;
        }

        public void removeMarkerSelection() {
            mSelectedMarker = null;
        }

        public Marker getSelectedMarker() {
            return mSelectedMarker;
        }
    }
}
