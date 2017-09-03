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

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    private static final int NUM_DIRECTIONS = 9; // 8 directions + undirected mStops

    private static Bitmap[] bus_stop_icons = new Bitmap[NUM_DIRECTIONS];

    private static int mPx; // Bus stop icon size

    // Bus icon arrow attributes - by default assume we're not going to add a direction arrow
    private static float mArrowWidthPx = 0;

    private static float mArrowHeightPx = 0;

    private static float mBuffer = 0;  // Add this to the icon size to get the Bitmap size

    private static float mPercentOffset = 0.5f;
    // % offset to position the stop icon, so the selection marker hits the middle of the circle

    private static Paint mArrowPaintStroke;
    // Stroke color used for outline of directional arrows on stops

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

        // Report Stop distance metric
        Location stopLocation = stop.getLocation();
        Location myLocation = Application.getLastKnownLocation(mActivity, null);
        // Track the users distance to bus stop
        ObaAnalytics.trackBusStopDistance(stop.getId(), myLocation, stopLocation);
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

        bus_stop_icons[0] = createBusStopIcon(NORTH);
        bus_stop_icons[1] = createBusStopIcon(NORTH_WEST);
        bus_stop_icons[2] = createBusStopIcon(WEST);
        bus_stop_icons[3] = createBusStopIcon(SOUTH_WEST);
        bus_stop_icons[4] = createBusStopIcon(SOUTH);
        bus_stop_icons[5] = createBusStopIcon(SOUTH_EAST);
        bus_stop_icons[6] = createBusStopIcon(EAST);
        bus_stop_icons[7] = createBusStopIcon(NORTH_EAST);
        bus_stop_icons[8] = createBusStopIcon(NO_DIRECTION);
    }

    /**
     * Creates a bus stop icon with the given direction arrow, or without a direction arrow if
     * the direction is NO_DIRECTION
     *
     * @param direction Bus stop direction, obtained from ObaStop.getDirection() and defined in
     *                  constants in this class, or NO_DIRECTION if the stop icon shouldn't have a
     *                  direction arrow
     * @return a bus stop icon bitmap with the arrow pointing the given direction, or with no arrow
     * if direction is NO_DIRECTION
     */
    private static Bitmap createBusStopIcon(String direction) throws NullPointerException {
        if (direction == null) {
            throw new IllegalArgumentException(direction);
        }

        Resources r = Application.get().getResources();
        Context context = Application.get();

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
            bm = Bitmap.createBitmap(mPx, mPx, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds(0, 0, bm.getWidth(), bm.getHeight());
        } else if (direction.equals(NORTH)) {
            directionAngle = 0f;
            bm = Bitmap.createBitmap(mPx, (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds(0, (int) mBuffer, mPx, bm.getHeight());
            // Shade with darkest color at tip of arrow
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth() / 2, 0, bm.getWidth() / 2, mArrowHeightPx,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // For NORTH, no rotation occurs - use center of image anyway so we have some value
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(NORTH_WEST)) {
            directionAngle = 315f;  // Arrow is drawn N, rotate 315 degrees
            bm = Bitmap.createBitmap((int) (mPx + mBuffer),
                    (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds((int) mBuffer, (int) mBuffer, bm.getWidth(), bm.getHeight());
            // Shade with darkest color at tip of arrow
            arrowPaintFill.setShader(
                    new LinearGradient(0, 0, mBuffer, mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around below coordinates (trial and error)
            rotationX = mPx / 2f + mBuffer / 2f;
            rotationY = bm.getHeight() / 2f - mBuffer / 2f;
        } else if (direction.equals(WEST)) {
            directionAngle = 0f;  // Arrow is drawn pointing West, so no rotation
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), mPx, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds((int) mBuffer, 0, bm.getWidth(), bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(0, bm.getHeight() / 2, mArrowHeightPx, bm.getHeight() / 2,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // For WEST
            rotationX = bm.getHeight() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(SOUTH_WEST)) {
            directionAngle = 225f;  // Arrow is drawn N, rotate 225 degrees
            bm = Bitmap.createBitmap((int) (mPx + mBuffer),
                    (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds((int) mBuffer, 0, bm.getWidth(), mPx);
            arrowPaintFill.setShader(
                    new LinearGradient(0, bm.getHeight(), mBuffer, bm.getHeight() - mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around below coordinates (trial and error)
            rotationX = bm.getWidth() / 2f - mBuffer / 4f;
            rotationY = mPx / 2f + mBuffer / 4f;
        } else if (direction.equals(SOUTH)) {
            directionAngle = 180f;  // Arrow is drawn N, rotate 180 degrees
            bm = Bitmap.createBitmap(mPx, (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds(0, 0, bm.getWidth(), (int) (bm.getHeight() - mBuffer));
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth() / 2, bm.getHeight(), bm.getWidth() / 2,
                            bm.getHeight() - mArrowHeightPx,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(SOUTH_EAST)) {
            directionAngle = 135f;  // Arrow is drawn N, rotate 135 degrees
            bm = Bitmap.createBitmap((int) (mPx + mBuffer),
                    (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds(0, 0, mPx, mPx);
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), bm.getHeight(), bm.getWidth() - mBuffer,
                            bm.getHeight() - mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around below coordinates (trial and error)
            rotationX = (mPx + mBuffer / 2) / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(EAST)) {
            directionAngle = 180f;  // Arrow is drawn pointing West, so rotate 180
            bm = Bitmap.createBitmap((int) (mPx + mBuffer), mPx, Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds(0, 0, mPx, bm.getHeight());
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), bm.getHeight() / 2,
                            bm.getWidth() - mArrowHeightPx, bm.getHeight() / 2,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            rotationX = bm.getWidth() / 2f;
            rotationY = bm.getHeight() / 2f;
        } else if (direction.equals(NORTH_EAST)) {
            directionAngle = 45f;  // Arrow is drawn pointing N, so rotate 45 degrees
            bm = Bitmap.createBitmap((int) (mPx + mBuffer),
                    (int) (mPx + mBuffer), Bitmap.Config.ARGB_8888);
            c = new Canvas(bm);
            shape = ContextCompat.getDrawable(context, R.drawable.map_stop_icon);
            shape.setBounds(0, (int) mBuffer, mPx, bm.getHeight());
            // Shade with darkest color at tip of arrow
            arrowPaintFill.setShader(
                    new LinearGradient(bm.getWidth(), 0, bm.getWidth() - mBuffer, mBuffer,
                            r.getColor(R.color.theme_primary), r.getColor(R.color.theme_accent),
                            Shader.TileMode.MIRROR));
            // Rotate around middle of circle
            rotationX = (float) mPx / 2;
            rotationY = bm.getHeight() - (float) mPx / 2;
        } else {
            throw new IllegalArgumentException(direction);
        }

        shape.draw(c);

        if (direction.equals(NO_DIRECTION)) {
            // Everything after this point is for drawing the arrow image, so return the bitmap as-is for no arrow
            return bm;
        }

        /**
         * Draw the arrow - all dimensions should be relative to px so the arrow is drawn the same
         * size for all orientations
         */
        // Height of the cutout in the bottom of the triangle that makes it an arrow (0=triangle)
        final float CUTOUT_HEIGHT = mPx / 12;
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
            x1 = mPx / 2;
            y1 = 0;

            // lower left
            x2 = (mPx / 2) - (mArrowWidthPx / 2);
            y2 = mArrowHeightPx;

            // cutout in arrow bottom
            x3 = mPx / 2;
            y3 = mArrowHeightPx - CUTOUT_HEIGHT;

            // lower right
            x4 = (mPx / 2) + (mArrowWidthPx / 2);
            y4 = mArrowHeightPx;
        } else if (direction.equals(EAST) || direction.equals(WEST)) {
            // Arrow is drawn pointing WEST
            // Tip of arrow
            x1 = 0;
            y1 = mPx / 2;

            // lower left
            x2 = mArrowHeightPx;
            y2 = (mPx / 2) - (mArrowWidthPx / 2);

            // cutout in arrow bottom
            x3 = mArrowHeightPx - CUTOUT_HEIGHT;
            y3 = mPx / 2;

            // lower right
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

        // Rotate arrow around (rotationX, rotationY) point
        Matrix matrix = new Matrix();
        matrix.postRotate(directionAngle, rotationX, rotationY);
        path.transform(matrix);

        c.drawPath(path, arrowPaintFill);
        c.drawPath(path, mArrowPaintStroke);

        return bm;
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
     * Returns the BitMapDescriptor for a particular bus stop icon, based on the stop direction
     *
     * @param direction Bus stop direction, obtained from ObaStop.getDirection() and defined in
     *                  constants in this class
     * @return BitmapDescriptor for the bus stop icon that should be used for that direction
     */
    private static BitmapDescriptor getBitmapDescriptorForBusStopDirection(String direction) {
        if (direction.equals(NORTH)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[0]);
        } else if (direction.equals(NORTH_WEST)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[1]);
        } else if (direction.equals(WEST)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[2]);
        } else if (direction.equals(SOUTH_WEST)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[3]);
        } else if (direction.equals(SOUTH)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[4]);
        } else if (direction.equals(SOUTH_EAST)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[5]);
        } else if (direction.equals(EAST)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[6]);
        } else if (direction.equals(NORTH_EAST)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[7]);
        } else if (direction.equals(NO_DIRECTION)) {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[8]);
        } else {
            return BitmapDescriptorFactory.fromBitmap(bus_stop_icons[8]);
        }
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
         * clicked/tapped)
         */
        private Marker mCurrentFocusMarker;

        private ObaStop mCurrentFocusStop;

        /**
         * Keep a copy of ObaRoute references for stops have have had focus, so we can reconstruct
         * the mStopRoutes HashMap after clearing the cache
         */
        private List<ObaRoute> mFocusedRoutes;

        MarkerData() {
            mStopMarkers = new HashMap<String, Marker>();
            mStops = new HashMap<Marker, ObaStop>();
            mStopRoutes = new HashMap<String, ObaRoute>();
            mFocusedRoutes = new LinkedList<ObaRoute>();
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
        private void addMarkerToMap(ObaStop stop, List<ObaRoute> routes) {
            Marker m = mMap.addMarker(new MarkerOptions()
                            .position(MapHelpV2.makeLatLng(stop.getLocation()))
                            .icon(getBitmapDescriptorForBusStopDirection(stop.getDirection()))
                            .flat(true)
                            .anchor(getXPercentOffsetForDirection(stop.getDirection()),
                                    getYPercentOffsetForDirection(stop.getDirection()))
            );
            mStopMarkers.put(stop.getId(), m);
            mStops.put(m, stop);
            for (ObaRoute route : routes) {
                // ObaRoutes may have already been added for other stops, so check before adding
                if (!mStopRoutes.containsKey(route.getId())) {
                    mStopRoutes.put(route.getId(), route);
                }
            }
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
         * Sets the current focus to a particular stop
         *
         * @param stop ObaStop that should have focus
         */
        void setFocus(ObaStop stop) {
            if (mCurrentFocusMarker != null) {
                // Remove the current focus marker from map
                mCurrentFocusMarker.remove();
            }
            mCurrentFocusStop = stop;

            // Save a copy of ObaRoute references for this stop, so we have them when clearing cache
            mFocusedRoutes.clear();
            String[] routeIds = stop.getRouteIds();
            for (int i = 0; i < routeIds.length; i++) {
                ObaRoute route = mStopRoutes.get(routeIds[i]);
                if (route != null) {
                    mFocusedRoutes.add(route);
                }
            }

            // Reduce focus marker latitude by small amount to ensure it is always on top of the
            // corresponding stop marker (i.e., so its not identical to stop marker latitude)
            LatLng latLng = new LatLng(stop.getLatitude() - 0.000001, stop.getLongitude());

            mCurrentFocusMarker = mMap.addMarker(new MarkerOptions()
                            .position(latLng)
            );

            // TODO - This doesn't look good since when bouncing, the focus marker is drawn behind
            // the bus stop marker.  Maybe fix with new z-order property?
            // animateMarker(mCurrentFocusMarker);
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
        void removeFocus() {
            if (mCurrentFocusMarker != null) {
                // Remove the current focus marker from map
                mCurrentFocusMarker.remove();
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
