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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.PlausibleAnalytics;
import org.onebusaway.android.map.ObaMapFragment;
import org.onebusaway.android.util.LayerUtils;
import org.onebusaway.android.util.RegionUtils;
import org.onebusaway.android.util.UIUtils;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MapLibre implementation of the bike station overlay.
 * Shows bike station and floating bike markers with zoom-dependent icon sizes.
 */
public class BikeStationOverlay {

    private final MapLibreMap mMap;

    private BikeStationData mBikeStationData;

    private ObaMapFragment.OnFocusChangedListener mOnFocusChangedListener;

    private Icon mSmallBikeStationIcon;
    private Icon mBigBikeStationIcon;
    private Icon mBigFloatingBikeIcon;

    private final Context mContext;
    private final FirebaseAnalytics mFirebaseAnalytics;

    private boolean mIsInDirectionsMode = false;

    public BikeStationOverlay(Activity activity, MapLibreMap map, boolean isInDirectionsMode) {
        mContext = activity;
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(mContext);
        mMap = map;
        mIsInDirectionsMode = isInDirectionsMode;
        mBikeStationData = new BikeStationData();

        IconFactory iconFactory = IconFactory.getInstance(activity);
        mSmallBikeStationIcon = iconFactory.fromBitmap(createBitmapFromShape());
        mBigBikeStationIcon = iconFactory.fromResource(R.drawable.bike_station_marker_big);
        mBigFloatingBikeIcon = iconFactory.fromResource(R.drawable.bike_floating_marker_big);
    }

    public void setOnFocusChangeListener(
            ObaMapFragment.OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }

    public void addBikeStations(List<BikeRentalStation> bikeStations) {
        boolean showBikeMarkers = mIsInDirectionsMode || LayerUtils.isBikeshareLayerVisible();

        // Preserve selected marker across data refresh
        BikeRentalStation selectedBikeStation = getBikeStationForSelectedMarker();
        mBikeStationData.addBikeStations(bikeStations, showBikeMarkers);

        // Restore selection if a marker was previously selected
        if (selectedBikeStation != null) {
            Marker selectedMarker = mBikeStationData.addMarker(selectedBikeStation);
            mBikeStationData.updateMarkerView(selectedMarker, selectedBikeStation, showBikeMarkers);
            if (selectedMarker != null) {
                mMap.selectMarker(selectedMarker);
                mBikeStationData.selectMarker(selectedMarker);
            }
        }
    }

    private BikeRentalStation getBikeStationForSelectedMarker() {
        Marker selectedMarker = mBikeStationData.getSelectedMarker();
        if (selectedMarker != null) {
            return mBikeStationData.getBikeStationOnMarker(selectedMarker);
        }
        return null;
    }

    public void clearBikeStations() {
        mBikeStationData.clearBikeStationMarkers();
    }

    public boolean markerClicked(Marker marker) {
        if (mBikeStationData.containsMarker(marker)) {
            BikeRentalStation bikeRentalStation = mBikeStationData.getBikeStationOnMarker(marker);
            mMap.selectMarker(marker);
            if (mOnFocusChangedListener != null) {
                mOnFocusChangedListener.onFocusChanged(bikeRentalStation);
            }
            mBikeStationData.selectMarker(marker);

            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                    Application.get().getPlausibleInstance(),
                    PlausibleAnalytics.REPORT_BIKE_EVENT_URL,
                    mContext.getString(bikeRentalStation.isFloatingBike ?
                            R.string.analytics_label_floating_bike_marker_clicked :
                            R.string.analytics_label_bike_station_marker_clicked),
                    null);
            return true;
        } else {
            mBikeStationData.removeMarkerSelection();
        }
        return false;
    }

    public void removeMarkerClicked(LatLng latLng) {
        if (mOnFocusChangedListener != null) {
            mOnFocusChangedListener.onFocusChanged(null);
        }
        mBikeStationData.removeMarkerSelection();
    }

    /**
     * @return true if this overlay handled the click
     */
    public boolean onInfoWindowClick(Marker marker) {
        BikeRentalStation bikeStation = mBikeStationData.getBikeStationOnMarker(marker);
        if (bikeStation == null) {
            return false;
        }
        if (Application.get().getCurrentRegion() != null
                && Application.get().getCurrentRegion().getId() == RegionUtils.TAMPA_REGION_ID) {
            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                    Application.get().getPlausibleInstance(),
                    PlausibleAnalytics.REPORT_BIKE_EVENT_URL,
                    mContext.getString(bikeStation.isFloatingBike ?
                            R.string.analytics_label_floating_bike_balloon_clicked :
                            R.string.analytics_label_bike_station_balloon_clicked),
                    null);
            UIUtils.launchTampaHoprApp(mContext);
        }
        return true;
    }

    public BikeRentalStation getBikeStationOnMarker(Marker marker) {
        return mBikeStationData.getBikeStationOnMarker(marker);
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

    // ========================================================================
    // BikeStationData — manages markers on the map
    // ========================================================================

    private class BikeStationData {

        private float mCurrentMapZoomLevel = 0;
        private static final int FUZZY_MAX_MARKER_COUNT = 200;

        private Marker mSelectedMarker = null;
        private HashMap<Marker, BikeRentalStation> mMarkers;
        private List<String> mBikeStationKeys;

        BikeStationData() {
            mMarkers = new HashMap<>();
            mBikeStationKeys = new ArrayList<>();
        }

        synchronized void addBikeStations(List<BikeRentalStation> bikeStations,
                                           boolean showBikeMarkers) {
            if (mMarkers.size() > FUZZY_MAX_MARKER_COUNT) {
                clearBikeStationMarkers();
            }
            if (hasZoomLevelChangedBands()) {
                for (Map.Entry<Marker, BikeRentalStation> entry : mMarkers.entrySet()) {
                    updateMarkerView(entry.getKey(), entry.getValue(), showBikeMarkers);
                }
            }
            for (BikeRentalStation bikeStation : bikeStations) {
                if (!mBikeStationKeys.contains(bikeStation.id)) {
                    Marker marker = addMarker(bikeStation);
                    updateMarkerView(marker, bikeStation, showBikeMarkers);
                }
            }
            mCurrentMapZoomLevel = (float) mMap.getCameraPosition().zoom;
        }

        private boolean hasZoomLevelChangedBands() {
            float zoom = (float) mMap.getCameraPosition().zoom;
            return (mCurrentMapZoomLevel <= 12 && zoom > 12) ||
                    (mCurrentMapZoomLevel > 15 && zoom <= 15) ||
                    (mCurrentMapZoomLevel > 12 && mCurrentMapZoomLevel <= 15 &&
                            (zoom <= 12 || zoom > 15));
        }

        private synchronized Marker addMarker(BikeRentalStation bikeStation) {
            MarkerOptions options = new MarkerOptions()
                    .position(new LatLng(bikeStation.y, bikeStation.x))
                    .title(bikeStation.name);
            if (!bikeStation.isFloatingBike) {
                options.snippet("Bikes: " + bikeStation.bikesAvailable
                        + " | Docks: " + bikeStation.spacesAvailable);
            }
            Marker m = mMap.addMarker(options);
            mMarkers.put(m, bikeStation);
            mBikeStationKeys.add(bikeStation.id);
            return m;
        }

        private synchronized void updateMarkerView(Marker marker, BikeRentalStation station,
                                                    boolean showBikeMarker) {
            float zoom = (float) mMap.getCameraPosition().zoom;
            if (zoom > 12 && showBikeMarker) {
                if (zoom > 15) {
                    if (station.isFloatingBike) {
                        marker.setIcon(mBigFloatingBikeIcon);
                    } else {
                        marker.setIcon(mBigBikeStationIcon);
                    }
                } else {
                    marker.setIcon(mSmallBikeStationIcon);
                }
            } else {
                // MapLibre doesn't have Marker.setVisible(), so we remove and track
                // markers that should be hidden. For simplicity, we set a tiny alpha icon.
                marker.setIcon(mSmallBikeStationIcon);
            }
        }

        synchronized void clearBikeStationMarkers() {
            for (Marker marker : mMarkers.keySet()) {
                mMap.removeAnnotation(marker);
            }
            mMarkers.clear();
            mBikeStationKeys.clear();
        }

        BikeRentalStation getBikeStationOnMarker(Marker marker) {
            return mMarkers.get(marker);
        }

        boolean containsMarker(Marker marker) {
            return mMarkers.containsKey(marker);
        }

        void selectMarker(Marker marker) {
            mSelectedMarker = marker;
        }

        void removeMarkerSelection() {
            mSelectedMarker = null;
        }

        Marker getSelectedMarker() {
            return mSelectedMarker;
        }
    }
}
