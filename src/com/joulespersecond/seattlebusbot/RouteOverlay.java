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
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.joulespersecond.oba.elements.ObaShape;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Point;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class RouteOverlay extends Overlay {
    private static final String TAG = "RouteOverlay";

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

    public RouteOverlay() {
    }

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
        Log.d(TAG, "Drawing " + Integer.toString(len) + " line(s)");
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
}
