/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.map.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import org.onebusaway.android.R;

/**
 * Flavor-neutral generation of the three bike marker <b>bitmaps</b> (the small dot drawn from a
 * vector via a Canvas, and the big station / floating-bike raster icons). Lives in {@code src/main}
 * so the Google flavor wraps them as {@code BitmapDescriptor}s and maplibre as {@code Icon}s.
 * Lifted from the old BikeStationOverlay.
 */
public final class BikeBitmaps {

    private BikeBitmaps() {
    }

    // The three icons never vary, so cache them once. The maplibre renderer clears + redraws every
    // marker on each snapshot, so without this it would re-decode these PNGs per bike per render.
    private static Bitmap sSmall;

    private static Bitmap sBigStation;

    private static Bitmap sBigFloating;

    /** The small bike-dot bitmap, drawn from the {@code bike_marker_small} vector. */
    public static Bitmap small(Context context) {
        if (sSmall == null) {
            int px = context.getResources().getDimensionPixelSize(R.dimen.bikeshare_small_marker_size);
            Bitmap bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Drawable shape = ContextCompat.getDrawable(context, R.drawable.bike_marker_small);
            shape.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            shape.draw(canvas);
            sSmall = bitmap;
        }
        return sSmall;
    }

    /** The large bike-station icon. */
    public static Bitmap bigStation(Context context) {
        if (sBigStation == null) {
            sBigStation = BitmapFactory.decodeResource(context.getResources(),
                    R.drawable.bike_station_marker_big);
        }
        return sBigStation;
    }

    /** The large floating-bike icon. */
    public static Bitmap bigFloating(Context context) {
        if (sBigFloating == null) {
            sBigFloating = BitmapFactory.decodeResource(context.getResources(),
                    R.drawable.bike_floating_marker_big);
        }
        return sBigFloating;
    }
}
