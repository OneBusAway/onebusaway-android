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
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import org.onebusaway.android.R;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Creates circular map marker icons with a vector drawable centered inside.
 */
public final class MapIconUtils {

    private static final int ICON_SIZE_DP = 28;
    private static final int ICON_PADDING_DP = 4;
    private static final float STROKE_WIDTH_DP = 2f;
    private static final int STROKE_COLOR = 0xFF616161;
    private static final int FILL_COLOR = 0xDDFFFFFF;

    private MapIconUtils() {
    }

    /**
     * Creates a circular icon with a dark stroke, translucent white fill, and the
     * given
     * drawable centered inside. The drawable is rendered at its intrinsic color.
     */
    public static BitmapDescriptor createCircleIcon(Context context, @DrawableRes int drawableRes) {
        return createCircleIcon(context, drawableRes, 0);
    }

    /**
     * Creates a circular icon with a dark stroke, translucent white fill, and the
     * given
     * drawable centered inside. If tintColor is non-zero, the drawable is tinted.
     */
    public static BitmapDescriptor createCircleIcon(Context context, @DrawableRes int drawableRes,
            int tintColor) {
        float d = context.getResources().getDisplayMetrics().density;
        int sizePx = (int) (ICON_SIZE_DP * d);
        int padding = (int) (ICON_PADDING_DP * d);
        float strokeWidth = STROKE_WIDTH_DP * d;
        float cx = sizePx / 2f;
        float cy = sizePx / 2f;

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(STROKE_COLOR);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);
        canvas.drawCircle(cx, cy, cx - strokeWidth / 2, strokePaint);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(FILL_COLOR);
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, cx - strokeWidth, fillPaint);

        Drawable icon = ContextCompat.getDrawable(context, drawableRes);
        if (icon != null) {
            if (tintColor != 0) {
                icon.setTint(tintColor);
            }
            icon.setBounds(padding, padding, sizePx - padding, sizePx - padding);
            icon.draw(canvas);
        }

        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    /**
     * Creates the standard data-received marker icon (signal indicator in stroke
     * color).
     */
    public static BitmapDescriptor createDataReceivedIcon(Context context) {
        return createCircleIcon(context, R.drawable.ic_signal_indicator, STROKE_COLOR);
    }
}
