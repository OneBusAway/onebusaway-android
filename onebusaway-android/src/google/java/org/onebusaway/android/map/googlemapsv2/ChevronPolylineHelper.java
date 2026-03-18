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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.location.Location;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.StampStyle;
import com.google.android.gms.maps.model.StrokeStyle;
import com.google.android.gms.maps.model.StyleSpan;
import com.google.android.gms.maps.model.TextureStyle;

import org.onebusaway.android.R;

import java.util.HashMap;
import java.util.List;

/**
 * Shared chevron-stamped polyline logic used by both route overview and trip view.
 */
final class ChevronPolylineHelper {

    private final HashMap<Integer, StampStyle> mChevronStampCache = new HashMap<>();

    /**
     * Creates a bitmap that tiles along a polyline with extra transparent
     * padding so the visible stamp repeats less frequently.
     *
     * @param spacingMultiplier 1 = default density, 4 = one-quarter as many stamps
     */
    Bitmap spacedStamp(Resources res, int resId, int spacingMultiplier) {
        Bitmap original = BitmapFactory.decodeResource(res, resId);
        if (spacingMultiplier <= 1) return original;
        Bitmap padded = Bitmap.createBitmap(
                original.getWidth(),
                original.getHeight() * spacingMultiplier,
                Bitmap.Config.ARGB_8888);
        new Canvas(padded).drawBitmap(original, 0, 0, null);
        return padded;
    }

    StampStyle chevronStamp(Resources res, int spacingMultiplier) {
        StampStyle cached = mChevronStampCache.get(spacingMultiplier);
        if (cached != null) return cached;
        cached = TextureStyle.newBuilder(BitmapDescriptorFactory.fromBitmap(
                spacedStamp(res, R.drawable.ic_navigation_expand_more, spacingMultiplier))).build();
        mChevronStampCache.put(spacingMultiplier, cached);
        return cached;
    }

    /**
     * Adds a chevron-stamped polyline to the map.
     *
     * @return the number of points in the polyline
     */
    int addArrowPolyline(GoogleMap map, List<Polyline> sink, List<Location> points,
                         int color, float width, int stampSpacing, Resources res) {
        PolylineOptions opts = new PolylineOptions();
        opts.width(width);
        opts.zIndex(1f);
        opts.addSpan(new StyleSpan(StrokeStyle.colorBuilder(color)
                .stamp(chevronStamp(res, stampSpacing)).build()));
        for (Location l : points) {
            opts.add(MapHelpV2.makeLatLng(l));
        }
        sink.add(map.addPolyline(opts));
        return opts.getPoints().size();
    }

    /**
     * Adds a chevron-stamped polyline with default width and spacing.
     *
     * @return the number of points in the polyline
     */
    int addArrowPolyline(GoogleMap map, List<Polyline> sink, List<Location> points,
                         int color, Resources res) {
        return addArrowPolyline(map, sink, points, color, 10f, 1, res);
    }
}
