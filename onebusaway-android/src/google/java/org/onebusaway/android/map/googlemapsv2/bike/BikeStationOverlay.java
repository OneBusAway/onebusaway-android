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
import org.onebusaway.android.map.googlemapsv2.MapHelpV2;
import org.onebusaway.android.map.googlemapsv2.MarkerListeners;
import org.onebusaway.android.util.LayerUtils;
import org.onebusaway.android.util.RegionUtils;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to hold bike stations and control their display on the map.
 */
public class BikeStationOverlay
        implements MarkerListeners,
        BikeInfoWindowAdapter.BikeStationsInfo,
        GoogleMap.OnInfoWindowClickListener {

    private GoogleMap mMap;

    private BikeStationData mBikeStationData;

    private BaseMapFragment.OnFocusChangedListener mOnFocusChangedListener;

    private BitmapDescriptor mSmallBikeStationIcon;

    private BitmapDescriptor mBigBikeStationIcon;

    private BitmapDescriptor mBigFloatingBikeIcon;

    private Context mContext;

    private BikeInfoWindowAdapter mBikeInfoWindowAdapter = null;

    /**
     * Indicates if the map is in DIRECTIONS_MODE. When in directions mode, the bike markers
     * should be displayed regardless of the bikeshare layer being active or not in the main map.
     */
    private boolean mIsInDirectionsMode = false;

    public BikeStationOverlay(Activity activity, GoogleMap map, boolean isInDirectionsMode) {
        mContext = activity;
        mMap = map;
        mIsInDirectionsMode = isInDirectionsMode;
        mBikeStationData = new BikeStationData();
        mBikeInfoWindowAdapter = new BikeInfoWindowAdapter(activity, this);
        setupInfoWindow();

        mSmallBikeStationIcon = BitmapDescriptorFactory.fromBitmap(createBitmapFromShape());
        mBigBikeStationIcon = BitmapDescriptorFactory
                .fromResource(R.drawable.bike_station_marker_big);
        mBigFloatingBikeIcon = BitmapDescriptorFactory
                .fromResource(R.drawable.bike_floating_marker_big);
    }

    private void setupInfoWindow() {
        mMap.setInfoWindowAdapter(mBikeInfoWindowAdapter);
        mMap.setOnInfoWindowClickListener(this);
    }

    public void setOnFocusChangeListener(
            BaseMapFragment.OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }


    /**
     * Add the bike stations to the map keeping the currently selected marker.
     *
     * @param bikeStations list of bikeStations to display on the map
     */
    public void addBikeStations(List<BikeRentalStation> bikeStations) {
        mBikeInfoWindowAdapter = new BikeInfoWindowAdapter(mContext, this);

        // bike station associated with the selected marker (if any)
        BikeRentalStation selectedBikeStation = getBikeStationForSelectedMarker();
        mBikeStationData.addBikeStations(bikeStations);
        // show the info window again if a marker was previously selected
        if (selectedBikeStation != null) {
            /**
             * Add the selected marker to the map. Since the method to add the markers has already
             * been called, there is already a marker in the position. But addMarker will replace
             * it with a new one and it its info window needs to be displayed and also added as
             * selected in the bikeStationData.
             */
            Marker selectedMarker = mBikeStationData.addMarker(selectedBikeStation);
            mBikeStationData.updateMarkerView(selectedMarker, selectedBikeStation,
                    LayerUtils.isBikeshareLayerVisible());
            if (selectedMarker != null) {
                if (selectedMarker.isVisible()) {
                    selectedMarker.showInfoWindow();
                }
                mBikeStationData.selectMaker(selectedMarker);
            }
        }
    }

    /**
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
            // Set the info window adapter before showing the info window as it may have changed by
            // another overlay.
            setupInfoWindow();
            BikeRentalStation bikeRentalStation = mBikeStationData.getBikeStationOnMarker(marker);
            marker.showInfoWindow();
            if (mOnFocusChangedListener != null) {
                mOnFocusChangedListener.onFocusChanged(bikeRentalStation);
            }

            mBikeStationData.selectMaker(marker);

            ObaAnalytics.reportEventWithCategory(
                    ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    mContext.getString(R.string.analytics_action_button_press),
                    mContext.getString(bikeRentalStation.isFloatingBike ?
                            R.string.analytics_label_bike_station_marker_clicked :
                            R.string.analytics_label_floating_bike_marker_clicked));
            return true;
        } else {
            mBikeStationData.removeMarkerSelection();
        }
        return false;
    }

    @Override
    public void removeMarkerClicked(LatLng latLng) {
        if (mOnFocusChangedListener != null) {
            mOnFocusChangedListener.onFocusChanged(null);
        }
        mBikeStationData.removeMarkerSelection();
    }

    private Bitmap createBitmapFromShape() {
        int px = Application.get().getResources()
                .getDimensionPixelSize(R.dimen.bikeshare_small_marker_size);

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

    @Override
    public void onInfoWindowClick(Marker marker) {
        // Proof of concept for deep-linking integration hard-coded for Tampa region (id = 0)
        if (Application.get().getCurrentRegion() != null
                && Application.get().getCurrentRegion().getId() == RegionUtils.TAMPA_REGION_ID) {
            BikeRentalStation bikeStation = mBikeStationData.getBikeStationOnMarker(marker);
            if (bikeStation != null) {
                String url;

                // Trim SoBi IDs - See https://github.com/OneBusAway/onebusaway-android/issues/402#issuecomment-321369719
                String bikeStationId = bikeStation.id.replace("bike_", "").replace("hub_", "")
                        .replace("\"", "");

                if (bikeStation.isFloatingBike) {
                    url = mContext.getString(R.string.sobi_deep_link_floating_bike_url)
                            + bikeStationId;
                } else {
                    url = mContext.getString(R.string.sobi_deep_link_bike_station_url)
                            + bikeStationId;
                }

                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        mContext.getString(R.string.analytics_action_button_press),
                        mContext.getString(bikeStation.isFloatingBike ?
                                R.string.analytics_label_bike_station_balloon_clicked :
                                R.string.analytics_label_floating_bike_balloon_clicked));

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                mContext.startActivity(i);
            }
        }
    }

    private class BikeStationData {

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
            // Clear cache of markers if maximum number has been reached
            if (mMarkers.size() > FUZZY_MAX_MARKER_COUNT) {
                clearBikeStationMarkers();
            }
            boolean showBikeMarkers = mIsInDirectionsMode || LayerUtils.isBikeshareLayerVisible();
            if (hasZoomLevelChangedBands()) {
                // Update existing markers according to new zoom band and bike station type
                for (Map.Entry<Marker, BikeRentalStation> entry : mMarkers.entrySet()) {
                    updateMarkerView(entry.getKey(), entry.getValue(), showBikeMarkers);
                }
            }
            // Add markers for the bike stations that are not already visible on the map
            for (BikeRentalStation bikeStation : bikeStations) {
                if (!mBikeStationKeys.contains(bikeStation.id)) {
                    Marker marker = addMarker(bikeStation);
                    updateMarkerView(marker, bikeStation, showBikeMarkers);
                }
            }
            // Store the new zoom level in order to detect when the zoom level bands change
            mCurrentMapZoomLevel = mMap.getCameraPosition().zoom;
        }

        // Detect map zoom level changes between bands <= 12 | 12 - 15 | > 15
        private boolean hasZoomLevelChangedBands() {
            return (mCurrentMapZoomLevel <= 12 && mMap.getCameraPosition().zoom > 12) ||
                    (mCurrentMapZoomLevel > 15 && mMap.getCameraPosition().zoom <= 15) ||
                    (mCurrentMapZoomLevel > 12 && mCurrentMapZoomLevel <= 15 &&
                            (mMap.getCameraPosition().zoom <= 12
                                    || mMap.getCameraPosition().zoom > 15));
        }

        /**
         * Add a marker on the map for a bike staton. The default marker is added. The method
         * updateMarkerView needs to be called to update it's appearance.
         *
         * @param bikeStation bike station to be added to the map
         */
        private synchronized Marker addMarker(BikeRentalStation bikeStation) {
            MarkerOptions options = new MarkerOptions()
                    .position(MapHelpV2.makeLatLng(bikeStation.y, bikeStation.x));
            Marker m = mMap.addMarker(options);
            mMarkers.put(m, bikeStation);
            mBikeStationKeys.add(bikeStation.id);
            return m;
        }

        /**
         * Change marker appearance according to the zoom level and type of bike station is
         * represents
         *
         * @param marker         the marker to update its display
         * @param station        the bike station/floating bike associated with the marker
         * @param showBikeMarker used to control if the marker should be displayed or not. Included
         *                       to control bike markers display when the layer is deactivated
         *                       while the bike data was loading (it was deactivated between the
         *                       request has been sent to the server and the results have arrived)
         */
        private synchronized void updateMarkerView(Marker marker, BikeRentalStation station,
                boolean showBikeMarker) {
            if (mMap.getCameraPosition().zoom > 12 && showBikeMarker) {
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
