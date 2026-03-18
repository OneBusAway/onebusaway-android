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
import android.location.Location;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import org.onebusaway.android.extrapolation.math.SpeedDistribution;

import java.util.List;

/**
 * Manages all estimate-related map overlays for a selected vehicle:
 * the slow/fast estimate labels and the PDF opacity segments.
 * Computes quantile speeds once per param change and shares them.
 */
public final class EstimateOverlayManager {

    private final EstimateLabelManager mLabels;
    private final PdfOverlayRenderer mPdfOverlay;
    private final double mLabelLowQuantile;
    private final double mLabelHighQuantile;
    private final double mPdfLowQuantile;
    private final double mPdfHighQuantile;

    private final int mSegmentCount;
    private SpeedDistribution mCachedDistribution;
    private double mCachedLabelSpeedLowMps;
    private double mCachedLabelSpeedHighMps;
    private final double[] mCachedPdfEdgeSpeedsMps;
    private final double[] mCachedPdfMidPdfValues;

    public EstimateOverlayManager(GoogleMap map, Context context) {
        this(map, context, 0.10, 0.90, 0.01, 0.99, 9);
    }

    /**
     * @param labelLowQuantile  quantile for the slow estimate label (e.g. 0.10)
     * @param labelHighQuantile quantile for the fast estimate label (e.g. 0.90)
     * @param pdfLowQuantile    quantile for the PDF overlay start (e.g. 0.01)
     * @param pdfHighQuantile   quantile for the PDF overlay end (e.g. 0.99)
     * @param segmentCount      number of opacity segments in the PDF overlay
     */
    public EstimateOverlayManager(GoogleMap map, Context context,
                                  double labelLowQuantile, double labelHighQuantile,
                                  double pdfLowQuantile, double pdfHighQuantile,
                                  int segmentCount) {
        mLabels = new EstimateLabelManager(map, context);
        mPdfOverlay = new PdfOverlayRenderer(map, segmentCount,
                TripMapRenderer.TRIP_BASE_WIDTH_PX);
        mSegmentCount = segmentCount;
        mCachedPdfEdgeSpeedsMps = new double[segmentCount + 1];
        mCachedPdfMidPdfValues = new double[segmentCount];
        mLabelLowQuantile = labelLowQuantile;
        mLabelHighQuantile = labelHighQuantile;
        mPdfLowQuantile = pdfLowQuantile;
        mPdfHighQuantile = pdfHighQuantile;
    }

    /** Creates all overlays at the given initial position. */
    public void create(LatLng initialPosition) {
        mLabels.create(initialPosition);
        mPdfOverlay.create();
        mCachedDistribution = null;
    }

    /** Removes all overlays from the map. */
    public void destroy() {
        mLabels.destroy();
        mPdfOverlay.destroy();
        mCachedDistribution = null;
    }

    /** Hides all overlays without removing them. */
    public void hide() {
        mLabels.hide();
        mPdfOverlay.hide();
    }

    /**
     * Per-frame update for all estimate overlays.
     */
    public void update(SpeedDistribution distribution,
                       List<Location> shape, double[] cumDist,
                       double lastDist, double dtSec, int baseColor) {
        if (!distribution.equals(mCachedDistribution)) {
            mCachedDistribution = distribution;
            mCachedLabelSpeedLowMps = distribution.quantile(mLabelLowQuantile);
            mCachedLabelSpeedHighMps = distribution.quantile(mLabelHighQuantile);

            for (int i = 0; i <= mSegmentCount; i++) {
                double p = mPdfLowQuantile
                        + (mPdfHighQuantile - mPdfLowQuantile) * i / mSegmentCount;
                mCachedPdfEdgeSpeedsMps[i] = distribution.quantile(p);
            }
            for (int i = 0; i < mSegmentCount; i++) {
                double midSpeedMps = (mCachedPdfEdgeSpeedsMps[i]
                        + mCachedPdfEdgeSpeedsMps[i + 1]) / 2.0;
                mCachedPdfMidPdfValues[i] = distribution.pdf(midSpeedMps);
            }
        }

        mLabels.update(
                lastDist + mCachedLabelSpeedLowMps * dtSec,
                lastDist + mCachedLabelSpeedHighMps * dtSec,
                shape, cumDist);
        mPdfOverlay.update(mCachedPdfEdgeSpeedsMps, mCachedPdfMidPdfValues,
                lastDist, dtSec, baseColor, shape, cumDist);
    }

    /** Delegates click handling to the estimate labels. */
    public boolean handleClick(Marker marker) {
        return mLabels.handleClick(marker);
    }
}
