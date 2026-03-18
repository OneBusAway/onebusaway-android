/*
 * Copyright (C) 2024 Open Transit Software Foundation
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.extrapolation.math.SpeedDistribution;
import org.onebusaway.android.extrapolation.data.TripDataManager;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.ObaTripStatusExtensionsKt;
import org.onebusaway.android.util.UIUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Owns all trip-specific map rendering: trip polyline, stop dots, estimate
 * overlays, and data-received markers. Activated when a vehicle is selected
 * and deactivated when deselected.
 */
final class TripMapRenderer {

    // --- Constants ---

    public static final float TRIP_BASE_WIDTH_PX = 44f;
    private static final float STOP_STROKE_WIDTH = 4f;
    private static final int STOP_STROKE_COLOR = 0xFF242424;

    private static final float DATA_RECEIVED_Z_INDEX = 2f;
    private static final String DATA_RECEIVED_TITLE = "Most recent data";
    private static final int DATA_ICON_RADIUS_DP = 13;
    private static final int DATA_ICON_INNER_DP = 20;

    // --- Fields ---

    private final GoogleMap mMap;
    private final Context mContext;
    private final ChevronPolylineHelper mChevronHelper;

    private final ArrayList<Polyline> mTripPolylines = new ArrayList<>();
    private final ArrayList<Marker> mTripStopMarkers = new ArrayList<>();
    private final HashMap<Marker, StopInfo> mStopInfoMap = new HashMap<>();

    /** Data associated with each stop marker for click handling. */
    private static final class StopInfo {
        final String name;
        final long arrivalTimeSec;

        StopInfo(String name, long arrivalTimeSec) {
            this.name = name;
            this.arrivalTimeSec = arrivalTimeSec;
        }
    }

    private EstimateOverlayManager mEstimateOverlay;

    private Marker mDataReceivedIconMarker;
    private String mLastDataReceivedLabel;
    private long mLastDataReceivedUpdateTime;
    private BitmapDescriptor mCachedCircleIcon;

    private boolean mActive;
    private String mActiveTripId;
    private int mRouteColor;
    private long mScheduleDeviation;

    TripMapRenderer(GoogleMap map, Context context, ChevronPolylineHelper chevronHelper) {
        mMap = map;
        mContext = context;
        mChevronHelper = chevronHelper;
    }

    // --- Lifecycle ---

    void activate(String tripId, List<Location> shape, double[] cumDist,
                  ObaTripSchedule schedule, int routeColor, LatLng vehiclePosition,
                  Integer routeType, Map<String, String> stopNames,
                  long scheduleDeviation, String selectedStopId) {
        if (mActive) {
            deactivate();
        }
        mActive = true;
        mActiveTripId = tripId;
        mRouteColor = routeColor;
        mScheduleDeviation = scheduleDeviation;

        if (shape != null && mMap != null) {
            showTripPolyline(shape, routeColor);
        }
        showTripStopCircles(schedule, shape, cumDist, stopNames, selectedStopId);
        List<ObaTripStatus> history = TripDataManager.getInstance()
                .getHistory(tripId);
        showOrUpdateDataReceivedMarker(tripId, shape, cumDist, history);
        createEstimateOverlays(tripId, vehiclePosition, routeType);
    }

    void deactivate() {
        if (!mActive) return;
        removeTripPolylines();
        removeTripStopCircles();
        removeDataReceivedMarker();
        destroyEstimateOverlays();
        mActive = false;
        mActiveTripId = null;
    }

    boolean isActive() {
        return mActive;
    }

    String getActiveTripId() {
        return mActiveTripId;
    }

    // --- Trip polyline ---

    private void showTripPolyline(List<Location> tripShape, int color) {
        removeTripPolylines();
        mChevronHelper.addArrowPolyline(mMap, mTripPolylines, tripShape, color,
                TRIP_BASE_WIDTH_PX, 4, mContext.getResources());
    }

    private void removeTripPolylines() {
        for (Polyline p : mTripPolylines) {
            p.remove();
        }
        mTripPolylines.clear();
    }

    // --- Trip stop circles ---

    private void showTripStopCircles(ObaTripSchedule schedule,
                                     List<Location> shape, double[] cumDist,
                                     Map<String, String> stopNames,
                                     String selectedStopId) {
        if (mMap == null || schedule == null || shape == null || cumDist == null) return;
        ObaTripSchedule.StopTime[] stopTimes = schedule.getStopTimes();
        if (stopTimes == null) return;

        BitmapDescriptor icon = makeStopCircleIcon();
        BitmapDescriptor selectedIcon = selectedStopId != null
                ? makeBullseyeIcon() : null;
        for (ObaTripSchedule.StopTime st : stopTimes) {
            Location loc = LocationUtils.interpolateAlongPolyline(
                    shape, cumDist, st.getDistanceAlongTrip());
            if (loc == null) continue;
            boolean isSelected = st.getStopId().equals(selectedStopId);
            Marker m = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(loc.getLatitude(), loc.getLongitude()))
                    .icon(isSelected ? selectedIcon : icon)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .zIndex(isSelected ? 1.5f : 1f));
            mTripStopMarkers.add(m);
            String name = stopNames != null ? stopNames.get(st.getStopId()) : null;
            if (name == null) name = st.getStopId();
            mStopInfoMap.put(m, new StopInfo(name, st.getArrivalTime()));
        }
    }

    private BitmapDescriptor makeStopCircleIcon() {
        int size = (int) TRIP_BASE_WIDTH_PX;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = size / 2f;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        c.drawCircle(r, r, r - STOP_STROKE_WIDTH / 2f, fill);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(STOP_STROKE_WIDTH);
        stroke.setColor(STOP_STROKE_COLOR);
        c.drawCircle(r, r, r - STOP_STROKE_WIDTH / 2f, stroke);
        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    private BitmapDescriptor makeBullseyeIcon() {
        int size = (int) TRIP_BASE_WIDTH_PX;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = size / 2f;
        // Outer white circle with dark stroke (same as normal stop)
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        c.drawCircle(r, r, r - STOP_STROKE_WIDTH / 2f, fill);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(STOP_STROKE_WIDTH);
        stroke.setColor(STOP_STROKE_COLOR);
        c.drawCircle(r, r, r - STOP_STROKE_WIDTH / 2f, stroke);
        // Inner filled dot
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(STOP_STROKE_COLOR);
        c.drawCircle(r, r, r * 0.4f, dot);
        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    private void removeTripStopCircles() {
        for (Marker m : mTripStopMarkers) {
            m.remove();
        }
        mTripStopMarkers.clear();
        mStopInfoMap.clear();
    }

    /**
     * Handles a click on a trip stop marker. Computes the ETA based on the
     * vehicle's current position and speed, sets the info window content,
     * and shows it. Returns true if the marker was a stop marker.
     */
    boolean handleStopMarkerClick(Marker marker) {
        StopInfo info = mStopInfoMap.get(marker);
        if (info == null) return false;

        marker.setTitle(info.name);
        marker.setSnippet(computeEtaSnippet(info.arrivalTimeSec));
        marker.showInfoWindow();
        return true;
    }

    private String computeEtaSnippet(long arrivalTimeSec) {
        if (mActiveTripId == null) return null;

        Long serviceDate = TripDataManager.getInstance()
                .getServiceDate(mActiveTripId);
        if (serviceDate == null) return null;

        long predictedMs = serviceDate + arrivalTimeSec * 1000 + mScheduleDeviation * 1000;
        long now = System.currentTimeMillis();
        long diffMs = predictedMs - now;
        long diffMin = TimeUnit.MILLISECONDS.toMinutes(diffMs);

        String clockTime = UIUtils.formatTime(mContext, predictedMs);

        if (diffMs <= 0) {
            return clockTime + " (departed)";
        }
        if (diffMin < 1) {
            return clockTime + " (< 1 min)";
        }
        return clockTime + " (" + diffMin + " min)";
    }

    // --- Estimate overlays ---

    void updateEstimateOverlays(SpeedDistribution distribution,
                                List<Location> shape, double[] cumDist,
                                List<ObaTripStatus> history, long now,
                                int baseColor) {
        if (mEstimateOverlay == null) return;

        if (distribution == null || shape == null || cumDist == null
                || history == null || history.isEmpty()) {
            hideEstimateOverlays();
            return;
        }

        // Find newest entry with valid AVL data (equivalent to old findNewestValid)
        ObaTripStatus newest = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            ObaTripStatus entry = history.get(i);
            if (ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(entry) != null
                    && entry.getLastLocationUpdateTime() > 0) {
                newest = entry;
                break;
            }
        }
        if (newest == null) {
            hideEstimateOverlays();
            return;
        }

        Double lastDist = ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(newest);
        long lastTime = newest.getLastLocationUpdateTime();
        if (lastDist == null || lastTime <= 0) {
            hideEstimateOverlays();
            return;
        }

        double dtSec = (now - lastTime) / 1000.0;
        if (dtSec < 0.5) {
            hideEstimateOverlays();
            return;
        }

        mEstimateOverlay.update(distribution, shape, cumDist, lastDist, dtSec, baseColor);
    }

    void hideEstimateOverlays() {
        if (mEstimateOverlay != null) mEstimateOverlay.hide();
    }

    boolean handleEstimateLabelClick(Marker marker) {
        return mEstimateOverlay != null && mEstimateOverlay.handleClick(marker);
    }

    boolean handleDataReceivedClick(Marker marker) {
        if (marker.equals(mDataReceivedIconMarker)) {
            marker.showInfoWindow();
            return true;
        }
        return false;
    }

    private void createEstimateOverlays(String tripId, LatLng vehiclePosition,
                                        Integer routeType) {
        if (tripId == null) return;
        if (routeType != null && ObaRoute.isGradeSeparated(routeType)) return;
        if (vehiclePosition == null) return;

        mEstimateOverlay = new EstimateOverlayManager(mMap, mContext);
        mEstimateOverlay.create(vehiclePosition);
    }

    private void destroyEstimateOverlays() {
        if (mEstimateOverlay != null) {
            mEstimateOverlay.destroy();
            mEstimateOverlay = null;
        }
    }

    // --- Data-received marker ---

    void showOrUpdateDataReceivedMarker(String tripId,
                                        List<Location> shape, double[] cumDist,
                                        List<ObaTripStatus> history) {
        if (tripId == null || history == null || history.isEmpty()) return;

        ObaTripStatus latest = history.get(history.size() - 1);
        long updateTime = latest.getLastLocationUpdateTime();
        boolean newData = updateTime != mLastDataReceivedUpdateTime;

        // Always refresh the elapsed-time snippet so it stays current
        String label = formatElapsedTime(updateTime);
        if (mDataReceivedIconMarker != null && !label.equals(mLastDataReceivedLabel)
                && !mDataReceivedIconMarker.isInfoWindowShown()) {
            mDataReceivedIconMarker.setSnippet(label);
        }
        mLastDataReceivedLabel = label;

        // Skip position/marker creation if the history entry hasn't changed
        if (!newData && mDataReceivedIconMarker != null) {
            return;
        }
        mLastDataReceivedUpdateTime = updateTime;

        Location pos = latest.getLastKnownLocation();
        if (pos == null) pos = latest.getPosition();
        if (pos == null) return;

        LatLng latLng = MapHelpV2.makeLatLng(pos);

        if (mDataReceivedIconMarker != null) {
            mDataReceivedIconMarker.setPosition(latLng);
        } else {
            if (mCachedCircleIcon == null) {
                mCachedCircleIcon = createDataReceivedCircleIcon();
            }
            mDataReceivedIconMarker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .icon(mCachedCircleIcon)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .title(DATA_RECEIVED_TITLE)
                    .snippet(label)
                    .zIndex(DATA_RECEIVED_Z_INDEX + 0.1f)
            );
        }
    }

    void removeDataReceivedMarker() {
        if (mDataReceivedIconMarker != null) {
            mDataReceivedIconMarker.remove();
            mDataReceivedIconMarker = null;
        }
        mLastDataReceivedLabel = null;
        mLastDataReceivedUpdateTime = 0;
    }

    private String formatElapsedTime(long lastUpdateTime) {
        if (lastUpdateTime <= 0) return "";
        long elapsedSec = TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis() - lastUpdateTime);
        if (elapsedSec < 0) elapsedSec = 0;
        if (elapsedSec < 60) {
            return elapsedSec + " sec ago";
        }
        long elapsedMin = TimeUnit.SECONDS.toMinutes(elapsedSec);
        long secMod60 = elapsedSec % 60;
        return elapsedMin + " min " + secMod60 + " sec ago";
    }

    private BitmapDescriptor createDataReceivedCircleIcon() {
        float d = mContext.getResources().getDisplayMetrics().density;
        int circleRadius = (int) (DATA_ICON_RADIUS_DP * d);
        int size = circleRadius * 2;

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(0xFF616161);
        circlePaint.setStyle(Paint.Style.FILL);
        c.drawCircle(circleRadius, circleRadius, circleRadius, circlePaint);

        int iconSize = (int) (DATA_ICON_INNER_DP * d);
        android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(
                mContext, R.drawable.ic_signal_indicator);
        if (icon != null) {
            int iconLeft = (size - iconSize) / 2;
            int iconTop = (size - iconSize) / 2;
            icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
            icon.draw(c);
        }

        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

}
