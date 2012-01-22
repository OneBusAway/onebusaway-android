/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com) and individual contributors.
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
package com.joulespersecond.seattlebusbot.map;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaStopsForRouteRequest;
import com.joulespersecond.oba.request.ObaStopsForRouteResponse;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.UIHelp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

class RouteMapController implements MapFragmentController,
            LoaderManager.LoaderCallbacks<ObaStopsForRouteResponse> {
    //private static final String TAG = "RouteMapController";
    private static final int ROUTES_LOADER = 5677;

    private final FragmentCallback mFragment;

    private String mRouteId;
    private LineOverlay mLineOverlay;
    private boolean mZoomToRoute;
    private final int mLineOverlayColor;
    private RoutePopup mRoutePopup;

    RouteMapController(FragmentCallback callback) {
        mFragment = callback;
        mLineOverlayColor = mFragment.getActivity()
                                .getResources()
                                .getColor(R.color.route_overlay_line);
        mRoutePopup = new RoutePopup();
    }

    @Override
    public void setState(Bundle args) {
        String routeId = args.getString(MapParams.ROUTE_ID);
        mZoomToRoute = args.getBoolean(MapParams.ZOOM_TO_ROUTE, false);
        if (!routeId.equals(mRouteId)) {
            mRouteId = routeId;
            mRoutePopup.showLoading();
            mFragment.showProgress(true);
            mFragment.getLoaderManager().restartLoader(ROUTES_LOADER, null, this);
        }
    }

    @Override
    public String getMode() {
        return MapParams.MODE_ROUTE;
    }

    @Override
    public void destroy() {
        mRoutePopup.hide();
        removeOverlay();
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(MapParams.ROUTE_ID, mRouteId);
        outState.putBoolean(MapParams.ZOOM_TO_ROUTE, mZoomToRoute);
    }

    @Override
    public void onLocation() {
        // Don't care
    }

    @Override
    public void onNoLocation() {
        // Don't care
    }

    @Override
    public Loader<ObaStopsForRouteResponse> onCreateLoader(int id,
            Bundle args) {
        return new RoutesLoader(mFragment.getActivity(), mRouteId);
    }

    @Override
    public void onLoadFinished(Loader<ObaStopsForRouteResponse> loader,
            ObaStopsForRouteResponse response) {
        MapView mapView = mFragment.getMapView();
        List<Overlay> overlays = mapView.getOverlays();

        if (mLineOverlay == null) {
            mLineOverlay = new LineOverlay();
            overlays.add(mLineOverlay);
        }

        if (response.getCode() != ObaApi.OBA_OK) {
            Activity act = mFragment.getActivity();
            Toast.makeText(act,
                    act.getString(R.string.main_stop_errors),
                    Toast.LENGTH_LONG);
            return;
        }

        mRoutePopup.show(response.getRoute(response.getRouteId()));
        mLineOverlay.setLines(mLineOverlayColor, response.getShapes());

        // Set the stops for this route
        List<ObaStop> stops = response.getStops();
        mFragment.showStops(stops, response);
        mFragment.showProgress(false);

        if (mZoomToRoute) {
            mLineOverlay.zoom(mapView.getController());
            mZoomToRoute = false;
        }
        //
        // wait to zoom till we have the right response
        mapView.postInvalidate();
    }

    @Override
    public void onLoaderReset(Loader<ObaStopsForRouteResponse> loader) {
        removeOverlay();
    }

    private void removeOverlay() {
        MapView mapView = mFragment.getMapView();
        List<Overlay> overlays = mapView.getOverlays();
        if (mLineOverlay != null) {
            overlays.remove(mLineOverlay);
        }
        mLineOverlay = null;
        mapView.postInvalidate();
    }

    //
    // Map popup
    //
    private class RoutePopup {
        //private final Context mContext;
        private final View mView;
        private final TextView mRouteShortName;
        private final TextView mRouteLongName;

        RoutePopup() {
            //mContext = fragment.getActivity();
            mView = mFragment.getView().findViewById(R.id.route_info);
            mRouteShortName = (TextView)mView.findViewById(R.id.short_name);
            mRouteLongName = (TextView)mView.findViewById(R.id.long_name);
            TextView agency = (TextView)mView.findViewById(R.id.agency);
            agency.setVisibility(View.GONE);
            // Make sure the cancel button is shown
            View cancel = mView.findViewById(R.id.cancel_route_mode);
            cancel.setVisibility(View.VISIBLE);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFragment.setMapMode(MapParams.MODE_STOP, null);
                }
            });
        }

        void showLoading() {
            mRouteShortName.setVisibility(View.GONE);
            mRouteLongName.setText(R.string.loading);
            mView.setVisibility(View.VISIBLE);
        }

        void show(ObaRoute route) {
            mRouteShortName.setText(UIHelp.getRouteDisplayName(route));
            mRouteLongName.setText(UIHelp.getRouteDescription(route));
            mRouteShortName.setVisibility(View.VISIBLE);
            mView.setVisibility(View.VISIBLE);
        }

        void hide() {
            mView.setVisibility(View.GONE);
        }
    }

    //
    // Loader
    //
    private static class RoutesLoader extends AsyncTaskLoader<ObaStopsForRouteResponse> {
        private final String mRouteId;
        //private ObaStopsForRouteResponse mResponse;

        public RoutesLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public ObaStopsForRouteResponse loadInBackground() {
            return new ObaStopsForRouteRequest.Builder(getContext(), mRouteId)
                .setIncludeShapes(true)
                .build()
                .call();
        }

        @Override
        public void deliverResult(ObaStopsForRouteResponse data) {
            //mResponse = data;
            super.deliverResult(data);
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }
    }

    //
    // The real line Overlay
    //
    public static class LineOverlay extends Overlay {

        public static final class Line {
            private final List<GeoPoint> mPoints;
            private final Paint mPaint;

            public Line(int color, List<GeoPoint> points) {
                mPoints = points;
                mPaint = new Paint();
                mPaint.setColor(color);
                mPaint.setAlpha(128);
                mPaint.setStrokeWidth(5);
                mPaint.setStrokeCap(Cap.ROUND);
                mPaint.setStrokeJoin(Join.ROUND);
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
            final int len = mLines.size();
            // Log.d(TAG, String.format("Drawing %d line(s)", len));

            Path path = new Path();
            for (int i=0; i < len; ++i) {
                final Line line = mLines.get(i);
                final List<GeoPoint> geoPoints = line.getPoints();
                int numPts = geoPoints.size();
                projection.toPixels(geoPoints.get(0), pt);
                path.moveTo(pt.x, pt.y);

                int j=1;
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
