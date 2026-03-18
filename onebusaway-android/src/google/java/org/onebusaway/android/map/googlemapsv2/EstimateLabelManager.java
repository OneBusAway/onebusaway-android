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
import android.graphics.Paint;
import android.location.Location;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.onebusaway.android.util.LocationUtils;

import java.util.List;

/**
 * Owns the slow/fast estimate label markers, icon creation, and click-to-expand.
 * Labels interpolate directly on the polyline.
 */
public final class EstimateLabelManager {

    static final float INFO_LABEL_Z_INDEX = 0.5f; // VEHICLE_MARKER_Z_INDEX (1) - 0.5f
    private static final int INFO_LABEL_SP = 10;
    private static final int INFO_LABEL_POINTER_WIDTH_DP = 10;
    private static final String SLOW_ESTIMATE_LABEL = "Slow estimate";
    private static final String FAST_ESTIMATE_LABEL = "Fast estimate";
    private static final String SLOW_ESTIMATE_EXPANDED = "Slow estimate\n10th percentile speed";
    private static final String FAST_ESTIMATE_EXPANDED = "Fast estimate\n90th percentile speed";
    private static final double LABEL_DEADZONE_DEG = 20.0;

    private final GoogleMap mMap;
    private final Context mContext;

    private Marker mSlowEstimateMarker;
    private Marker mFastEstimateMarker;
    private BitmapDescriptor mSlowEstimateIcon;
    private BitmapDescriptor mFastEstimateIcon;
    private BitmapDescriptor mSlowEstimateExpandedIcon;
    private BitmapDescriptor mFastEstimateExpandedIcon;
    private boolean mSlowEstimateExpanded;
    private boolean mFastEstimateExpanded;

    private final Location mReusableLoc = new Location("label");

    public EstimateLabelManager(GoogleMap map, Context context) {
        mMap = map;
        mContext = context;
    }

    /** Creates the label markers at the given initial position. */
    public void create(LatLng initialPosition) {
        mSlowEstimateIcon = createInfoLabelIcon(SLOW_ESTIMATE_LABEL, null);
        mFastEstimateIcon = createInfoLabelIcon(FAST_ESTIMATE_LABEL, null);
        mSlowEstimateExpandedIcon = createInfoLabelExpandedIcon(SLOW_ESTIMATE_EXPANDED);
        mFastEstimateExpandedIcon = createInfoLabelExpandedIcon(FAST_ESTIMATE_EXPANDED);
        mSlowEstimateExpanded = false;
        mFastEstimateExpanded = false;

        mSlowEstimateMarker = addFlatInfoLabelMarker(initialPosition, mSlowEstimateIcon);
        mFastEstimateMarker = addFlatInfoLabelMarker(initialPosition, mFastEstimateIcon);
    }

    /** Removes the label markers and clears state. */
    public void destroy() {
        if (mSlowEstimateMarker != null) {
            mSlowEstimateMarker.remove();
            mSlowEstimateMarker = null;
        }
        if (mFastEstimateMarker != null) {
            mFastEstimateMarker.remove();
            mFastEstimateMarker = null;
        }
        mSlowEstimateIcon = null;
        mFastEstimateIcon = null;
        mSlowEstimateExpandedIcon = null;
        mFastEstimateExpandedIcon = null;
    }

    /** Hides the markers without removing them. */
    public void hide() {
        if (mSlowEstimateMarker != null) mSlowEstimateMarker.setVisible(false);
        if (mFastEstimateMarker != null) mFastEstimateMarker.setVisible(false);
    }

    public boolean isActive() {
        return mSlowEstimateMarker != null;
    }

    /**
     * Per-frame update: positions labels at the given distances along the polyline.
     *
     * @param dist10  slow estimate distance (10th percentile)
     * @param dist90  fast estimate distance (90th percentile)
     * @param shape   decoded polyline points
     * @param cumDist precomputed cumulative distances
     */
    public void update(double dist10, double dist90,
                       List<Location> shape, double[] cumDist) {
        if (mSlowEstimateMarker == null || mFastEstimateMarker == null) return;

        updateInfoLabelPosition(mSlowEstimateMarker, dist10, shape, cumDist);
        updateInfoLabelPosition(mFastEstimateMarker, dist90, shape, cumDist);
    }

    /**
     * Handles a click on an info label marker by toggling between collapsed
     * and expanded label. Returns true if the marker was an info label.
     */
    public boolean handleClick(Marker marker) {
        if (marker.equals(mSlowEstimateMarker)) {
            mSlowEstimateExpanded = !mSlowEstimateExpanded;
            mSlowEstimateMarker.setIcon(mSlowEstimateExpanded
                    ? mSlowEstimateExpandedIcon : mSlowEstimateIcon);
            return true;
        }
        if (marker.equals(mFastEstimateMarker)) {
            mFastEstimateExpanded = !mFastEstimateExpanded;
            mFastEstimateMarker.setIcon(mFastEstimateExpanded
                    ? mFastEstimateExpandedIcon : mFastEstimateIcon);
            return true;
        }
        return false;
    }

    // --- Label positioning ---

    private void updateInfoLabelPosition(Marker marker, double distance,
                                         List<Location> shape, double[] cumDist) {
        if (!LocationUtils.interpolateAlongPolyline(
                shape, cumDist, distance, mReusableLoc)) {
            marker.setVisible(false);
            return;
        }
        marker.setPosition(new LatLng(
                mReusableLoc.getLatitude(),
                mReusableLoc.getLongitude()));
        double heading = LocationUtils.headingAlongPolyline(
                shape, cumDist, distance);
        if (!Double.isNaN(heading)) {
            double labelAz = clampedLabelAzimuth(heading);
            marker.setRotation((float) (labelAz - 90.0));
        }
        marker.setVisible(true);
    }

    private Marker addFlatInfoLabelMarker(LatLng pos, BitmapDescriptor icon) {
        return mMap.addMarker(new MarkerOptions()
                .position(pos)
                .icon(icon)
                .anchor(0f, 0.5f)
                .flat(true)
                .zIndex(INFO_LABEL_Z_INDEX)
                .visible(false)
        );
    }

    // --- Icon creation (static, shared with data-received label in VehicleOverlay) ---

    private BitmapDescriptor createInfoLabelIcon(String label, float[] outWidth) {
        return createInfoLabelIcon(mContext, new String[]{label}, outWidth);
    }

    private BitmapDescriptor createInfoLabelExpandedIcon(String label) {
        return createInfoLabelIcon(mContext, label.split("\n"), null);
    }

    static BitmapDescriptor createInfoLabelIcon(Context context, String[] lines,
                                                float[] outWidth) {
        float d = context.getResources().getDisplayMetrics().density;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(INFO_LABEL_SP * d);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setColor(0xFF616161);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        int lineHeight = (int) Math.ceil(fm.descent - fm.ascent);
        float lineGap = d;

        float maxLineWidth = 0;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, textPaint.measureText(line));
        }

        float padLeft = 4 * d;
        float padRight = 6 * d;
        float padY = 2 * d;
        float pointerWidth = INFO_LABEL_POINTER_WIDTH_DP * d;
        int textBlockHeight = lineHeight * lines.length
                + (int) (lineGap * (lines.length - 1));
        int bubbleWidth = (int) (pointerWidth + padLeft + maxLineWidth + padRight);
        int bubbleHeight = (int) (textBlockHeight + padY * 2);
        float cornerRadius = 3 * d;

        Bitmap bmp = Bitmap.createBitmap(bubbleWidth, bubbleHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        float bodyLeft = drawPointerBubble(c, 0, 0,
                bubbleWidth, bubbleHeight, cornerRadius, pointerWidth);

        float textX = bodyLeft + padLeft;
        float textY = padY - fm.ascent;
        for (int i = 0; i < lines.length; i++) {
            c.drawText(lines[i], textX, textY + i * (lineHeight + lineGap), textPaint);
        }

        if (outWidth != null && outWidth.length > 0) {
            outWidth[0] = bubbleWidth;
        }
        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    /**
     * Draws a 5-sided pointer bubble: a rectangle with the left side replaced
     * by two edges meeting at a pointed tip.
     *
     * @return the X coordinate of the body's left edge (after the pointer)
     */
    private static float drawPointerBubble(Canvas c, float left, float top,
                                   float width, float height,
                                   float cornerRadius, float pointerWidth) {
        float bodyLeft = left + pointerWidth;
        float right = left + width;
        float bottom = top + height;
        float midY = top + height / 2f;
        float r = cornerRadius;

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(left, midY);
        path.lineTo(bodyLeft, top);
        path.lineTo(right - r, top);
        path.quadTo(right, top, right, top + r);
        path.lineTo(right, bottom - r);
        path.quadTo(right, bottom, right - r, bottom);
        path.lineTo(bodyLeft, bottom);
        path.close();

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xDDFFFFFF);
        bgPaint.setStyle(Paint.Style.FILL);
        c.drawPath(path, bgPaint);

        return bodyLeft;
    }

    // --- Azimuth helpers ---

    private static double normalizeDeg(double angle) {
        return ((angle % 360) + 360) % 360;
    }

    private static double headingToLabelAzimuth(double heading) {
        double az = normalizeDeg(heading - 90);
        if (az > 180) {
            az = normalizeDeg(heading + 90);
        }
        return az;
    }

    static double clampedLabelAzimuth(double heading) {
        double labelAz = headingToLabelAzimuth(heading);
        labelAz = clampAzimuthAwayFrom(labelAz, 0);
        labelAz = clampAzimuthAwayFrom(labelAz, 180);
        return labelAz;
    }

    private static double clampAzimuthAwayFrom(double value, double center) {
        double delta = value - center;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        if (delta >= -LABEL_DEADZONE_DEG && delta < 0) {
            return normalizeDeg(center - LABEL_DEADZONE_DEG);
        }
        if (delta >= 0 && delta < LABEL_DEADZONE_DEG) {
            return normalizeDeg(center + LABEL_DEADZONE_DEG);
        }
        return value;
    }
}
