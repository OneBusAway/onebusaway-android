/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import org.onebusaway.android.R;
import org.onebusaway.android.map.render.StopBitmaps;
import org.onebusaway.android.models.ObaRoute;

import java.util.HashMap;
import java.util.Map;

/**
 * Stateless factory for bus-stop marker icons, extracted from the old StopOverlay. It holds the
 * pre-rendered direction/route-type bitmap caches (normal + focused) and the anchor offsets; the
 * declarative ObaMapContent renderer asks it for a {@link BitmapDescriptor} + anchor per stop while
 * the imperative marker bookkeeping (MarkerData, focus, clicks) goes away. Icons are built once on
 * first use.
 */
public final class StopIconFactory {

    private StopIconFactory() {
    }

    private static final String TAG = "StopIconFactory";

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

    private static final int NUM_DIRECTIONS = 9; // 8 directions + undirected mStops

    /**
     * Descriptor cache keyed by route type, each a BitmapDescriptor array of size NUM_DIRECTIONS. The
     * descriptors (native texture wrappers) are built once so re-iconing markers — e.g. swapping every
     * stop full icon ⇄ dot on a zoom-band crossing, up to the full stop cache at once (default 200, up
     * to 2000) — reuses them instead of minting a fresh texture per marker (the cost that made the
     * transition stutter).
     */
    private static final SparseArray<BitmapDescriptor[]> sStopDescriptors = new SparseArray<>();

    /** Focused (selected) variant of {@link #sStopDescriptors}. */
    private static final SparseArray<BitmapDescriptor[]> sStopDescriptorsFocused = new SparseArray<>();

    /** The small directionless dot shown in place of the full icon at distant zoom (declutter). */
    private static BitmapDescriptor sDotDescriptor;

    /**
     * The focused variant of {@link #sDotDescriptor}: a larger ({@link StopBitmaps#FOCUSED_DOT_SCALE})
     * accent dot, so the selected stop stays visible and clearly larger than its neighbours far out.
     */
    private static BitmapDescriptor sDotDescriptorFocused;

    /**
     * Route types that get distinct stop icons on the map.
     * TYPE_CABLECAR uses TYPE_TRAM icon; TYPE_GONDOLA/TYPE_FUNICULAR fall back to TYPE_BUS.
     */
    private static final int[] ICON_ROUTE_TYPES = {
            ObaRoute.TYPE_BUS,
            ObaRoute.TYPE_RAIL,
            ObaRoute.TYPE_SUBWAY,
            ObaRoute.TYPE_TRAM,
            ObaRoute.TYPE_FERRY
    };

    // (The primary-route-type priority that used to live here is now the pure primaryRouteType() in
    // src/main, called by GoogleMapHost when it builds StopMarkers.)

    private static final float FOCUS_ICON_SCALE = 1.5f;

    /**
     * Scale factor for stop icons to make the vehicle glyph clearly visible inside the circle.
     * All stop types (bus, rail, subway, tram, ferry) display a glyph, matching iOS/Wayfinder.
     */
    private static final float GLYPH_ICON_SCALE = 1.35f;

    private static int mPx; // Bus stop icon size

    private static float mBuffer = 0;  // Add this to the icon size to get the Bitmap size

    private static float mPercentOffset = 0.5f;
    // % offset to position the stop icon, so the selection marker hits the middle of the circle

    private static Paint mArrowPaintStroke;
    // Stroke color used for outline of directional arrows on stops

    /**
     * Cached route type glyph bitmaps (ic_train, ic_tram, etc.) keyed by ObaRoute.TYPE_* constant.
     * Loaded once during loadIcons() and drawn inside the stop circle for all stop types.
     */
    private static final SparseArray<Bitmap> sRouteTypeGlyphs = new SparseArray<>();

    private static final Paint sGlyphPaint = new Paint();

    static {
        sGlyphPaint.setAntiAlias(true);
        sGlyphPaint.setFilterBitmap(true);
        sGlyphPaint.setColorFilter(
                new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
    }

    /**
     * Builds the icon caches on first use (the old constructor called loadIcons() each time). The
     * [context] is used only to read app resources while rendering the bitmaps; only the first call
     * (which populates the caches) touches it.
     */
    public static synchronized void ensureLoaded(Context context) {
        if (!sLoaded) {
            loadIcons(context);
            sLoaded = true;
        }
    }

    /** The normal (unfocused) stop icon for a direction + primary route type. */
    public static synchronized BitmapDescriptor stopIcon(Context context, String direction, int routeType) {
        ensureLoaded(context);
        return getStopBitmapDescriptor(direction, routeType);
    }

    /** The focused (1.5x) stop icon for a direction + primary route type. */
    public static synchronized BitmapDescriptor focusedStopIcon(Context context, String direction, int routeType) {
        ensureLoaded(context);
        return getFocusedStopBitmapDescriptor(direction, routeType);
    }

    /**
     * The small dot shown in place of the full icon at distant zoom. Directionless and route-type
     * agnostic (a neutral themed point), so the caller anchors it at the marker center (0.5, 0.5).
     */
    public static synchronized BitmapDescriptor dotStopIcon(Context context) {
        ensureLoaded(context);
        return sDotDescriptor;
    }

    /** The focused (accent) dot, shown for the selected stop at distant zoom. */
    public static synchronized BitmapDescriptor focusedDotStopIcon(Context context) {
        ensureLoaded(context);
        return sDotDescriptorFocused;
    }

    /** Marker anchor X for the given direction (positions the pin tip on the circle center). */
    public static float anchorX(String direction) {
        return getXPercentOffsetForDirection(direction);
    }

    /** Marker anchor Y for the given direction. */
    public static float anchorY(String direction) {
        return getYPercentOffsetForDirection(direction);
    }

    /**
     * Cache the BitmapDescriptors that hold the images used for icons
     */
    private static final void loadIcons(Context context) {
        // Initialize variables used for all marker icons
        Resources r = context.getResources();
        mPx = r.getDimensionPixelSize(R.dimen.map_stop_shadow_size_6);
        float arrowHeightPx = mPx / 3f;
        float arrowSpacingReductionPx = mPx / 10f;
        mBuffer = arrowHeightPx - arrowSpacingReductionPx;

        // Set offset used to position the image for markers (see getX/YPercentOffsetForDirection())
        // This allows the current selection marker to land on the middle of the stop marker circle
        mPercentOffset = (mBuffer / (mPx + mBuffer)) * 0.5f;

        mArrowPaintStroke = new Paint();
        mArrowPaintStroke.setColor(Color.WHITE);
        mArrowPaintStroke.setStyle(Paint.Style.STROKE);
        mArrowPaintStroke.setStrokeWidth(1.0f);
        mArrowPaintStroke.setAntiAlias(true);

        // Pre-scale route type glyph icons to the target circle size
        int px = (int) (mPx * GLYPH_ICON_SCALE);
        int glyphSizePx = (int) (px * 0.70f);
        loadRouteTypeGlyphs(r, glyphSizePx);

        String[] directions = {NORTH, NORTH_WEST, WEST, SOUTH_WEST, SOUTH, SOUTH_EAST, EAST,
                NORTH_EAST, NO_DIRECTION};

        for (int routeType : ICON_ROUTE_TYPES) {
            Bitmap[] icons = new Bitmap[NUM_DIRECTIONS];
            Bitmap[] iconsFocused = new Bitmap[NUM_DIRECTIONS];
            for (int i = 0; i < directions.length; i++) {
                icons[i] = createStopIcon(context, directions[i], false, routeType);
                iconsFocused[i] = createStopIcon(context, directions[i], true, routeType);
            }
            // Scale the focused icons to be larger than the normal icons
            for (int i = 0; i < NUM_DIRECTIONS; i++) {
                Bitmap bmp = iconsFocused[i];
                iconsFocused[i] = Bitmap.createScaledBitmap(bmp,
                        (int) (bmp.getWidth() * FOCUS_ICON_SCALE),
                        (int) (bmp.getHeight() * FOCUS_ICON_SCALE), true);
            }
            sStopDescriptors.put(routeType, toDescriptors(icons));
            sStopDescriptorsFocused.put(routeType, toDescriptors(iconsFocused));
        }

        sDotDescriptor = BitmapDescriptorFactory.fromBitmap(
                StopBitmaps.dot(mPx, r.getColor(R.color.theme_primary)));
        sDotDescriptorFocused = BitmapDescriptorFactory.fromBitmap(
                StopBitmaps.dot(mPx, r.getColor(R.color.map_stop_focus),
                        StopBitmaps.FOCUSED_DOT_SCALE));
    }

    /** Wraps each pre-rendered bitmap into a BitmapDescriptor once, so callers can reuse them. */
    private static BitmapDescriptor[] toDescriptors(Bitmap[] bitmaps) {
        BitmapDescriptor[] descriptors = new BitmapDescriptor[bitmaps.length];
        for (int i = 0; i < bitmaps.length; i++) {
            descriptors[i] = BitmapDescriptorFactory.fromBitmap(bitmaps[i]);
        }
        return descriptors;
    }

    /**
     * Creates a stop icon with the given direction arrow and route type symbol.
     *
     * @param direction Bus stop direction, obtained from ObaStop.getDirection() and defined in
     *                  constants in this class, or NO_DIRECTION if the stop icon shouldn't have a
     *                  direction arrow
     * @param selected  true to use the selected icon style, false for normal icon style
     * @param routeType one of ObaRoute.TYPE_* constants indicating the stop's primary route type
     * @return a stop icon bitmap with the arrow pointing the given direction, or with no arrow
     * if direction is NO_DIRECTION
     */
    private static Bitmap createStopIcon(Context context, String direction, boolean selected, int routeType) {
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }

        Resources r = context.getResources();

        // All stops get a slightly larger circle so the vehicle glyph is clearly visible
        int px = (int) (mPx * GLYPH_ICON_SCALE);
        float arrowWidthPx = px / 2f;
        float arrowHeightPx = px / 3f;
        float arrowSpacingReductionPx = px / 10f;
        float buffer = arrowHeightPx - arrowSpacingReductionPx;

        Float directionAngle = null;  // 0-360 degrees
        Bitmap bm;
        Canvas c;
        Drawable shape;
        Float rotationX = null, rotationY = null;  // Point around which to rotate the arrow

        Paint arrowPaintFill = new Paint();
        arrowPaintFill.setStyle(Paint.Style.FILL);
        arrowPaintFill.setAntiAlias(true);

        if (direction.equals(NO_DIRECTION)) {
            // Don't draw the arrow
            bm = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, bm.getWidth(), bm.getHeight());
        } else if (direction.equals(NORTH)) {
            directionAngle = 0f;
            bm = Bitmap.createBitmap(px, (int) (px + buffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, (int) buffer, px, bm.getHeight());
            // Shade with darkest color at tip of arrow
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth() / 2, 0, bm.getWidth() / 2, arrowHeightPx,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // For NORTH, no rotation occurs - use center of image anyway so we have some value
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(NORTH_WEST)) {
            directionAngle = 315f;  // Arrow is drawn N, rotate 315 degrees
            bm = Bitmap.createBitmap((int) (px + buffer),
                    (int) (px + buffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds((int) buffer, (int) buffer, bm.getWidth(), bm.getHeight());
            // Shade with darkest color at tip of arrow
            arrowPaintFill.setShader(
                    new LinearGradient(0, 0, buffer, buffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around below coordinates (trial and error)
            rotationX = px / 2f + buffer / 2f;
            rotationY = bm.getHeight() / 2f - buffer / 2f;
        } else if (direction.equals(WEST)) {
            directionAngle = 0f;  // Arrow is drawn pointing West, so no rotation
            bm = Bitmap.createBitmap((int) (px + buffer), px, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds((int) buffer, 0, bm.getWidth(), bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(0, bm.getHeight() / 2, arrowHeightPx, bm.getHeight() / 2,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // For WEST
            rotationX = bm.getHeight() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(SOUTH_WEST)) {
            directionAngle = 225f;  // Arrow is drawn N, rotate 225 degrees
            bm = Bitmap.createBitmap((int) (px + buffer),
                    (int) (px + buffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds((int) buffer, 0, bm.getWidth(), px);
            arrowPaintFill.setShader(
                    new LinearGradient(0, bm.getHeight(), buffer, bm.getHeight() - buffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around below coordinates (trial and error)
            rotationX = bm.getWidth() / 2f - buffer / 4f;
            rotationY = px / 2f + buffer / 4f;
        } else if (direction.equals(SOUTH)) {
            directionAngle = 180f;  // Arrow is drawn N, rotate 180 degrees
            bm = Bitmap.createBitmap(px, (int) (px + buffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, bm.getWidth(), (int) (bm.getHeight() - buffer));
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth() / 2, bm.getHeight(), bm.getWidth() / 2,
                            bm.getHeight() - arrowHeightPx,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(SOUTH_EAST)) {
            directionAngle = 135f;  // Arrow is drawn N, rotate 135 degrees
            bm = Bitmap.createBitmap((int) (px + buffer),
                    (int) (px + buffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, px, px);
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), bm.getHeight(), bm.getWidth() - buffer,
                            bm.getHeight() - buffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around below coordinates (trial and error)
            rotationX = (px + buffer / 2) / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(EAST)) {
            directionAngle = 180f;  // Arrow is drawn pointing West, so rotate 180
            bm = Bitmap.createBitmap((int) (px + buffer), px, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, 0, px, bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), bm.getHeight() / 2,
                            bm.getWidth() - arrowHeightPx, bm.getHeight() / 2,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(NORTH_EAST)) {
            directionAngle = 45f;  // Arrow is drawn pointing N, so rotate 45 degrees
            bm = Bitmap.createBitmap((int) (px + buffer),
                    (int) (px + buffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, selected ? R.drawable.selected_map_stop_icon : R.drawable.map_stop_icon);
            shape.setBounds(0, (int) buffer, px, bm.getHeight());
            // Shade with darkest color at tip of arrow
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), 0, bm.getWidth() - buffer, buffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around middle of circle
            rotationX = (float) px / 2;
            rotationY = bm.getHeight() - (float) px / 2;
        } else {
            throw new IllegalArgumentException(direction);
        }

        shape.draw(c);

        if (direction.equals(NO_DIRECTION)) {
            // Draw route type symbol on the circle, then return (no arrow for this direction)
            drawRouteTypeSymbol(c, shape.getBounds(), routeType);
            return bm;
        }

        /**
         * Draw the arrow - all dimensions should be relative to px so the arrow is drawn the same
         * size for all orientations
         */
        // Height of the cutout in the bottom of the triangle that makes it an arrow (0=triangle)
        final float CUTOUT_HEIGHT = px / 12f;
        Path path = new Path();
        float x1 = 0, y1 = 0;  // Tip of arrow
        float x2 = 0, y2 = 0;  // lower left
        float x3 = 0, y3 = 0; // cutout in arrow bottom
        float x4 = 0, y4 = 0; // lower right

        if (direction.equals(NORTH) || direction.equals(SOUTH) ||
                direction.equals(NORTH_EAST) || direction.equals(SOUTH_EAST) ||
                direction.equals(NORTH_WEST) || direction.equals(SOUTH_WEST)) {
            // Arrow is drawn pointing NORTH
            // Tip of arrow
            x1 = px / 2f;
            y1 = 0;

            // lower left
            x2 = (px / 2f) - (arrowWidthPx / 2);
            y2 = arrowHeightPx;

            // cutout in arrow bottom
            x3 = px / 2f;
            y3 = arrowHeightPx - CUTOUT_HEIGHT;

            // lower right
            x4 = (px / 2f) + (arrowWidthPx / 2);
            y4 = arrowHeightPx;
        } else if (direction.equals(EAST) || direction.equals(WEST)) {
            // Arrow is drawn pointing WEST
            // Tip of arrow
            x1 = 0;
            y1 = px / 2f;

            // lower left
            x2 = arrowHeightPx;
            y2 = (px / 2f) - (arrowWidthPx / 2);

            // cutout in arrow bottom
            x3 = arrowHeightPx - CUTOUT_HEIGHT;
            y3 = px / 2f;

            // lower right
            x4 = arrowHeightPx;
            y4 = (px / 2f) + (arrowWidthPx / 2);
        }

        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.lineTo(x4, y4);
        path.lineTo(x1, y1);
        path.close();

        // Rotate arrow around (rotationX, rotationY) point
        Matrix matrix = new Matrix();
        matrix.postRotate(directionAngle, rotationX, rotationY);
        path.transform(matrix);

        c.drawPath(path, arrowPaintFill);
        c.drawPath(path, mArrowPaintStroke);

        // Draw route type symbol on the circle after the arrow
        drawRouteTypeSymbol(c, shape.getBounds(), routeType);

        return bm;
    }

    /**
     * Loads and pre-scales route type glyph bitmaps (ic_train, ic_tram, etc.) into the cache.
     * These are the same icons used in TripDetailsListFragment for route type display.
     * Pre-scaling avoids repeated Bitmap.createScaledBitmap() calls during icon generation.
     *
     * @param r           Resources to load drawables from
     * @param glyphSizePx target glyph size in pixels (already scaled for circle size)
     */
    private static void loadRouteTypeGlyphs(Resources r, int glyphSizePx) {
        int[][] glyphMapping = {
                {ObaRoute.TYPE_BUS, R.drawable.ic_bus},
                {ObaRoute.TYPE_RAIL, R.drawable.ic_train},
                {ObaRoute.TYPE_SUBWAY, R.drawable.ic_subway},
                {ObaRoute.TYPE_TRAM, R.drawable.ic_tram},
                {ObaRoute.TYPE_FERRY, R.drawable.ic_ferry},
        };
        for (int[] entry : glyphMapping) {
            Bitmap raw = BitmapFactory.decodeResource(r, entry[1]);
            sRouteTypeGlyphs.put(entry[0],
                    Bitmap.createScaledBitmap(raw, glyphSizePx, glyphSizePx, true));
        }
    }

    /**
     * Draws the pre-scaled route type glyph icon in the center of the stop icon circle.
     * Uses the existing ic_bus, ic_train, ic_tram, ic_subway, ic_ferry PNG assets — the same
     * icons used in TripDetailsListFragment and matching the iOS/Wayfinder transport glyphs.
     *
     * @param canvas       the canvas to draw on
     * @param circleBounds the bounds of the circle drawable
     * @param routeType    one of ObaRoute.TYPE_* constants
     */
    private static void drawRouteTypeSymbol(Canvas canvas, Rect circleBounds, int routeType) {
        int normalizedType = normalizeRouteType(routeType);
        Bitmap glyph = sRouteTypeGlyphs.get(normalizedType);
        if (glyph == null) {
            Log.w(TAG, "No glyph loaded for route type " + routeType);
            return;
        }

        float cx = circleBounds.centerX();
        float cy = circleBounds.centerY();

        canvas.drawBitmap(glyph,
                cx - glyph.getWidth() / 2f,
                cy - glyph.getHeight() / 2f,
                sGlyphPaint);
    }

    /**
     * Gets the % X offset used for the bus stop icon, for the given direction
     *
     * @param direction Bus stop direction, obtained from ObaStop.getDirection() and defined in
     *                  constants in this class
     * @return percent offset X for the bus stop icon that should be used for that direction
     */
    private static float getXPercentOffsetForDirection(String direction) {
        if (direction.equals(NORTH)) {
            // Middle of icon
            return 0.5f;
        } else if (direction.equals(NORTH_WEST)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(WEST)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(SOUTH_WEST)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(SOUTH)) {
            // Middle of icon
            return 0.5f;
        } else if (direction.equals(SOUTH_EAST)) {
            return 0.5f - mPercentOffset;
        } else if (direction.equals(EAST)) {
            return 0.5f - mPercentOffset;
        } else if (direction.equals(NORTH_EAST)) {
            return 0.5f - mPercentOffset;
        } else if (direction.equals(NO_DIRECTION)) {
            // Middle of icon
            return 0.5f;
        } else {
            // Assume middle of icon
            return 0.5f;
        }
    }

    /**
     * Gets the % Y offset used for the bus stop icon, for the given direction
     *
     * @param direction Bus stop direction, obtained from ObaStop.getDirection() and defined in
     *                  constants in this class
     * @return percent offset Y for the bus stop icon that should be used for that direction
     */
    private static float getYPercentOffsetForDirection(String direction) {
        if (direction.equals(NORTH)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(NORTH_WEST)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(WEST)) {
            // Middle of icon
            return 0.5f;
        } else if (direction.equals(SOUTH_WEST)) {
            return 0.5f - mPercentOffset;
        } else if (direction.equals(SOUTH)) {
            return 0.5f - mPercentOffset;
        } else if (direction.equals(SOUTH_EAST)) {
            return 0.5f - mPercentOffset;
        } else if (direction.equals(EAST)) {
            // Middle of icon
            return 0.5f;
        } else if (direction.equals(NORTH_EAST)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(NO_DIRECTION)) {
            // Middle of icon
            return 0.5f;
        } else {
            // Assume middle of icon
            return 0.5f;
        }
    }

    /**
     * Returns the direction index for the given direction string
     *
     * @param direction Bus stop direction string
     * @return index into the direction arrays (0-8), defaults to 8 (NO_DIRECTION) for unknown
     */
    private static int getDirectionIndex(String direction) {
        Integer index = directionToIndexMap.get(direction);
        if (index == null) {
            index = 8;
        }
        return index;
    }

    /**
     * Normalizes a route type to one that has a distinct icon.
     * TYPE_CABLECAR maps to TYPE_TRAM; unsupported types fall back to TYPE_BUS.
     *
     * @param routeType one of ObaRoute.TYPE_* constants
     * @return the normalized route type that has a pre-rendered icon set
     */
    private static int normalizeRouteType(int routeType) {
        switch (routeType) {
            case ObaRoute.TYPE_RAIL:
            case ObaRoute.TYPE_SUBWAY:
            case ObaRoute.TYPE_TRAM:
            case ObaRoute.TYPE_FERRY:
                return routeType;
            case ObaRoute.TYPE_CABLECAR:
                return ObaRoute.TYPE_TRAM;
            default:
                return ObaRoute.TYPE_BUS;
        }
    }

    /**
     * Looks up a cached stop descriptor by direction and route type.
     * Falls back to TYPE_BUS if the requested type is not found, then to the default marker.
     *
     * @param cache     descriptor cache to look up from (normal or focused)
     * @param direction stop direction string
     * @param routeType one of ObaRoute.TYPE_* constants
     * @return BitmapDescriptor for the stop icon
     */
    @NonNull
    private static BitmapDescriptor lookupStopIcon(SparseArray<BitmapDescriptor[]> cache,
            String direction, int routeType) {
        int normalizedType = normalizeRouteType(routeType);
        BitmapDescriptor[] icons = cache.get(normalizedType);
        if (icons == null) {
            icons = cache.get(ObaRoute.TYPE_BUS);
        }
        if (icons == null) {
            Log.w(TAG, "Stop icons not initialized for type " + routeType);
            return BitmapDescriptorFactory.defaultMarker();
        }
        return icons[getDirectionIndex(direction)];
    }

    private static BitmapDescriptor getStopBitmapDescriptor(String direction, int routeType) {
        return lookupStopIcon(sStopDescriptors, direction, routeType);
    }

    @NonNull
    private static BitmapDescriptor getFocusedStopBitmapDescriptor(String direction,
            int routeType) {
        return lookupStopIcon(sStopDescriptorsFocused, direction, routeType);
    }
}
