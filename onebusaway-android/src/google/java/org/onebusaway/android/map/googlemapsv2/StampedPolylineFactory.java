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

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.StampStyle;
import com.google.android.gms.maps.model.StrokeStyle;
import com.google.android.gms.maps.model.StyleSpan;
import com.google.android.gms.maps.model.TextureStyle;

import java.util.List;

/**
 * Creates stamped polyline options using a single stamp built at construction
 * time.
 */
public final class StampedPolylineFactory {

    private final StampStyle mStamp;

    /**
     * @param res               resources for decoding the drawable
     * @param resId             drawable resource to use as the stamp
     * @param spacingMultiplier 1 = default density, 4 = one-quarter as many stamps
     */
    public StampedPolylineFactory(Resources res, int resId, int spacingMultiplier) {
        Bitmap stamp = BitmapFactory.decodeResource(res, resId);
        if (spacingMultiplier > 1) {
            Bitmap padded = Bitmap.createBitmap(
                    stamp.getWidth(),
                    stamp.getHeight() * spacingMultiplier,
                    Bitmap.Config.ARGB_8888);
            new Canvas(padded).drawBitmap(stamp, 0, 0, null);
            stamp.recycle();
            stamp = padded;
        }
        mStamp = TextureStyle.newBuilder(
                BitmapDescriptorFactory.fromBitmap(stamp)).build();
        stamp.recycle();
    }

    /**
     * Creates stamped polyline options.
     */
    public PolylineOptions create(List<Location> points, int color, float width) {
        PolylineOptions opts = new PolylineOptions();
        opts.width(width);
        opts.zIndex(1f);
        opts.addSpan(new StyleSpan(StrokeStyle.colorBuilder(color)
                .stamp(mStamp).build()));
        for (Location l : points) {
            opts.add(MapHelpV2.makeLatLng(l));
        }
        return opts;
    }
}
