/*
 * Copyright (C) 2011-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), and individual contributors.
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

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaStopsForRouteRequest;
import com.joulespersecond.oba.request.ObaStopsForRouteResponse;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.BuildConfig;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.map.googlemapsv2.BaseMapFragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

public class RouteMapController implements MapModeController,
        LoaderManager.LoaderCallbacks<ObaStopsForRouteResponse>,
        Loader.OnLoadCompleteListener<ObaStopsForRouteResponse> {

    private static final String TAG = "RouteMapController";

    private static final int ROUTES_LOADER = 5677;

    private final Callback mFragment;

    private String mRouteId;

    private boolean mZoomToRoute;

    private final int mLineOverlayColor;

    private RoutePopup mRoutePopup;

    // In lieu of using an actual LoaderManager, which isn't
    // available in SherlockMapActivity
    private Loader<ObaStopsForRouteResponse> mLoader;

    public RouteMapController(Callback callback) {
        mFragment = callback;
        mLineOverlayColor = mFragment.getActivity()
                .getResources()
                .getColor(R.color.route_overlay_line);
        mRoutePopup = new RoutePopup();
    }

    @Override
    public void setState(Bundle args) {
        assert (args != null);
        String routeId = args.getString(MapParams.ROUTE_ID);
        mZoomToRoute = args.getBoolean(MapParams.ZOOM_TO_ROUTE, false);
        if (!routeId.equals(mRouteId)) {
            mRouteId = routeId;
            mRoutePopup.showLoading();
            mFragment.showProgress(true);
            //mFragment.getLoaderManager().restartLoader(ROUTES_LOADER, null, this);
            mLoader = onCreateLoader(ROUTES_LOADER, null);
            mLoader.registerListener(0, this);
            mLoader.startLoading();
        }
    }

    @Override
    public String getMode() {
        return MapParams.MODE_ROUTE;
    }

    @Override
    public void destroy() {
        mRoutePopup.hide();
        mFragment.getMapView().removeRouteOverlay();
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
    public void notifyMapChanged() {
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

        ObaMapView obaMapView = mFragment.getMapView();

        if (response.getCode() != ObaApi.OBA_OK) {
            BaseMapFragment.showMapError(mFragment.getActivity(), response);
            return;
        }

        mRoutePopup.show(response.getRoute(response.getRouteId()));

        obaMapView.setRouteOverlay(mLineOverlayColor, response.getShapes());

        // Set the stops for this route
        List<ObaStop> stops = response.getStops();
        mFragment.showStops(stops, response);
        mFragment.showProgress(false);

        if (mZoomToRoute) {
            obaMapView.zoomToRoute();
            mZoomToRoute = false;
        }
        //
        // wait to zoom till we have the right response
        obaMapView.postInvalidate();
    }

    @Override
    public void onLoaderReset(Loader<ObaStopsForRouteResponse> loader) {
        mFragment.getMapView().removeRouteOverlay();
    }

    @Override
    public void onLoadComplete(Loader<ObaStopsForRouteResponse> loader,
            ObaStopsForRouteResponse response) {
        onLoadFinished(loader, response);
    }

    //
    // Map popup
    //
    private class RoutePopup {

        //private final Context mContext;
        //private final View mView;

//        private final TextView mRouteShortName;
//
//        private final TextView mRouteLongName;

        RoutePopup() {
            //mContext = fragment.getActivity();
            //mView = mFragment.getView().findViewById(R.id.route_info);
//            mRouteShortName = (TextView) mView.findViewById(R.id.short_name);
//            mRouteLongName = (TextView) mView.findViewById(R.id.long_name);
//            TextView agency = (TextView) mView.findViewById(R.id.agency);
//            agency.setVisibility(View.GONE);
//            Make sure the cancel button is shown
//            View cancel = mView.findViewById(R.id.cancel_route_mode);
//            cancel.setVisibility(View.VISIBLE);
//            cancel.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    ObaMapView obaMapView = mFragment.getMapView();
//                    // We want to preserve the current zoom and center.
//                    Bundle bundle = new Bundle();
//                    bundle.putFloat(MapParams.ZOOM, obaMapView.getZoomLevelAsFloat());
//                    Location point = obaMapView.getMapCenterAsLocation();
//                    bundle.putDouble(MapParams.CENTER_LAT, point.getLatitude());
//                    bundle.putDouble(MapParams.CENTER_LON, point.getLongitude());
//                    mFragment.setMapMode(MapParams.MODE_STOP, bundle);
//                }
//            });
        }

        void showLoading() {
//            mRouteShortName.setVisibility(View.GONE);
//            mRouteLongName.setText(R.string.loading);
//            mView.setVisibility(View.VISIBLE);
        }

        void show(ObaRoute route) {
//            mRouteShortName.setText(UIHelp.getRouteDisplayName(route));
//            mRouteLongName.setText(UIHelp.getRouteDescription(route));
//            mRouteShortName.setVisibility(View.VISIBLE);
//            mView.setVisibility(View.VISIBLE);
        }

        void hide() {
            //mView.setVisibility(View.GONE);
        }
    }

    //
    // Loader
    //
    private static class RoutesLoader extends AsyncTaskLoader<ObaStopsForRouteResponse> {

        private final String mRouteId;

        public RoutesLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public ObaStopsForRouteResponse loadInBackground() {
            if (Application.get().getCurrentRegion() == null &&
                    TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
                //We don't have region info or manually entered API to know what server to contact
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Trying to load stops for route from server " +
                            "without OBA REST API endpoint, aborting...");
                }
                return null;
            }
            //Make OBA REST API call to the server and return result
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
}
