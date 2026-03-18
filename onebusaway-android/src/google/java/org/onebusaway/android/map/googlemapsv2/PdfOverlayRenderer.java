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

import android.location.Location;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.onebusaway.android.util.LocationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the gamma speed PDF as polyline segments with varying opacity
 * on top of the trip polyline. Each segment's alpha is proportional to the
 * PDF value at its midpoint, normalized so the peak is fully opaque.
 */
public final class PdfOverlayRenderer {

    private static final float Z_INDEX = 2f;

    private final GoogleMap mMap;
    private final int mSegmentCount;
    private final float mWidthPx;
    private Polyline[] mSegments;

    // Pre-allocated array to avoid per-frame allocation
    private final double[] mSegDists;
    private final Location mReusableLoc = new Location("pdf");

    public PdfOverlayRenderer(GoogleMap map, int segmentCount, float widthPx) {
        mMap = map;
        mSegmentCount = segmentCount;
        mWidthPx = widthPx;
        mSegDists = new double[segmentCount + 1];
    }

    /** Creates the polyline segments on the map. */
    public void create() {
        mSegments = new Polyline[mSegmentCount];
        for (int i = 0; i < mSegmentCount; i++) {
            mSegments[i] = mMap.addPolyline(new PolylineOptions()
                    .width(mWidthPx)
                    .color(0)
                    .zIndex(Z_INDEX));
        }
    }

    /** Removes the segments from the map and clears state. */
    public void destroy() {
        if (mSegments == null) return;
        for (Polyline p : mSegments) p.remove();
        mSegments = null;
    }

    /** Hides all segments without removing them. */
    public void hide() {
        if (mSegments == null) return;
        for (Polyline p : mSegments) p.setVisible(false);
    }

    public boolean isActive() {
        return mSegments != null;
    }

    /**
     * Per-frame update: positions segments and sets opacities from pre-computed PDF values.
     *
     * @param edgeSpeedsMps edge speeds in m/s (length segmentCount + 1)
     * @param pdfValues     PDF values at segment midpoints (length segmentCount)
     * @param lastDist      AVL distance along the trip
     * @param dtSec         seconds since last AVL update
     * @param baseColor     RGB color (alpha ignored) for the overlay segments
     * @param shape         decoded polyline points
     * @param cumDist       precomputed cumulative distances
     */
    public void update(double[] edgeSpeedsMps, double[] pdfValues,
                       double lastDist, double dtSec, int baseColor,
                       List<Location> shape, double[] cumDist) {
        if (mSegments == null) return;

        // Convert edge speeds to distances and find max PDF value
        double maxPdf = 0;
        for (int i = 0; i <= mSegmentCount; i++) {
            mSegDists[i] = lastDist + edgeSpeedsMps[i] * dtSec;
        }
        for (int i = 0; i < mSegmentCount; i++) {
            if (pdfValues[i] > maxPdf) maxPdf = pdfValues[i];
        }

        // Update each segment's geometry and opacity
        int rgb = baseColor & 0x00FFFFFF;
        for (int i = 0; i < mSegmentCount; i++) {
            updateSegment(i, mSegDists[i], mSegDists[i + 1],
                    pdfValues[i], maxPdf, rgb, shape, cumDist);
        }
    }

    private void updateSegment(int index, double segStart, double segEnd,
                               double pdfValue, double maxPdf, int rgb,
                               List<Location> shape, double[] cumDist) {
        List<LatLng> pts = new ArrayList<>();

        if (!LocationUtils.interpolateAlongPolyline(
                shape, cumDist, segStart, mReusableLoc)) {
            mSegments[index].setVisible(false); return;
        }
        pts.add(new LatLng(mReusableLoc.getLatitude(), mReusableLoc.getLongitude()));

        int[] range = LocationUtils.findVertexRange(cumDist, segStart, segEnd);
        if (range != null) {
            for (int j = range[0]; j < range[1]; j++) {
                Location v = shape.get(j);
                pts.add(new LatLng(v.getLatitude(), v.getLongitude()));
            }
        }

        if (!LocationUtils.interpolateAlongPolyline(
                shape, cumDist, segEnd, mReusableLoc)) {
            mSegments[index].setVisible(false); return;
        }
        pts.add(new LatLng(mReusableLoc.getLatitude(), mReusableLoc.getLongitude()));

        int alpha = maxPdf > 0 ? (int) (255 * pdfValue / maxPdf) : 0;
        mSegments[index].setPoints(pts);
        mSegments[index].setColor((alpha << 24) | rgb);
        mSegments[index].setVisible(true);
    }
}
