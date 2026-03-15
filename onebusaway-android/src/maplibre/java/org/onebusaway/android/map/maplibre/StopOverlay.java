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

import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;

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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class StopOverlay {

    private static final String TAG = "StopOverlay";

    private final MapLibreMap mMap;

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

    OnFocusChangedListener mOnFocusChangedListener;

    public interface OnFocusChangedListener {
        void onFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location);
    }

    public StopOverlay(Activity activity, MapLibreMap map) {
        mActivity = activity;
        mMap = map;
        loadIcons();
    }

    public void setOnFocusChangeListener(OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }

    /**
     * Called when a marker is clicked. Returns true if this overlay handled the click.
     */
    public boolean markerClicked(Marker marker) {
        long startTime = SystemClock.elapsedRealtimeNanos();

        ObaStop stop = mMarkerData.getStopFromMarker(marker);

        long endTime = SystemClock.elapsedRealtimeNanos();
        Log.d(TAG, "Stop HashMap read time: " + TimeUnit.MILLISECONDS
                .convert(endTime - startTime, TimeUnit.NANOSECONDS) + "ms");

        if (stop == null) {
            return false;
        }

        if (BuildConfig.DEBUG) {
            Toast.makeText(mActivity, stop.getId(), Toast.LENGTH_SHORT).show();
        }

        doFocusChange(stop);

        float currentZoom = (float) mMap.getCameraPosition().zoom;

        if (currentZoom < MapLibreMapFragment.CAMERA_DEFAULT_ZOOM) {
            mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                            marker.getPosition(),
                            MapLibreMapFragment.CAMERA_DEFAULT_ZOOM
                    )
            );
        } else {
            mMap.animateCamera(
                    CameraUpdateFactory.newLatLng(marker.getPosition())
            );
        }

        return true;
    }

    /**
     * Called when the map is clicked (not on a marker). Removes focus.
     */
    public void removeMarkerClicked(LatLng location) {
        Log.d(TAG, "Map clicked");
        removeFocus(location);
    }

    public synchronized void populateStops(List<ObaStop> stops, ObaReferences refs) {
        populate(stops, refs.getRoutes());
    }

    public synchronized void populateStops(List<ObaStop> stops, List<ObaRoute> routes) {
        populate(stops, routes);
    }

    private void populate(List<ObaStop> stops, List<ObaRoute> routes) {
        setupMarkerData();
        mMarkerData.populate(stops, routes);
    }

    public synchronized int size() {
        if (mMarkerData != null) {
            return mMarkerData.size();
        }
        return 0;
    }

    public synchronized void clear(boolean clearFocusedStop) {
        if (mMarkerData != null) {
            mMarkerData.clear(clearFocusedStop);
        }
    }

    // ============================================================================================
    // Icon rendering (pure Android Canvas/Path/Paint — identical to Google flavor)
    // ============================================================================================

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

    private static float getXPercentOffsetForDirection(String direction) {
        if (direction.equals(NORTH_WEST) || direction.equals(WEST) || direction.equals(SOUTH_WEST)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(SOUTH_EAST) || direction.equals(EAST) || direction.equals(NORTH_EAST)) {
            return 0.5f - mPercentOffset;
        }
        return 0.5f;
    }

    private static float getYPercentOffsetForDirection(String direction) {
        if (direction.equals(NORTH) || direction.equals(NORTH_WEST) || direction.equals(NORTH_EAST)) {
            return 0.5f + mPercentOffset;
        } else if (direction.equals(SOUTH_WEST) || direction.equals(SOUTH) || direction.equals(SOUTH_EAST)) {
            return 0.5f - mPercentOffset;
        }
        return 0.5f;
    }

    private Icon getIconForBusStopDirection(String direction) {
        Integer index = directionToIndexMap.get(direction);
        if (index == null) {
            index = 8;
        }
        return IconFactory.getInstance(mActivity).fromBitmap(bus_stop_icons[index]);
    }

    @NonNull
    private Icon getFocusedIconForBusStopDirection(String direction) {
        Integer index = directionToIndexMap.get(direction);
        if (index == null) {
            index = 8;
        }
        return IconFactory.getInstance(mActivity).fromBitmap(bus_stop_icons_focused[index]);
    }

    public ObaStop getFocus() {
        if (mMarkerData != null) {
            return mMarkerData.getFocus();
        }
        return null;
    }

    public void setFocus(ObaStop stop, List<ObaRoute> routes) {
        setupMarkerData();

        if (stop == null) {
            removeFocus(null);
            return;
        }

        if (!mMarkerData.containsStop(stop)) {
            ArrayList<ObaStop> l = new ArrayList<>();
            l.add(stop);
            populateStops(l, routes);
        }

        doFocusChange(stop);
    }

    private void doFocusChange(ObaStop stop) {
        mMarkerData.setFocus(stop);
        if (mOnFocusChangedListener != null) {
            HashMap<String, ObaRoute> routes = mMarkerData.getCachedRoutes();
            mOnFocusChangedListener.onFocusChanged(stop, routes, stop.getLocation());
        }
    }

    private void removeFocus(LatLng latLng) {
        if (mMarkerData != null && mMarkerData.getFocus() != null) {
            mMarkerData.removeFocus();
        }
        Location location = (latLng != null) ? MapHelpMapLibre.makeLocation(latLng) : null;
        if (mOnFocusChangedListener != null) {
            mOnFocusChangedListener.onFocusChanged(null, null, location);
        }
    }

    private void setupMarkerData() {
        if (mMarkerData == null) {
            mMarkerData = new MarkerData();
        }
    }

    // ============================================================================================
    // MarkerData inner class — tracks stops/markers on the map
    // ============================================================================================

    class MarkerData {

        private static final int FUZZY_MAX_MARKER_COUNT = 200;

        private HashMap<String, Marker> mStopMarkers;
        private HashMap<Marker, ObaStop> mStops;
        private HashMap<String, ObaRoute> mStopRoutes;
        private Marker mCurrentFocusMarker;
        private ObaStop mCurrentFocusStop;
        private List<ObaRoute> mFocusedRoutes;

        MarkerData() {
            mStopMarkers = new HashMap<>();
            mStops = new HashMap<>();
            mStopRoutes = new HashMap<>();
            mFocusedRoutes = new LinkedList<>();
        }

        synchronized void populate(List<ObaStop> stops, List<ObaRoute> routes) {
            int count = 0;

            if (mStopMarkers.size() >= FUZZY_MAX_MARKER_COUNT) {
                Log.d(TAG, "Exceed max marker cache of " + FUZZY_MAX_MARKER_COUNT
                        + ", clearing cache");
                removeMarkersFromMap();
                mStopMarkers.clear();
                mStops.clear();

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

        private synchronized void addMarkerToMap(ObaStop stop, List<ObaRoute> routes) {
            Icon icon = getIconForBusStopDirection(stop.getDirection());
            if (mCurrentFocusStop != null && stop.getId().equals(mCurrentFocusStop.getId())) {
                icon = getFocusedIconForBusStopDirection(stop.getDirection());
            }

            Marker m = mMap.addMarker(new MarkerOptions()
                    .position(MapHelpMapLibre.makeLatLng(stop.getLocation()))
                    .icon(icon)
            );
            mStopMarkers.put(stop.getId(), m);
            mStops.put(m, stop);
            for (ObaRoute route : routes) {
                if (!mStopRoutes.containsKey(route.getId())) {
                    mStopRoutes.put(route.getId(), route);
                }
            }
        }

        synchronized ObaStop getStopFromMarker(Marker marker) {
            return mStops.get(marker);
        }

        synchronized boolean containsStop(ObaStop stop) {
            if (stop != null) {
                return mStopMarkers.containsKey(stop.getId());
            }
            return false;
        }

        synchronized HashMap<String, ObaRoute> getCachedRoutes() {
            return new HashMap<>(mStopRoutes);
        }

        synchronized void setFocus(ObaStop stop) {
            if (stop == null) {
                removeFocus();
                return;
            }

            if (mCurrentFocusMarker != null && mCurrentFocusStop != null) {
                Marker currentMarker = mStopMarkers.get(mCurrentFocusStop.getId());
                if (currentMarker != null) {
                    currentMarker.setIcon(getIconForBusStopDirection(
                            mCurrentFocusStop.getDirection()));
                }
            }
            mCurrentFocusStop = stop;
            mCurrentFocusMarker = mStopMarkers.get(stop.getId());

            if (mCurrentFocusMarker == null) {
                mCurrentFocusStop = null;
                return;
            }

            mFocusedRoutes.clear();
            String[] routeIds = stop.getRouteIds();
            for (String routeId : routeIds) {
                ObaRoute route = mStopRoutes.get(routeId);
                if (route != null) {
                    mFocusedRoutes.add(route);
                }
            }

            mCurrentFocusMarker.setIcon(
                    getFocusedIconForBusStopDirection(stop.getDirection()));
        }

        ObaStop getFocus() {
            return mCurrentFocusStop;
        }

        synchronized void removeFocus() {
            if (mCurrentFocusMarker != null && mCurrentFocusStop != null) {
                Marker currentMarker = mStopMarkers.get(mCurrentFocusStop.getId());
                if (currentMarker != null) {
                    currentMarker.setIcon(getIconForBusStopDirection(
                            mCurrentFocusStop.getDirection()));
                }
                mCurrentFocusMarker = null;
            }
            mFocusedRoutes.clear();
            mCurrentFocusStop = null;
        }

        private void removeMarkersFromMap() {
            for (Map.Entry<String, Marker> entry : mStopMarkers.entrySet()) {
                mMap.removeAnnotation(entry.getValue());
            }
        }

        synchronized void clear(boolean clearFocusedStop) {
            if (mStopMarkers != null) {
                removeMarkersFromMap();
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
                if (mCurrentFocusStop != null && mFocusedRoutes != null) {
                    addMarkerToMap(mCurrentFocusStop, mFocusedRoutes);
                }
            }
        }

        synchronized int size() {
            return mStopMarkers.size();
        }
    }
}
