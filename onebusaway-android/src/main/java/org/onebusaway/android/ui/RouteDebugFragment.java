/*
 * Copyright (C) 2024 Sean J. Barbeau (sjbarbeau@gmail.com)
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

package org.onebusaway.android.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.request.ObaRouteResponse;
import org.onebusaway.android.io.request.ObaTripsForRouteRequest;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

public class RouteDebugFragment extends ListFragment {

    private static final int ROUTE_INFO_LOADER = 0;
    private static final int TRIPS_LOADER = 1;

    private String mRouteId;

    private final RouteLoaderCallback mRouteCallback = new RouteLoaderCallback();
    private final TripsLoaderCallback mTripsCallback = new TripsLoaderCallback();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            return null;
        }
        return inflater.inflate(R.layout.route_debug, null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Uri uri = (Uri) getArguments().getParcelable(FragmentUtils.URI);
        mRouteId = uri.getLastPathSegment();

        getLoaderManager().initLoader(ROUTE_INFO_LOADER, null, mRouteCallback);
        getLoaderManager().initLoader(TRIPS_LOADER, null, mTripsCallback);
    }

    @Override
    public void onListItemClick(android.widget.ListView l, View v, int position, long id) {
        ObaTripDetails details = (ObaTripDetails) l.getItemAtPosition(position);
        if (details != null) {
            TripDetailsActivity.start(getActivity(), details.getId());
        }
    }

    private void setHeader(ObaRouteResponse routeInfo) {
        View view = getView();
        if (view == null || routeInfo.getCode() != ObaApi.OBA_OK) {
            return;
        }

        ProgressBar spinner = (ProgressBar) view.findViewById(R.id.route_info_loading_spinner);
        if (spinner != null) {
            spinner.setVisibility(View.GONE);
        }

        UIUtils.setRouteView(view, routeInfo);
        TextView agencyText = (TextView) view.findViewById(R.id.agency);
        agencyText.setText(routeInfo.getAgency().getName());
    }

    private void setTripsData(ObaTripsForRouteResponse response) {
        View view = getView();
        if (view == null) {
            return;
        }

        // Hide loading spinner, show empty text if needed
        view.findViewById(R.id.trips_loading_spinner).setVisibility(View.GONE);
        TextView emptyText = (TextView) view.findViewById(R.id.trips_empty_text);
        emptyText.setVisibility(View.VISIBLE);

        if (response.getCode() != ObaApi.OBA_OK) {
            emptyText.setText("Error loading trips: " + response.getCode());
            return;
        }

        // Filter to only trips belonging to this route
        ObaTripDetails[] allTrips = response.getTrips();
        List<ObaTripDetails> filtered = new ArrayList<>();
        for (ObaTripDetails t : allTrips) {
            ObaTrip trip = response.getTrip(t.getId());
            if (trip != null && mRouteId.equals(trip.getRouteId())) {
                filtered.add(t);
            }
        }
        ObaTripDetails[] trips = filtered.toArray(new ObaTripDetails[0]);

        TextView countView = (TextView) view.findViewById(R.id.debug_trip_count);
        countView.setText(trips.length + " active trips");

        emptyText.setText("No active trips");
        setListAdapter(new TripDebugAdapter(getActivity(), trips, response));
    }

    // -- Loaders --

    private final class RouteLoaderCallback
            implements LoaderManager.LoaderCallbacks<ObaRouteResponse> {

        @Override
        public Loader<ObaRouteResponse> onCreateLoader(int id, Bundle args) {
            return new QueryUtils.RouteInfoLoader(getActivity(), mRouteId);
        }

        @Override
        public void onLoadFinished(Loader<ObaRouteResponse> loader, ObaRouteResponse data) {
            setHeader(data);
        }

        @Override
        public void onLoaderReset(Loader<ObaRouteResponse> loader) {
        }
    }

    private final class TripsLoaderCallback
            implements LoaderManager.LoaderCallbacks<ObaTripsForRouteResponse> {

        @Override
        public Loader<ObaTripsForRouteResponse> onCreateLoader(int id, Bundle args) {
            return new TripsForRouteLoader(getActivity(), mRouteId);
        }

        @Override
        public void onLoadFinished(Loader<ObaTripsForRouteResponse> loader,
                ObaTripsForRouteResponse data) {
            setTripsData(data);
        }

        @Override
        public void onLoaderReset(Loader<ObaTripsForRouteResponse> loader) {
        }
    }

    private static final class TripsForRouteLoader
            extends AsyncTaskLoader<ObaTripsForRouteResponse> {

        private final String mRouteId;

        TripsForRouteLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }

        @Override
        public ObaTripsForRouteResponse loadInBackground() {
            return new ObaTripsForRouteRequest.Builder(getContext(), mRouteId)
                    .setIncludeStatus(true)
                    .setIncludeSchedule(false)
                    .build()
                    .call();
        }
    }

    // -- Adapter --

    private static class TripDebugAdapter extends BaseAdapter {

        private final Context mContext;
        private final ObaTripDetails[] mTrips;
        private final ObaTripsForRouteResponse mResponse;

        TripDebugAdapter(Context context, ObaTripDetails[] trips,
                ObaTripsForRouteResponse response) {
            mContext = context;
            mTrips = trips;
            mResponse = response;
        }

        @Override
        public int getCount() {
            return mTrips.length;
        }

        @Override
        public ObaTripDetails getItem(int position) {
            return mTrips[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext)
                        .inflate(R.layout.route_debug_list_item, parent, false);
            }

            ObaTripDetails details = getItem(position);
            ObaTripStatus status = details.getStatus();
            String tripId = details.getId();
            ObaTrip trip = mResponse.getTrip(tripId);

            TextView headsignView = convertView.findViewById(R.id.debug_headsign);
            TextView tripIdView = convertView.findViewById(R.id.debug_trip_id);
            TextView vehicleIdView = convertView.findViewById(R.id.debug_vehicle_id);
            TextView deviationView = convertView.findViewById(R.id.debug_deviation);
            TextView distanceView = convertView.findViewById(R.id.debug_distance);
            TextView lastUpdateView = convertView.findViewById(R.id.debug_last_update);

            // Headsign
            if (trip != null) {
                headsignView.setText(trip.getHeadsign());
            } else {
                headsignView.setText(tripId);
            }

            // Trip ID
            tripIdView.setText("Trip: " + tripId);

            if (status != null) {
                // Vehicle ID
                String vehicleId = status.getVehicleId();
                if (!TextUtils.isEmpty(vehicleId)) {
                    vehicleIdView.setText("V: " + vehicleId);
                    vehicleIdView.setVisibility(View.VISIBLE);
                } else {
                    vehicleIdView.setVisibility(View.GONE);
                }

                // Schedule deviation
                if (status.isPredicted()) {
                    long deviationMin = TimeUnit.SECONDS.toMinutes(
                            status.getScheduleDeviation());
                    String devStr = ArrivalInfoUtils.computeArrivalLabelFromDelay(
                            mContext.getResources(), deviationMin);
                    deviationView.setText("Dev: " + devStr);
                } else {
                    deviationView.setText("Dev: scheduled");
                }

                // Distance along trip
                Double dist = status.getDistanceAlongTrip();
                Double total = status.getTotalDistanceAlongTrip();
                if (dist != null && total != null && total > 0) {
                    int pct = (int) (dist / total * 100);
                    distanceView.setText("Dist: " + pct + "%");
                } else if (dist != null) {
                    distanceView.setText("Dist: " + dist.intValue() + "m");
                } else {
                    distanceView.setText("Dist: --");
                }

                // Last update time
                long lastUpdate = status.getLastUpdateTime();
                if (lastUpdate > 0) {
                    lastUpdateView.setText(DateUtils.getRelativeTimeSpanString(
                            lastUpdate, System.currentTimeMillis(),
                            DateUtils.SECOND_IN_MILLIS));
                } else {
                    lastUpdateView.setText("no update");
                }
            } else {
                vehicleIdView.setVisibility(View.GONE);
                deviationView.setText("Dev: --");
                distanceView.setText("Dist: --");
                lastUpdateView.setText("no status");
            }

            return convertView;
        }
    }
}
