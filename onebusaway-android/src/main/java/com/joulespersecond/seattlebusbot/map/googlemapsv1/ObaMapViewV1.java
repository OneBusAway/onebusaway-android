package com.joulespersecond.seattlebusbot.map.googlemapsv1;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.location.Location;
import android.os.Build;
import android.view.View;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.seattlebusbot.map.MapModeController;

import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper around MapView for Google Maps API v1, to abstract the dependency on the Google APIs
 */
public class ObaMapViewV1 extends com.google.android.maps.MapView
        implements MapModeController.ObaMapView {

    private LineOverlay mLineOverlay;

    // We have to convert from GeoPoint to Location, so hold references to both
    private GeoPoint mCenter;
    private Location mCenterLocation;

    public ObaMapViewV1(Context context, String apiKey) {
        super(context, apiKey);
    }

    @Override
    public void setZoom(float zoomLevel) {
        MapController mapCtrl = getController();
        mapCtrl.setZoom((int) zoomLevel);
    }

    @Override
    public Location getMapCenterAsLocation() {
        // If the center is the same as the last call to this method, pass back the same Location
        // object
        if (mCenter == null || mCenter != getMapCenter()) {
            mCenter = getMapCenter();
            mCenterLocation = MapHelp.makeLocation(mCenter);
        }

        return mCenterLocation;
    }

    @Override
    public void setMapCenter(Location location) {
        MapController mapCtrl = getController();
        mapCtrl.setCenter(MapHelp.makeGeoPoint(location));
    }

    @Override
    public double getLatitudeSpanInDecDegrees() {
        return super.getLatitudeSpan() / 1E6;
    }

    @Override
    public double getLongitudeSpanInDecDegrees() {
        return super.getLongitudeSpan() / 1E6;
    }

    @Override
    public float getZoomLevelAsFloat() {
        return super.getZoomLevel();
    }

    //
    // See this bug: http://code.google.com/p/android/issues/detail?id=24023
    // Large paths and HW acceleration don't mix, so we can disable it
    // only when this overlay is visible.
    //
    @TargetApi(11)
    private void enableHWAccel(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            int type = enable ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_SOFTWARE;
            setLayerType(type, null);
        }
    }

    @Override
    public void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes) {
        List<Overlay> overlays = getOverlays();

        if (mLineOverlay == null) {
            enableHWAccel(false);
            mLineOverlay = new LineOverlay();
            overlays.add(mLineOverlay);
        }

        mLineOverlay.setLines(lineOverlayColor, shapes);
    }

    @Override
    public void zoomToRoute() {
        mLineOverlay.zoom(getController());
    }

    @Override
    public void removeRouteOverlay() {
        enableHWAccel(true);
        List<Overlay> overlays = getOverlays();
        if (mLineOverlay != null) {
            overlays.remove(mLineOverlay);
        }
        mLineOverlay = null;
        postInvalidate();
    }

    //
    // The real line Overlay
    //
    public static class LineOverlay extends com.google.android.maps.Overlay {

        public static final class Line {

            private final List<GeoPoint> mPoints;

            private final Paint mPaint;

            public Line(int color, List<GeoPoint> points) {
                mPoints = points;
                mPaint = new Paint();
                mPaint.setColor(color);
                mPaint.setAlpha(128);
                mPaint.setStrokeWidth(5);
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                mPaint.setStrokeJoin(Paint.Join.ROUND);
                mPaint.setStyle(Paint.Style.STROKE);
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
            List<Location> points = line.getPoints();
            List<GeoPoint> geoPoints = new ArrayList<GeoPoint>();
            for (Location p : points) {
                geoPoints.add(MapHelp.makeGeoPoint(p));
            }
            mLines.add(new Line(color, geoPoints));
            // TODO: Invalidate
        }

        public void addLines(int color, ObaShape[] lines) {
            final int len = lines.length;
            for (int i = 0; i < len; ++i) {
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
            final int len = mLines.size();
            // Log.d(TAG, String.format("Drawing %d line(s)", len));

            Path path = new Path();
            for (int i = 0; i < len; ++i) {
                final Line line = mLines.get(i);
                final List<GeoPoint> geoPoints = line.getPoints();
                int numPts = geoPoints.size();
                projection.toPixels(geoPoints.get(0), pt);
                path.moveTo(pt.x, pt.y);

                int j = 1;
                for (; j < numPts; ++j) {
                    projection.toPixels(geoPoints.get(j), pt);
                    path.lineTo(pt.x, pt.y);
                }
                canvas.drawPath(path, line.getPaint());
                path.rewind();
            }
        }

        public void zoom(MapController mapCtrl) {
            if (mapCtrl == null) {
                return;
            }

            int minLat = Integer.MAX_VALUE;
            int maxLat = Integer.MIN_VALUE;
            int minLon = Integer.MAX_VALUE;
            int maxLon = Integer.MIN_VALUE;

            for (Line line : mLines) {
                for (GeoPoint item : line.mPoints) {
                    int lat = (int) (item.getLatitudeE6());
                    int lon = (int) (item.getLongitudeE6());

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
