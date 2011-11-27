package com.joulespersecond.seattlebusbot.map;

import com.joulespersecond.oba.request.ObaResponse;
import com.joulespersecond.oba.request.ObaStopsForRouteRequest;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

class MapLoaders {
    /**
     * Loads stops for a location
     * @author paulw
     */
    abstract class MapLoader extends AsyncTaskLoader<ObaResponse> {
        private ObaResponse mResponse;

        public MapLoader(Context context) {
            super(context);
        }

        public ObaResponse getResponse() {
            return mResponse;
        }

        @Override
        public void deliverResult(ObaResponse data) {
            mResponse = data;
            super.deliverResult(data);
        }
    }

    /**
     * Loads stops for a route.
     */
    class RouteLoader extends MapLoader {
        private final String mRouteId;

        public RouteLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public ObaResponse loadInBackground() {
            return new ObaStopsForRouteRequest.Builder(getContext(), mRouteId)
                .setIncludeShapes(true)
                .build()
                .call();
        }

        public String getRouteId() {
            return mRouteId;
        }
    }

}
