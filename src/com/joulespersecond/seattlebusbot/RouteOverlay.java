/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.oba.request.ObaResponse;
import com.joulespersecond.oba.request.ObaStopsForRouteResponse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Point;
// import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class RouteOverlay {
    @SuppressWarnings("unused")
    private static final String TAG = "RouteOverlay";

    protected final MapView mMapView;
    protected LineOverlay mLineOverlay;
    protected String mRouteId;
    protected String mZoomToRouteId;
    protected ObaRoute mRoute;

    private static int mLineOverlayColor;

    public RouteOverlay(Context context, MapView mapView) {
        mMapView = mapView;
        mLineOverlayColor = context.getResources().getColor(R.color.route_overlay_line);
    }

    public String getRouteId() {
        return mRouteId;
    }

    public void setRouteId(String routeId, boolean willZoom) {
        mRouteId = routeId;
        if (willZoom)
            mZoomToRouteId = routeId;
    }

    public ObaRoute getRoute() {
        return mRoute;
    }

    /*
     * Update the route once the new response has been obtained
     */
    public void completeShowRoute(ObaStopsForRouteResponse response) {
        List<Overlay> overlays = mMapView.getOverlays();
        if (mLineOverlay != null)
            overlays.remove(mLineOverlay);
        if (response != null) {
            String routeId = response.getRouteId();
            mLineOverlay = new LineOverlay();

            mLineOverlay.addLines(mLineOverlayColor, response.getShapes());
            overlays.add(mLineOverlay);

            // wait to zoom till we have the right response
            if (mZoomToRouteId != null && mZoomToRouteId.equals(routeId)) {
                // route zoom only once
                mZoomToRouteId = null;
                mLineOverlay.zoom(mMapView.getController());
            }
            mRoute = response.getRoute(routeId);
        }

        // TODO: are the polylines in "stopGroups" just subsets of the polylines in "entry"?
        // Is this still needed (other than having multi-color lines)?
        // What about detour situations (e.g., snow routes)?

        /*
        // Get all the stop groupings
        ObaArray<ObaStopGrouping> stopGroupings = response.getData().getStopGroupings();
        // For each stop grouping, get
        int color = 0;
        final int numGroupings = stopGroupings.length();
        for (int i=0; i < numGroupings; ++i) {
            final ObaArray<ObaStopGroup> groups = stopGroupings.get(i).getStopGroups();
            final int numGroups = groups.length();
            for (int j=0; j < numGroups; ++j) {
                final ObaArray<ObaPolyline> lines = groups.get(j).getPolylines();
                final int numLines = lines.length();
                for (int k=0; k < numLines; ++k) {
                    overlay.addLine(mColors[color], lines.get(k));
                    color = (color+1)%mColors.length;
                }
            }
        }
        */
    }

    /*
     * Remove the route and the overlay
     */
    public void clearRoute() {
        // hide route
        mRouteId = null;
        mRoute = null;
        if (mLineOverlay != null) {
            mMapView.getOverlays().remove(mLineOverlay);
            mLineOverlay = null;
        }
        mMapView.invalidate();
    }

    /*
     * Returns true if overlay is in route mode,
     * with the selected route drawn and only its
     * stops shown
     */
    public boolean isRouteMode() {
        return getRouteId() != null;
    }

    /*
     * Returns true if response is of type ObaStopsForRouteResponse
     * which is appropriate for route mode
     */
    protected static boolean isStopsForRouteResponse(ObaResponse response) {
        return (response instanceof ObaStopsForRouteResponse);
    }

    /*
     * Compare to current route
     */
    protected boolean isCurrentRoute(String routeCompare) {
        if (mRouteId == null)
            return false;
        return mRouteId.equals(routeCompare);
    }

    public static class LineOverlay extends Overlay {

        public static final class Line {
            private final List<GeoPoint> mPoints;
            private final Paint mPaint;

            public Line(int color, List<GeoPoint> points) {
                mPoints = points;
                mPaint = new Paint();
                mPaint.setColor(color);
                mPaint.setStrokeWidth(5);
                mPaint.setStrokeCap(Cap.ROUND);
                mPaint.setStrokeJoin(Join.ROUND);
            }
            public List<GeoPoint> getPoints() {
                return mPoints;
            }
            public Paint getPaint() {
                return mPaint;
            }
        }

        private ArrayList<Line> mLines = new ArrayList<Line>();

        public void addLine(int color, List<GeoPoint> points) {
            mLines.add(new Line(color, points));
            // TODO: Invalidate
        }

        public void addLine(int color, ObaShape line) {
            List<GeoPoint> points = line.getPoints();
            mLines.add(new Line(color, points));
            // TODO: Invalidate
        }
        public void addLines(int color, ObaShape[] lines) {
            final int len = lines.length;
            for (int i=0; i < len; ++i) {
                addLine(color, lines[i]);
            }
        }
        public void setLines(int color, ObaShape[] lines) {
            mLines.clear();
            addLines(color, lines);
        }

        public void clearLines() {
            mLines.clear();
        }

        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            if (shadow) {
                super.draw(canvas, mapView, shadow);
                return;
            }
            final Projection projection = mapView.getProjection();
            Point pt = new Point();

            // Convert points to coords and then call drawLines()
            // TODO: This is probably too slow to do within draw() --
            // spawn off another thread to generate the path?
            final int len = mLines.size();
            // Log.d(TAG, String.format("Drawing %d line(s)", len));

            for (int i=0; i < len; ++i) {
                final Line line = mLines.get(i);
                final List<GeoPoint> geoPoints = line.getPoints();
                final int numPts = geoPoints.size();

                // drawLines() draws lines as:
                //		(x0,y0) -> (x1,y1)
                //		(x2,y2) -> (x3,y3)
                // The polyline encodes lines as:
                //		(x0,y0) -> (x1,y1)
                //		(x1,y1)	-> (x2,y2)
                // So we need to double each point after the first.
                float points[] = new float[numPts*4-1];

                projection.toPixels(geoPoints.get(0), pt);
                points[0] = pt.x;
                points[1] = pt.y;

                int j=1;
                for (; j < (numPts-1); ++j) {
                    projection.toPixels(geoPoints.get(j), pt);
                    points[j*4-2] = pt.x;
                    points[j*4-1] = pt.y;
                    points[j*4+0] = pt.x;
                    points[j*4+1] = pt.y;
                }
                projection.toPixels(geoPoints.get(j), pt);
                points[j*4-2] = pt.x;
                points[j*4-1] = pt.y;
                assert((j*4-1) == points.length);

                canvas.drawLines(points, line.getPaint());
            }
        }

        public void zoom(MapController mapCtrl) {
            if (mapCtrl == null)
                return;

            int minLat = Integer.MAX_VALUE;
            int maxLat = Integer.MIN_VALUE;
            int minLon = Integer.MAX_VALUE;
            int maxLon = Integer.MIN_VALUE;

            for (Line line : mLines) {
                for (GeoPoint item : line.mPoints) {
                    int lat = (int)(item.getLatitudeE6());
                    int lon = (int)(item.getLongitudeE6());

                    maxLat = Math.max(lat, maxLat);
                    minLat = Math.min(lat, minLat);
                    maxLon = Math.max(lon, maxLon);
                    minLon = Math.min(lon, minLon);
                }
            }

            mapCtrl.zoomToSpan(Math.abs(maxLat - minLat),
                    Math.abs(maxLon - minLon));
            mapCtrl.animateTo(new GeoPoint((maxLat + minLat) / 2,
                    (maxLon + minLon) / 2));
        }
    }
}