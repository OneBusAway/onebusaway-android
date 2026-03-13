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

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.CameraUpdateFactory;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;

import android.app.Activity;
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
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class StopOverlay implements MarkerListeners {

    private static final String TAG = "StopOverlay";

    private GoogleMap mMap;

    private MarkerData mMarkerData;

    private final Activity mActivity;

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
     * Icon cache keyed by route type, each containing a Bitmap array of size NUM_DIRECTIONS.
     * Replaces the old single-type bus_stop_icons array to support distinct icons per stop type.
     */
    private static final SparseArray<Bitmap[]> sStopIcons = new SparseArray<>();

    private static final SparseArray<Bitmap[]> sStopIconsFocused = new SparseArray<>();

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

    /**
     * Priority order for determining a stop's primary route type.
     * Rail/subway/tram/ferry are more visually important than bus at transit hubs.
     */
    private static final int[] ROUTE_TYPE_PRIORITY = {
            ObaRoute.TYPE_RAIL,
            ObaRoute.TYPE_SUBWAY,
            ObaRoute.TYPE_TRAM,
            ObaRoute.TYPE_FERRY,
            ObaRoute.TYPE_CABLECAR,
            ObaRoute.TYPE_GONDOLA,
            ObaRoute.TYPE_FUNICULAR
    };

    private static final float FOCUS_ICON_SCALE = 1.5f;

    /**
     * Scale factor for stop icons to make the vehicle glyph clearly visible inside the circle.
     * All stop types (bus, rail, subway, tram, ferry) display a glyph, matching iOS/Wayfinder.
     */
    private static final float GLYPH_ICON_SCALE = 1.35f;

    private static int mPx; // Bus stop icon size

    // Bus icon arrow attributes - by default assume we're not going to add a direction arrow
    private static float mArrowWidthPx = 0;

    private static float mArrowHeightPx = 0;

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

    OnFocusChangedListener mOnFocusChangedListener;

    @Override
    public boolean markerClicked(Marker marker) {
        long startTime = Long.MAX_VALUE, endTime = Long.MAX_VALUE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            startTime = SystemClock.elapsedRealtimeNanos();
        }

        ObaStop stop = mMarkerData.getStopFromMarker(marker);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            endTime = SystemClock.elapsedRealtimeNanos();
            Log.d(TAG, "Stop HashMap read time: " + TimeUnit.MILLISECONDS
                    .convert(endTime - startTime, TimeUnit.NANOSECONDS) + "ms");
        }

        if (stop == null) {
            // The marker isn't a stop that is contained in this StopOverlay - return unhandled
            return false;
        }

        if (BuildConfig.DEBUG) {
            // Show the stop_id in a toast for debug purposes
            Toast.makeText(mActivity, stop.getId(), Toast.LENGTH_SHORT).show();
        }

        doFocusChange(stop);

        float currentZoom = mMap.getCameraPosition().zoom;

        if (currentZoom < BaseMapFragment.CAMERA_DEFAULT_ZOOM) {
            mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                            marker.getPosition(),
                            BaseMapFragment.CAMERA_DEFAULT_ZOOM
                    )
            );
        } else {
            mMap.animateCamera(
                    CameraUpdateFactory.newLatLng(
                            marker.getPosition()
                    )
            );
        }

        return true;
    }

    @Override
    public void removeMarkerClicked(LatLng location) {
        Log.d(TAG, "Map clicked");
        removeFocus(location);
    }


    public interface OnFocusChangedListener {

        /**
         * Called when a stop on the map is clicked (i.e., tapped), which sets focus to a stop,
         * or when the user taps on an area away from the map for the first time after a stop
         * is already selected, which removes focus.  Clearly the focused stop can also be triggered
         * programmatically via a call to setFocus() with a stop of null - in that case, because
         * the user did not touch the map, location will be null.
         *
         * @param stop     the ObaStop that obtained focus, or null if no stop is in focus
         * @param routes   a HashMap of all route display names that serve this stop - key is
         *                 routeId
         * @param location the user touch location on the map, or null if the focus was changed
         *                 programmatically without the user tapping on the map
         */
        void onFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location);
    }

    public StopOverlay(Activity activity, GoogleMap map) {
        mActivity = activity;
        mMap = map;
        loadIcons();
    }

    public void setOnFocusChangeListener(OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }

    public synchronized void populateStops(List<ObaStop> stops, ObaReferences refs) {
        populate(stops, refs.getRoutes());
    }

    public synchronized void populateStops(List<ObaStop> stops, List<ObaRoute> routes) {
        populate(stops, routes);
    }

    private void populate(List<ObaStop> stops, List<ObaRoute> routes) {
        // Make sure that the MarkerData has been initialized
        setupMarkerData();
        mMarkerData.populate(stops, routes);
    }

    public synchronized int size() {
        if (mMarkerData != null) {
            return mMarkerData.size();
        } else {
            return 0;
        }
    }

    /**
     * Clears any stop markers from the map
     *
     * @param clearFocusedStop true to clear the currently focused stop, false to leave it on map
     */
    public synchronized void clear(boolean clearFocusedStop) {
        if (mMarkerData != null) {
            mMarkerData.clear(clearFocusedStop);
        }
    }

    /**
     * Cache the BitmapDescriptors that hold the images used for icons
     */
    private static final void loadIcons() {
        // Initialize variables used for all marker icons
        Resources r = Application.get().getResources();
        mPx = r.getDimensionPixelSize(R.dimen.map_stop_shadow_size_6);
        mArrowWidthPx = mPx / 2f; // half the stop icon size
        mArrowHeightPx = mPx / 3f; // 1/3 the stop icon size
        float arrowSpacingReductionPx = mPx / 10f;
        mBuffer = mArrowHeightPx - arrowSpacingReductionPx;

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
                icons[i] = createStopIcon(directions[i], false, routeType);
                iconsFocused[i] = createStopIcon(directions[i], true, routeType);
            }
            // Scale the focused icons to be larger than the normal icons
            for (int i = 0; i < NUM_DIRECTIONS; i++) {
                Bitmap bmp = iconsFocused[i];
                iconsFocused[i] = Bitmap.createScaledBitmap(bmp,
                        (int) (bmp.getWidth() * FOCUS_ICON_SCALE),
                        (int) (bmp.getHeight() * FOCUS_ICON_SCALE), true);
            }
            sStopIcons.put(routeType, icons);
            sStopIconsFocused.put(routeType, iconsFocused);
        }
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
    private static Bitmap createStopIcon(String direction, boolean selected, int routeType) {
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }

        Resources r = Application.get().getResources();
        Context context = Application.get();

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
        Bitmap raw;

        raw = BitmapFactory.decodeResource(r, R.drawable.ic_bus);
        sRouteTypeGlyphs.put(ObaRoute.TYPE_BUS,
                Bitmap.createScaledBitmap(raw, glyphSizePx, glyphSizePx, true));

        raw = BitmapFactory.decodeResource(r, R.drawable.ic_train);
        sRouteTypeGlyphs.put(ObaRoute.TYPE_RAIL,
                Bitmap.createScaledBitmap(raw, glyphSizePx, glyphSizePx, true));

        raw = BitmapFactory.decodeResource(r, R.drawable.ic_subway);
        sRouteTypeGlyphs.put(ObaRoute.TYPE_SUBWAY,
                Bitmap.createScaledBitmap(raw, glyphSizePx, glyphSizePx, true));

        raw = BitmapFactory.decodeResource(r, R.drawable.ic_tram);
        sRouteTypeGlyphs.put(ObaRoute.TYPE_TRAM,
                Bitmap.createScaledBitmap(raw, glyphSizePx, glyphSizePx, true));

        raw = BitmapFactory.decodeResource(r, R.drawable.ic_ferry);
        sRouteTypeGlyphs.put(ObaRoute.TYPE_FERRY,
                Bitmap.createScaledBitmap(raw, glyphSizePx, glyphSizePx, true));
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
     * Looks up a stop icon from the given cache based on direction and route type.
     * Falls back to TYPE_BUS if the requested type is not found, then to the default marker.
     *
     * @param cache     icon cache to look up from (normal or focused)
     * @param direction stop direction string
     * @param routeType one of ObaRoute.TYPE_* constants
     * @return BitmapDescriptor for the stop icon
     */
    @NonNull
    private static BitmapDescriptor lookupStopIcon(SparseArray<Bitmap[]> cache, String direction,
            int routeType) {
        int normalizedType = normalizeRouteType(routeType);
        Bitmap[] icons = cache.get(normalizedType);
        if (icons == null) {
            icons = cache.get(ObaRoute.TYPE_BUS);
        }
        if (icons == null) {
            Log.w(TAG, "Stop icons not initialized for type " + routeType);
            return BitmapDescriptorFactory.defaultMarker();
        }
        return BitmapDescriptorFactory.fromBitmap(icons[getDirectionIndex(direction)]);
    }

    private static BitmapDescriptor getStopBitmapDescriptor(String direction, int routeType) {
        return lookupStopIcon(sStopIcons, direction, routeType);
    }

    @NonNull
    private static BitmapDescriptor getFocusedStopBitmapDescriptor(String direction,
            int routeType) {
        return lookupStopIcon(sStopIconsFocused, direction, routeType);
    }

    /**
     * Returns the primary route type for a stop based on its serving routes.
     * Rail/subway/tram/ferry take priority over bus for visibility at transit hubs.
     *
     * @param stop   the stop to determine type for
     * @param routes map of routeId to ObaRoute from cached stop routes
     * @return one of the ObaRoute.TYPE_* constants
     */
    private static int getPrimaryRouteType(ObaStop stop, HashMap<String, ObaRoute> routes) {
        if (stop == null || routes == null) {
            return ObaRoute.TYPE_BUS;
        }
        String[] routeIds = stop.getRouteIds();
        if (routeIds == null || routeIds.length == 0) {
            return ObaRoute.TYPE_BUS;
        }

        Set<Integer> stopTypes = new HashSet<>();
        for (String routeId : routeIds) {
            ObaRoute route = routes.get(routeId);
            if (route != null) {
                stopTypes.add(route.getType());
            }
        }

        for (int type : ROUTE_TYPE_PRIORITY) {
            if (stopTypes.contains(type)) {
                return type;
            }
        }
        return ObaRoute.TYPE_BUS;
    }

    /**
     * Returns the currently focused stop, or null if no stop is in focus
     *
     * @return the currently focused stop, or null if no stop is in focus
     */
    public ObaStop getFocus() {
        if (mMarkerData != null) {
            return mMarkerData.getFocus();
        }

        return null;
    }

    /**
     * Sets focus to a particular stop, or pass in null for the stop to clear the focus
     *
     * @param stop   ObaStop to focus on, or null to clear the focus
     * @param routes a list of all route display names that serve this stop
     */
    public void setFocus(ObaStop stop, List<ObaRoute> routes) {
        // Make sure that the MarkerData has been initialized
        setupMarkerData();

        if (stop == null) {
            // Clear the focus
            removeFocus(null);
            return;
        }

        /**
         * If mMarkerData exists before this method is called, the stop reference passed into this
         * method might not match any existing stop reference in our HashMaps, since this stop came
         * from an external REST API call - is this a problem???
         *
         * If so, we'll need to keep another HashMap mapping stopIds to ObaStops so we can pull out
         * an internal reference to an ObaStop object that has the same stopId as the ObaStop object
         * passed into this method.  Then, we would use that internal reference in place of the
         * ObaStop passed into this method.  We don't want to maintain Yet Another HashMap for
         * memory/performance reasons if we don't have to.  For now, I think we can get away with
         * a separate reference that doesn't match the internal HashMaps, since we don't need to
         * match the references.
         */

        /**
         * Make sure that this stop is added to the overlay.  If an intent/orientation change started
         * the map fragment to focus on a stop, no markers may exist on the map
         */
        if (!mMarkerData.containsStop(stop)) {
            ArrayList<ObaStop> l = new ArrayList<ObaStop>();
            l.add(stop);
            populateStops(l, routes);
        }

        // Add the focus marker to the map by setting focus to this stop
        doFocusChange(stop);
    }


    private void doFocusChange(ObaStop stop) {
        mMarkerData.setFocus(stop);
        HashMap<String, ObaRoute> routes = mMarkerData.getCachedRoutes();

        // Notify listener
        mOnFocusChangedListener.onFocusChanged(stop, routes, stop.getLocation());
    }

    /**
     * Removes the stop focus and notify listener
     *
     * @param latLng the location on the map where the user tapped if the focus change was
     *               triggered
     *               by the user tapping on the map, or null if the focus change was otherwise
     *               triggered programmatically.
     */
    private void removeFocus(LatLng latLng) {
        if (mMarkerData.getFocus() != null) {
            mMarkerData.removeFocus();
        }

        // Set map clicked location, if it exists
        Location location = null;
        if (latLng != null) {
            location = MapHelpV2.makeLocation(latLng);
        }
        // Notify focus changed every time the map is clicked away from a stop marker
        mOnFocusChangedListener.onFocusChanged(null, null, location);
    }

    private void setupMarkerData() {
        if (mMarkerData == null) {
            mMarkerData = new MarkerData();
        }
    }

    /**
     * Data structures to track what stops/markers are currently shown on the map
     */
    class MarkerData {

        /**
         * Stops-for-location REST API endpoint returns 100 markers per call by default
         * (see http://goo.gl/tzvrLb), so we'll support showing max results of around 2 calls
         * before
         * we completely clear the map and start over.  Note that this is a fuzzy max, since we
         * don't
         * want to clear the overlay in the middle of processing an API response and remove markers
         * in
         * the current view
         */
        private static final int FUZZY_MAX_MARKER_COUNT = 200;

        /**
         * A cached set of markers currently shown on the map, up to roughly
         * FUZZY_MAX_MARKER_COUNT in size.  This is needed to add/remove markers from the map.
         * StopId is the key.
         */
        private HashMap<String, Marker> mStopMarkers;

        /**
         * A cached set of ObaStops that are currently shown on the map, up to roughly
         * FUZZY_MAX_MARKER_COUNT in size.  Since onMarkerClick() provides a marker, we need a
         * mapping of that marker to the ObaStop.
         * Marker that represents an ObaStop is the key.
         */
        private HashMap<Marker, ObaStop> mStops;

        /**
         * A cached set of ObaRoutes that serve the currently cached ObaStops.  This is
         * needed to retrieve the route display names that serve a particular stop.
         * RouteId is the key.
         */
        private HashMap<String, ObaRoute> mStopRoutes;

        /**
         * Marker and stop used to indicate which bus stop has focus (i.e., was last
         * clicked/tapped). The marker reference points to the stop marker itself.
         */
        private Marker mCurrentFocusMarker;

        private ObaStop mCurrentFocusStop;

        /**
         * Keep a copy of ObaRoute references for stops have have had focus, so we can reconstruct
         * the mStopRoutes HashMap after clearing the cache
         */
        private List<ObaRoute> mFocusedRoutes;

        /**
         * Tracks the resolved primary route type for each stop, keyed by stopId.
         * Used by setFocus/removeFocus to restore the correct type-specific icon.
         */
        private HashMap<String, Integer> mStopRouteTypes;

        MarkerData() {
            mStopMarkers = new HashMap<String, Marker>();
            mStops = new HashMap<Marker, ObaStop>();
            mStopRoutes = new HashMap<String, ObaRoute>();
            mFocusedRoutes = new LinkedList<ObaRoute>();
            mStopRouteTypes = new HashMap<String, Integer>();
        }

        synchronized void populate(List<ObaStop> stops, List<ObaRoute> routes) {
            int count = 0;

            if (mStopMarkers.size() >= FUZZY_MAX_MARKER_COUNT) {
                // We've exceed our max, so clear the current marker cache and start over
                Log.d(TAG, "Exceed max marker cache of " + FUZZY_MAX_MARKER_COUNT
                        + ", clearing cache");
                removeMarkersFromMap();
                mStopMarkers.clear();
                mStops.clear();

                // Make sure the currently focused stop still exists on the map
                if (mCurrentFocusStop != null && mFocusedRoutes != null) {
                    addMarkerToMap(mCurrentFocusStop, mFocusedRoutes);
                    count++;
                }
            }

            for (ObaStop stop : stops) {
                if (!mStopMarkers.containsKey(stop.getId())) {
                    addMarkerToMap(stop, routes);
                    count++;
                }
            }

            Log.d(TAG, "Added " + count + " markers, total markers = " + mStopMarkers.size());
        }

        /**
         * Places a marker on the map for this stop, and adds it to our marker HashMap
         *
         * @param stop   ObaStop that should be shown on the map
         * @param routes A list of ObaRoutes that serve this stop
         */
        private synchronized void addMarkerToMap(ObaStop stop, List<ObaRoute> routes) {
            // Cache route data first so getPrimaryRouteType can use mStopRoutes
            for (ObaRoute route : routes) {
                if (!mStopRoutes.containsKey(route.getId())) {
                    mStopRoutes.put(route.getId(), route);
                }
            }

            // Resolve and store the primary route type for this stop
            int routeType = getPrimaryRouteType(stop, mStopRoutes);
            mStopRouteTypes.put(stop.getId(), routeType);

            // Determine icon within synchronized block to prevent race condition with focus changes
            BitmapDescriptor icon = getStopBitmapDescriptor(stop.getDirection(), routeType);
            if (mCurrentFocusStop != null && stop.getId().equals(mCurrentFocusStop.getId())) {
                icon = getFocusedStopBitmapDescriptor(stop.getDirection(), routeType);
            }

            Marker m = mMap.addMarker(new MarkerOptions()
                    .position(MapHelpV2.makeLatLng(stop.getLocation()))
                    .icon(icon)
                    .flat(true)
                    .anchor(getXPercentOffsetForDirection(stop.getDirection()),
                            getYPercentOffsetForDirection(stop.getDirection()))
            );
            mStopMarkers.put(stop.getId(), m);
            mStops.put(m, stop);
        }

        synchronized ObaStop getStopFromMarker(Marker marker) {
            return mStops.get(marker);
        }

        /**
         * Returns true if this overlay contains the provided ObaStop
         *
         * @param stop ObaStop to check for
         * @return true if this overlay contains the provided ObaStop, false if it does not
         */
        synchronized boolean containsStop(ObaStop stop) {
            if (stop != null) {
                return containsStop(stop.getId());
            } else {
                return false;
            }
        }

        /**
         * Returns true if this overlay contains the provided stopId
         *
         * @param stopId stopId to check for
         * @return true if this overlay contains the provided stopId, false if it does not
         */
        synchronized boolean containsStop(String stopId) {
            if (mStopMarkers != null) {
                return mStopMarkers.containsKey(stopId);
            } else {
                return false;
            }
        }

        /**
         * Gets the ObaRoute objects that have been cached
         *
         * @return a copy of the HashMap containing the ObaRoutes that have been cached, with the
         * routeId as key
         */
        synchronized HashMap<String, ObaRoute> getCachedRoutes() {
            return new HashMap<String, ObaRoute>(mStopRoutes);
        }

        /**
         * Restores the unfocused icon for the currently focused stop marker.
         * Called by setFocus() and removeFocus() to avoid duplicating the restore logic.
         */
        private void restoreUnfocusedIcon() {
            if (mCurrentFocusMarker == null || mCurrentFocusStop == null) {
                return;
            }
            Marker currentMarker = mStopMarkers.get(mCurrentFocusStop.getId());
            if (currentMarker != null) {
                Integer prevType = mStopRouteTypes.get(mCurrentFocusStop.getId());
                int routeType = (prevType != null) ? prevType : ObaRoute.TYPE_BUS;
                currentMarker.setIcon(getStopBitmapDescriptor(
                        mCurrentFocusStop.getDirection(), routeType));
            }
        }

        /**
         * Sets the current focus to a particular stop
         *
         * @param stop ObaStop that should have focus
         */
        synchronized void setFocus(ObaStop stop) {
            if (stop == null) {
                removeFocus();
                return;
            }

            restoreUnfocusedIcon();
            mCurrentFocusStop = stop;
            mCurrentFocusMarker = mStopMarkers.get(stop.getId());

            // Check if the marker exists in our cache before proceeding
            if (mCurrentFocusMarker == null) {
                mCurrentFocusStop = null;
                return;
            }

            // Save a copy of ObaRoute references for this stop, so we have them when clearing cache
            mFocusedRoutes.clear();
            String[] routeIds = stop.getRouteIds();
            for (int i = 0; i < routeIds.length; i++) {
                ObaRoute route = mStopRoutes.get(routeIds[i]);
                if (route != null) {
                    mFocusedRoutes.add(route);
                }
            }

            // Set focused icon with correct route type
            Integer focusType = mStopRouteTypes.get(stop.getId());
            int focusRouteType = (focusType != null) ? focusType : ObaRoute.TYPE_BUS;
            mCurrentFocusMarker.setIcon(
                    getFocusedStopBitmapDescriptor(stop.getDirection(), focusRouteType));
        }

        /**
         * Give the marker a slight bounce effect
         *
         * @param marker marker to animate
         */
        private void animateMarker(final Marker marker) {
            final Handler handler = new Handler();

            final long startTime = SystemClock.uptimeMillis();
            final long duration = 300; // ms

            Projection proj = mMap.getProjection();
            final LatLng markerLatLng = marker.getPosition();
            Point startPoint = proj.toScreenLocation(markerLatLng);
            startPoint.offset(0, -10);
            final LatLng startLatLng = proj.fromScreenLocation(startPoint);

            final Interpolator interpolator = new BounceInterpolator();

            handler.post(new Runnable() {
                @Override
                public void run() {
                    long elapsed = SystemClock.uptimeMillis() - startTime;
                    float t = interpolator.getInterpolation((float) elapsed / duration);
                    double lng = t * markerLatLng.longitude + (1 - t) * startLatLng.longitude;
                    double lat = t * markerLatLng.latitude + (1 - t) * startLatLng.latitude;
                    marker.setPosition(new LatLng(lat, lng));

                    if (t < 1.0) {
                        // Post again 16ms later (60fps)
                        handler.postDelayed(this, 16);
                    }
                }
            });
        }

        /**
         * Returns the last focused stop, or null if no stop is in focus
         *
         * @return last focused stop, or null if no stop is in focus
         */
        ObaStop getFocus() {
            return mCurrentFocusStop;
        }

        /**
         * Remove focus of a stop on the map
         */
        synchronized void removeFocus() {
            restoreUnfocusedIcon();
            if (mCurrentFocusMarker != null) {
                mCurrentFocusMarker = null;
            }
            mFocusedRoutes.clear();
            mCurrentFocusStop = null;
        }

        private void removeMarkersFromMap() {
            for (Map.Entry<String, Marker> entry : mStopMarkers.entrySet()) {
                entry.getValue().remove();
            }
        }

        /**
         * Clears any stop markers from the map
         *
         * @param clearFocusedStop true to clear the currently focused stop, false to leave it on map
         */
        synchronized void clear(boolean clearFocusedStop) {
            if (mStopMarkers != null) {
                // Clear all markers from the map
                removeMarkersFromMap();

                // Clear the data structures
                mStopMarkers.clear();
            }
            if (mStops != null) {
                mStops.clear();
            }
            if (mStopRoutes != null) {
                mStopRoutes.clear();
            }
            if (mStopRouteTypes != null) {
                mStopRouteTypes.clear();
            }
            if (clearFocusedStop) {
                removeFocus();
            } else {
                // Make sure the currently focused stop still exists on the map
                if (mCurrentFocusStop != null && mFocusedRoutes != null) {
                    addMarkerToMap(mCurrentFocusStop, mFocusedRoutes);
                }
            }
        }

        synchronized int size() {
            return mStopMarkers.size();
        }
    }

//    @Override
//    public boolean onTrackballEvent(MotionEvent event, MapView view) {
//        final int action = event.getAction();
//        OverlayItem next = null;
//        //Log.d(TAG, "MotionEvent: " + event);
//
//        if (action == MotionEvent.ACTION_MOVE) {
//            final float xDiff = event.getX();
//            final float yDiff = event.getY();
//            // Up
//            if (yDiff <= -1) {
//                next = findNext(getFocus(), true, true);
//            }
//            // Down
//            else if (yDiff >= 1) {
//                next = findNext(getFocus(), true, false);
//            }
//            // Right
//            else if (xDiff >= 1) {
//                next = findNext(getFocus(), false, true);
//            }
//            // Left
//            else if (xDiff <= -1) {
//                next = findNext(getFocus(), false, false);
//            }
//            if (next != null) {
//                setFocus(next);
//                view.postInvalidate();
//            }
//        } else if (action == MotionEvent.ACTION_UP) {
//            final OverlayItem focus = getFocus();
//            if (focus != null) {
//                ArrivalsListActivity.start(mActivity, ((StopOverlayItem) focus).getStop());
//            }
//        }
//        return true;
//    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event, MapView view) {
//        //Log.d(TAG, "KeyEvent: " + event);
//        OverlayItem next = null;
//        switch (keyCode) {
//            case KeyEvent.KEYCODE_DPAD_UP:
//                next = findNext(getFocus(), true, true);
//                break;
//            case KeyEvent.KEYCODE_DPAD_DOWN:
//                next = findNext(getFocus(), true, false);
//                break;
//            case KeyEvent.KEYCODE_DPAD_RIGHT:
//                next = findNext(getFocus(), false, true);
//                break;
//            case KeyEvent.KEYCODE_DPAD_LEFT:
//                next = findNext(getFocus(), false, false);
//                break;
//            case KeyEvent.KEYCODE_DPAD_CENTER:
//                final OverlayItem focus = getFocus();
//                if (focus != null) {
//                    ArrivalsListActivity.start(mActivity, ((StopOverlayItem) focus).getStop());
//                }
//                break;
//            default:
//                return false;
//        }
//        if (next != null) {
//            setFocus(next);
//            view.postInvalidate();
//        }
//        return true;
//    }

//    boolean setFocusById(String id) {
//        final int size = size();
//        for (int i = 0; i < size; ++i) {
//            StopOverlayItem item = (StopOverlayItem) getItem(i);
//            if (id.equals(item.getStop().getId())) {
//                setFocus(item);
//                return true;
//            }
//        }
//        return false;
//    }
//
//    String getFocusedId() {
//        final OverlayItem focus = getFocus();
//        if (focus != null) {
//            return ((StopOverlayItem) focus).getStop().getId();
//        }
//        return null;
//    }

//    @Override
//    protected boolean onTap(int index) {
//        final OverlayItem item = getItem(index);
//        if (item.equals(getFocus())) {
//            ObaStop stop = mStops.get(index);
//            ArrivalsListActivity.start(mActivity, stop);
//        } else {
//            setFocus(item);
//            // fix odd behavior where previously selected item is not re-highlighted
//            setLastFocusedIndex(-1);
//        }
//        return true;
//    }

    // The find next routines find the closest item along the specified axis.

//    OverlayItem findNext(OverlayItem initial, boolean lat, boolean positive) {
//        if (initial == null) {
//            return null;
//        }
//        final int size = size();
//        final GeoPoint initialPoint = initial.getPoint();
//        OverlayItem min = initial;
//        int minDist = Integer.MAX_VALUE;
//
//        for (int i = 0; i < size; ++i) {
//            OverlayItem item = getItem(i);
//            GeoPoint point = item.getPoint();
//            final int distX = point.getLongitudeE6() - initialPoint.getLongitudeE6();
//            final int distY = point.getLatitudeE6() - initialPoint.getLatitudeE6();
//
//            // We have to eliminate anything that's going in the wrong direction,
//            // or doesn't change in the correct axis (including the initial point)
//            if (lat) {
//                if (positive) {
//                    // Distance must be positive.
//                    if (distY <= 0) {
//                        continue;
//                    }
//                }
//                // Distance must to be negative.
//                else if (distY >= 0) {
//                    continue;
//                }
//            } else {
//                if (positive) {
//                    // Distance must be positive
//                    if (distX <= 0) {
//                        continue;
//                    }
//                }
//                // Distance must be negative
//                else if (distX >= 0) {
//                    continue;
//                }
//            }
//
//            final int distSq = distX * distX + distY * distY;
//
//            if (distSq < minDist) {
//                min = item;
//                minDist = distSq;
//            }
//        }
//        return min;
//    }
}
