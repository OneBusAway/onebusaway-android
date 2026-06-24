/*
 * Copyright (C) 2014-2024 University of South Florida (sjbarbeau@gmail.com)
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import java.util.HashMap;
import java.util.Map;

/**
 * Stateless factory for the maplibre bus-stop marker icons, extracted from the old maplibre
 * StopOverlay. Holds the 9-direction normal + focused bitmap caches and wraps them as maplibre
 * {@link Icon}s; the declarative MapRenderState renderer asks it for an icon per stop. Built once on
 * first use. (maplibre markers have no per-marker anchor, so the direction offset math is dropped.)
 */
public final class MapLibreStopIcons {

    private MapLibreStopIcons() {
    }

    private static final String TAG = "MapLibreStopIcons";

    private static boolean sLoaded = false;

    private static final String NORTH = "N";
    private static final String NORTH_WEST = "NW";
    private static final String WEST = "W";
    private static final String SOUTH_WEST = "SW";
    private static final String SOUTH = "S";
    private static final String SOUTH_EAST = "SE";
    private static final String EAST = "E";
    private static final String NORTH_EAST = "NE";
    private static final String NO_DIRECTION = "null";

    private static final Map<String, Integer> directionToIndexMap = new HashMap<>();

    static {
        directionToIndexMap.put(NORTH, 0);
        directionToIndexMap.put(NORTH_WEST, 1);
        directionToIndexMap.put(WEST, 2);
        directionToIndexMap.put(SOUTH_WEST, 3);
        directionToIndexMap.put(SOUTH, 4);
        directionToIndexMap.put(SOUTH_EAST, 5);
        directionToIndexMap.put(EAST, 6);
        directionToIndexMap.put(NORTH_EAST, 7);
        directionToIndexMap.put(NO_DIRECTION, 8);
    }

    private static final int NUM_DIRECTIONS = 9;

    private static final Bitmap[] bus_stop_icons = new Bitmap[NUM_DIRECTIONS];
    private static final Bitmap[] bus_stop_icons_focused = new Bitmap[NUM_DIRECTIONS];

    private static final float FOCUS_ICON_SCALE = 1.5f;

    private static int mPx;
    private static float mArrowWidthPx = 0;
    private static float mArrowHeightPx = 0;
    private static float mBuffer = 0;
    private static float mPercentOffset = 0.5f;
    private static Paint mArrowPaintStroke;

    /** Builds the icon caches on first use. */
    public static synchronized void ensureLoaded() {
        if (!sLoaded) {
            loadIcons();
            sLoaded = true;
        }
    }

    /** The normal (unfocused) stop icon for a direction string ("N".."NW" / "null"). */
    public static synchronized Icon iconForDirection(Context context, String direction) {
        ensureLoaded();
        return IconFactory.getInstance(context).fromBitmap(bus_stop_icons[indexFor(direction)]);
    }

    /** The focused (1.5x) stop icon for a direction string. */
    public static synchronized Icon focusedIconForDirection(Context context, String direction) {
        ensureLoaded();
        return IconFactory.getInstance(context).fromBitmap(bus_stop_icons_focused[indexFor(direction)]);
    }

    private static int indexFor(String direction) {
        Integer index = directionToIndexMap.get(direction);
        return index != null ? index : 8;
    }

    private static void loadIcons() {
        Resources r = Application.get().getResources();
        mPx = r.getDimensionPixelSize(R.dimen.map_stop_shadow_size_6);
        mArrowWidthPx = mPx / 2f;
        mArrowHeightPx = mPx / 3f;
        float arrowSpacingReductionPx = mPx / 10f;
        mBuffer = mArrowHeightPx - arrowSpacingReductionPx;

        mPercentOffset = (mBuffer / (mPx + mBuffer)) * 0.5f;

        mArrowPaintStroke = new Paint();
        mArrowPaintStroke.setColor(Color.WHITE);
        mArrowPaintStroke.setStyle(Paint.Style.STROKE);
        mArrowPaintStroke.setStrokeWidth(1.0f);
        mArrowPaintStroke.setAntiAlias(true);

        String[] directions = {NORTH, NORTH_WEST, WEST, SOUTH_WEST, SOUTH, SOUTH_EAST, EAST, NORTH_EAST, NO_DIRECTION};
        for (int i = 0; i < directions.length; i++) {
            bus_stop_icons[i] = createBusStopIcon(directions[i], false);
            bus_stop_icons_focused[i] = createBusStopIcon(directions[i], true);
        }
        for (int i = 0; i < NUM_DIRECTIONS; i++) {
            Bitmap bmp = bus_stop_icons_focused[i];
            bus_stop_icons_focused[i] = Bitmap.createScaledBitmap(bmp,
                    (int) (bmp.getWidth() * FOCUS_ICON_SCALE),
                    (int) (bmp.getHeight() * FOCUS_ICON_SCALE), true);
        }
    }

    @SuppressWarnings("deprecation")
    private static Bitmap createBusStopIcon(String direction, boolean selected) {
        if (direction == null) {
            throw new IllegalArgumentException("direction is null");
        }

        Resources r = Application.get().getResources();
        Context context = Application.get();

        Float directionAngle = null;
        Bitmap bm;
        Canvas c;
        Drawable shape;
        Float rotationX = null, rotationY = null;

        Paint arrowPaintFill = new Paint();
        arrowPaintFill.setStyle(Paint.Style.FILL);
        arrowPaintFill.setAntiAlias(true);

        if (direction.equals(NO_DIRECTION)) {
            bm = Bitmap.createBitmap(mPx, mPx, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, bm.getWidth(), bm.getHeight());
        } else if (direction.equals(NORTH)) {
            directionAngle = 0f;
            bm = Bitmap.createBitmap(mPx, (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, (int) mBuffer, mPx, bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth() / 2, 0, bm.getWidth() / 2, mArrowHeightPx,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(NORTH_WEST)) {
            directionAngle = 315f;
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds((int) mBuffer, (int) mBuffer, bm.getWidth(), bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(0, 0, mBuffer, mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = mPx / 2f + mBuffer / 2f;
            rotationY = bm.getHeight() / 2f - mBuffer / 2f;
        } else if (direction.equals(WEST)) {
            directionAngle = 0f;
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), mPx, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds((int) mBuffer, 0, bm.getWidth(), bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(0, bm.getHeight() / 2, mArrowHeightPx, bm.getHeight() / 2,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getHeight() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(SOUTH_WEST)) {
            directionAngle = 225f;
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds((int) mBuffer, 0, bm.getWidth(), mPx);
            arrowPaintFill.setShader(
                    new LinearGradient(0, bm.getHeight(), mBuffer, bm.getHeight() - mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f - mBuffer / 4f;
            rotationY = mPx / 2f + mBuffer / 4f;
        } else if (direction.equals(SOUTH)) {
            directionAngle = 180f;
            bm = Bitmap.createBitmap(mPx, (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, bm.getWidth(), (int) (bm.getHeight() - mBuffer));
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth() / 2, bm.getHeight(), bm.getWidth() / 2,
                            bm.getHeight() - mArrowHeightPx,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(SOUTH_EAST)) {
            directionAngle = 135f;
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, mPx, mPx);
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), bm.getHeight(), bm.getWidth() - mBuffer,
                            bm.getHeight() - mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = (mPx + mBuffer / 2) / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(EAST)) {
            directionAngle = 180f;
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), mPx, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, mPx, bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), bm.getHeight() / 2,
                            bm.getWidth() - mArrowHeightPx, bm.getHeight() / 2,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(NORTH_EAST)) {
            directionAngle = 45f;
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, (int) mBuffer, mPx, bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), 0, bm.getWidth() - mBuffer, mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = (float) mPx / 2;
            rotationY = bm.getHeight() - (float) mPx / 2;
        } else {
            throw new IllegalArgumentException(direction);
        }

        shape.draw(c);

        if (direction.equals(NO_DIRECTION)) {
            return bm;
        }

        // Draw the direction arrow
        final float CUTOUT_HEIGHT = mPx / 12;
        Path path = new Path();
        float x1 = 0, y1 = 0;
        float x2 = 0, y2 = 0;
        float x3 = 0, y3 = 0;
        float x4 = 0, y4 = 0;

        if (direction.equals(NORTH) || direction.equals(SOUTH) ||
                direction.equals(NORTH_EAST) || direction.equals(SOUTH_EAST) ||
                direction.equals(NORTH_WEST) || direction.equals(SOUTH_WEST)) {
            x1 = mPx / 2;
            y1 = 0;
            x2 = (mPx / 2) - (mArrowWidthPx / 2);
            y2 = mArrowHeightPx;
            x3 = mPx / 2;
            y3 = mArrowHeightPx - CUTOUT_HEIGHT;
            x4 = (mPx / 2) + (mArrowWidthPx / 2);
            y4 = mArrowHeightPx;
        } else if (direction.equals(EAST) || direction.equals(WEST)) {
            x1 = 0;
            y1 = mPx / 2;
            x2 = mArrowHeightPx;
            y2 = (mPx / 2) - (mArrowWidthPx / 2);
            x3 = mArrowHeightPx - CUTOUT_HEIGHT;
            y3 = mPx / 2;
            x4 = mArrowHeightPx;
            y4 = (mPx / 2) + (mArrowWidthPx / 2);
        }

        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x4, y4);
        path.lineTo(x1, y1);
        path.close();

        Matrix matrix = new Matrix();
        matrix.postRotate(directionAngle, rotationX, rotationY);
        path.transform(matrix);

        c.drawPath(path, arrowPaintFill);
        c.drawPath(path, mArrowPaintStroke);

        return bm;
    }
}
