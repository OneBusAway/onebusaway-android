/*
 * Copyright (C) 2014-2026 University of South Florida, Open Transit Software Foundation
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
package org.onebusaway.android.map.googlemapsv2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.ObaElementExtensionsKt;
import org.onebusaway.android.io.elements.OccupancyState;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.UIUtils;

import java.util.concurrent.TimeUnit;

/**
 * Custom info window adapter for vehicle markers on the map.
 * Shows route name, schedule deviation, last-updated time, and occupancy.
 */
class VehicleInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

    // Google Maps info windows always have a white background regardless of theme,
    // so force dark text to avoid white-on-white in dark mode
    private static final int PRIMARY_TEXT_COLOR = 0xDE000000; // 87% black
    private static final int SECONDARY_TEXT_COLOR = 0x8A000000; // 54% black

    interface InfoSource {
        ObaTripStatus getStatusFromMarker(Marker marker);

        boolean isDataReceivedMarker(Marker marker);

        boolean isExtrapolating(Marker marker);

        ObaTripsForRouteResponse getLastResponse();
    }

    private final LayoutInflater mInflater;
    private final Context mContext;
    private final InfoSource mSource;
    private final int mPaddingSides;
    private final int mPaddingTopBottom;

    VehicleInfoWindowAdapter(Context context, InfoSource source) {
        mInflater = LayoutInflater.from(context);
        mContext = context;
        mSource = source;
        mPaddingSides = UIUtils.dpToPixels(context, 5);
        mPaddingTopBottom = UIUtils.dpToPixels(context, 2);
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        if (mSource.isDataReceivedMarker(marker)) {
            return createDataReceivedInfoView(marker);
        }

        ObaTripStatus status = mSource.getStatusFromMarker(marker);
        if (status == null)
            return null;

        ObaTripsForRouteResponse response = mSource.getLastResponse();
        if (response == null)
            return null;

        ObaTrip trip = response.getTrip(status.getActiveTripId());
        if (trip == null)
            return null;
        ObaRoute route = response.getRoute(trip.getRouteId());
        if (route == null)
            return null;

        InfoWindowViews views = inflateAndBind();

        views.routeView.setText(UIUtils.getRouteDisplayName(route) + " " +
                mContext.getString(R.string.trip_info_separator) + " " +
                UIUtils.formatDisplayText(trip.getHeadsign()));

        long now = System.currentTimeMillis();
        boolean isRealtime = ObaElementExtensionsKt.isLocationRealtime(status);

        views.statusView.setBackgroundResource(R.drawable.round_corners_style_b_status);
        GradientDrawable d = (GradientDrawable) views.statusView.getBackground();

        if (!isRealtime) {
            Resources r = mContext.getResources();
            views.statusView.setText(r.getString(R.string.stop_info_scheduled));
            d.setColor(ContextCompat.getColor(mContext, R.color.stop_info_scheduled_time));
            views.statusView.setPadding(mPaddingSides, mPaddingTopBottom, mPaddingSides, mPaddingTopBottom);
            views.lastUpdatedView.setText(r.getString(R.string.vehicle_last_updated_scheduled));
            UIUtils.setOccupancyVisibilityAndColor(views.occupancyView, null, OccupancyState.HISTORICAL);
            UIUtils.setOccupancyContentDescription(views.occupancyView, null, OccupancyState.HISTORICAL);
            return views.root;
        }

        Resources r = mContext.getResources();
        int colorRes = VehicleIconFactory.getDeviationColorResource(isRealtime, status);
        long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
        views.statusView.setText(ArrivalInfoUtils.computeArrivalLabelFromDelay(r, deviationMin));
        d.setColor(ContextCompat.getColor(mContext, colorRes));
        views.statusView.setPadding(mPaddingSides, mPaddingTopBottom, mPaddingSides, mPaddingTopBottom);

        long elapsedSec = TimeUnit.MILLISECONDS.toSeconds(now - status.getLastUpdateTime());
        long elapsedMin = TimeUnit.SECONDS.toMinutes(elapsedSec);
        long secMod60 = elapsedSec % 60;

        boolean extrapolating = mSource.isExtrapolating(marker);
        String lastUpdated = elapsedSec < 60
                ? r.getString(extrapolating
                        ? R.string.vehicle_estimate_from_update_sec
                        : R.string.vehicle_last_updated_sec, elapsedSec)
                : r.getString(extrapolating
                        ? R.string.vehicle_estimate_from_update_min_and_sec
                        : R.string.vehicle_last_updated_min_and_sec, elapsedMin, secMod60);
        views.lastUpdatedView.setText(lastUpdated);

        UIUtils.setOccupancyVisibilityAndColor(views.occupancyView, status.getOccupancyStatus(),
                OccupancyState.REALTIME);
        UIUtils.setOccupancyContentDescription(views.occupancyView, status.getOccupancyStatus(),
                OccupancyState.REALTIME);

        return views.root;
    }

    private View createDataReceivedInfoView(Marker marker) {
        InfoWindowViews views = inflateAndBind();

        views.routeView.setText(marker.getTitle());
        views.statusView.setVisibility(View.GONE);

        Object tag = marker.getTag();
        if (tag instanceof VehicleMarkerState) {
            long fixTime = ((VehicleMarkerState) tag).dataReceivedFixTime;
            views.lastUpdatedView.setText(UIUtils.formatElapsedTime(
                    System.currentTimeMillis() - fixTime));
        }
        views.occupancyView.setVisibility(View.GONE);

        return views.root;
    }

    private InfoWindowViews inflateAndBind() {
        View view = mInflater.inflate(R.layout.vehicle_info_window, null);

        TextView routeView = view.findViewById(R.id.route_and_destination);
        routeView.setTextColor(PRIMARY_TEXT_COLOR);

        TextView lastUpdatedView = view.findViewById(R.id.last_updated);
        lastUpdatedView.setTextColor(SECONDARY_TEXT_COLOR);

        ImageView moreView = view.findViewById(R.id.trip_more_info);
        moreView.setColorFilter(ContextCompat.getColor(mContext, R.color.switch_thumb_normal_material_dark));

        return new InfoWindowViews(
                view,
                routeView,
                view.findViewById(R.id.status),
                lastUpdatedView,
                view.findViewById(R.id.occupancy));
    }

    private static class InfoWindowViews {
        final View root;
        final TextView routeView;
        final TextView statusView;
        final TextView lastUpdatedView;
        final ViewGroup occupancyView;

        InfoWindowViews(View root, TextView routeView, TextView statusView,
                TextView lastUpdatedView, ViewGroup occupancyView) {
            this.root = root;
            this.routeView = routeView;
            this.statusView = statusView;
            this.lastUpdatedView = lastUpdatedView;
            this.occupancyView = occupancyView;
        }
    }
}
