/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Benjamin Du (bendu@me.com), and individual contributors.
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

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.request.ObaTripDetailsRequest;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

public class TripDetailsListFragment extends ListFragment {

    private static final String TAG = "TripDetailsListFragment";

    public static final String TRIP_ID = ".TripId";

    public static final String STOP_ID = ".StopId";

    public static final String ROUTE_ID = ".RouteId";

    public static final String SCROLL_MODE = ".ScrollMode";

    public static final String SCROLL_MODE_VEHICLE = "vehicle";

    public static final String SCROLL_MODE_STOP = "stop";

    private static final long REFRESH_PERIOD = 60 * 1000;

    private static final int TRIP_DETAILS_LOADER = 0;

    private String mTripId;

    private String mRouteId;

    private String mStopId;

    private String mScrollMode;

    private Integer mStopIndex;

    private ObaTripDetailsResponse mTripInfo;

    private TripDetailsAdapter mAdapter;

    private final TripDetailsLoaderCallback mTripDetailsCallback = new TripDetailsLoaderCallback();

    /**
     * Builds an intent used to set the trip and stop for the TripDetailsListFragment directly
     * (i.e., when TripDetailsActivity is not used)
     */
    public static class IntentBuilder {

        private Intent mIntent;

        public IntentBuilder(Context context, String tripId) {
            mIntent = new Intent(context, TripDetailsListFragment.class);
            mIntent.putExtra(TripDetailsListFragment.TRIP_ID, tripId);
        }

        public IntentBuilder setStopId(String stopId) {
            mIntent.putExtra(TripDetailsListFragment.STOP_ID, stopId);
            return this;
        }

        public Intent build() {
            return mIntent;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Start out with a progress indicator.
        setListShown(false);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        getListView().setOnItemClickListener(mClickListener);

        // Get saved routeId if it exists - avoids potential NPE in onOptionsItemSelected() (#515)
        if (savedInstanceState != null) {
            mRouteId = savedInstanceState.getString(ROUTE_ID);
        }

        Bundle args = getArguments();
        mTripId = args.getString(TRIP_ID);
        if (mTripId == null) {
            Log.e(TAG, "TripId is null");
            throw new RuntimeException("TripId should not be null");
        }

        mStopId = args.getString(STOP_ID);
        mScrollMode = args.getString(SCROLL_MODE);

        getLoaderManager().initLoader(TRIP_DETAILS_LOADER, null, mTripDetailsCallback);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        return inflater.inflate(R.layout.trip_details, null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mRouteId != null) {
            outState.putString(ROUTE_ID, mRouteId);
        }
    }

    @Override
    public void onPause() {
        mRefreshHandler.removeCallbacks(mRefresh);
        super.onPause();
    }

    @Override
    public void onResume() {
        // Try to show any old data just in case we're coming out of sleep
        TripDetailsLoader loader = getTripDetailsLoader();
        if (loader != null) {
            ObaTripDetailsResponse lastGood = loader.getLastGoodResponse();
            if (lastGood != null) {
                setTripDetails(lastGood);
            }
        }

        getLoaderManager().restartLoader(TRIP_DETAILS_LOADER, null, mTripDetailsCallback);

        // If our timer would have gone off, then refresh.
        long lastResponseTime = getTripDetailsLoader().getLastResponseTime();
        long newPeriod = Math.min(REFRESH_PERIOD, (lastResponseTime + REFRESH_PERIOD)
                - System.currentTimeMillis());
        // Wait at least one second at least, and the full minute at most.
        //Log.d(TAG, "Refresh period:" + newPeriod);
        if (newPeriod <= 0) {
            refresh();
        } else {
            mRefreshHandler.postDelayed(mRefresh, newPeriod);
        }

        super.onResume();
    }

    //
    // Action Bar / Options Menu
    //
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.trip_details, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.refresh) {
            refresh();
            return true;
        } else if (id == R.id.show_on_map) {
            HomeActivity.start(getActivity(), mRouteId);
            return true;
        }
        return false;
    }

    private void setTripDetails(ObaTripDetailsResponse data) {
        mTripInfo = data;

        final int code = mTripInfo.getCode();
        if (code == ObaApi.OBA_OK) {
            setEmptyText("");
        } else {
            setEmptyText(UIUtils.getRouteErrorString(getActivity(), code));
            return;
        }

        setUpHeader();
        final ListView listView = getListView();

        if (mAdapter == null) {  // first time displaying list
            mAdapter = new TripDetailsAdapter();
            getListView().setDivider(null);
            setListAdapter(mAdapter);
            setScroller(listView);
        } else {  // refresh, keep scroll position
            int index = listView.getFirstVisiblePosition();
            View v = listView.getChildAt(0);
            int top = (v == null) ? 0 : v.getTop();

            mAdapter.notifyDataSetChanged();
            listView.setSelectionFromTop(index, top);
        }
    }

    private void setUpHeader() {
        ObaTripStatus status = mTripInfo.getStatus();
        ObaReferences refs = mTripInfo.getRefs();

        Context context = getActivity();

        String tripId = mTripInfo.getId();

        ObaTrip trip = refs.getTrip(tripId);
        mRouteId = trip.getRouteId();
        ObaRoute route = refs.getRoute(mRouteId);
        TextView shortName = (TextView) getView().findViewById(R.id.short_name);
        shortName.setText(route.getShortName());

        TextView longName = (TextView) getView().findViewById(R.id.long_name);
        longName.setText(trip.getHeadsign());

        TextView agency = (TextView) getView().findViewById(R.id.agency);
        agency.setText(refs.getAgency(route.getAgencyId()).getName());

        TextView vehicleView = (TextView) getView().findViewById(R.id.vehicle);
        TextView vehicleDeviation = (TextView) getView().findViewById(R.id.status);
        ViewGroup realtime = (ViewGroup) getView().findViewById(
                R.id.eta_realtime_indicator);
        realtime.setVisibility(View.GONE);
        ViewGroup statusLayout = (ViewGroup) getView().findViewById(
                R.id.status_layout);

        if (status == null) {
            // Show schedule info only
            vehicleView.setText(null);
            vehicleView.setVisibility(View.GONE);
            vehicleDeviation.setText(context.getString(R.string.trip_details_scheduled_data));
            return;
        }

        if (!TextUtils.isEmpty(status.getVehicleId())) {
            // Show vehicle info
            vehicleView
                    .setText(context.getString(R.string.trip_details_vehicle,
                            status.getVehicleId()));
            vehicleView.setVisibility(View.VISIBLE);
        } else {
            vehicleView.setVisibility(View.GONE);
        }

        // Set status color in header
        statusLayout.setBackgroundResource(
                R.drawable.round_corners_style_b_header_status);
        GradientDrawable d = (GradientDrawable) statusLayout.getBackground();

        int statusColor;

        if (!status.isPredicted()) {
            // We have only schedule info, but the bus position can still be interpolated
            vehicleDeviation.setText(context.getString(R.string.trip_details_scheduled_data));
            statusColor = R.color.stop_info_scheduled_time;
            d.setColor(getResources().getColor(statusColor));
            return;
        }

        /**
         * We have real-time info
         */
        realtime.setVisibility(View.VISIBLE);

        long deviation = status.getScheduleDeviation();
        long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
        long minutes = Math.abs(deviation) / 60;
        long seconds = Math.abs(deviation) % 60;
        String lastUpdate = DateUtils.formatDateTime(getActivity(),
                status.getLastUpdateTime(),
                DateUtils.FORMAT_SHOW_TIME |
                        DateUtils.FORMAT_NO_NOON |
                        DateUtils.FORMAT_NO_MIDNIGHT
        );

        statusColor = ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
        if (statusColor != R.color.stop_info_ontime) {
            // Show early/late/scheduled color
            d.setColor(getResources().getColor(statusColor));
        } else {
            // For on-time, use header default color
            d.setColor(getResources().getColor(R.color.theme_primary));
        }

        if (deviation >= 0) {
            if (deviation < 60) {
                vehicleDeviation.setText(
                        context.getString(R.string.trip_details_real_time_sec_late, seconds,
                                lastUpdate));
            } else {
                vehicleDeviation.setText(
                        context.getString(R.string.trip_details_real_time_min_sec_late,
                                minutes, seconds, lastUpdate));
            }
        } else {
            if (deviation > -60) {
                vehicleDeviation.setText(
                        context.getString(R.string.trip_details_real_time_sec_early, seconds,
                                lastUpdate));
            } else {
                vehicleDeviation.setText(
                        context.getString(R.string.trip_details_real_time_min_sec_early, minutes,
                                seconds, lastUpdate));
            }
        }
    }

    private Integer findIndexForStop(ObaTripSchedule.StopTime[] stopTimes, String stopId) {
        if (stopId == null) {
            return null;
        }
        for (int i = 0; i < stopTimes.length; i++) {
            if (stopId.equals(stopTimes[i].getStopId())) {
                return i;
            }
        }
        return null;
    }

    private void showArrivals(String stopId, String stopName, String stopDirection) {
        new ArrivalsListActivity.Builder(getActivity(), stopId)
                .setUpMode(NavHelp.UP_MODE_BACK)
                .setStopName(stopName)
                .setStopDirection(stopDirection)
                .start();
    }

    private final AdapterView.OnItemClickListener mClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mTripInfo == null || mTripInfo.getSchedule() == null) {
                return;
            }
            ObaTripSchedule.StopTime time = mTripInfo.getSchedule().getStopTimes()[position];
            ObaReferences refs = mTripInfo.getRefs();
            String stopId = time.getStopId();
            ObaStop stop = refs.getStop(stopId);
            showArrivals(stopId, stop.getName(), stop.getDirection());
        }
    };

    private final Handler mRefreshHandler = new Handler();

    private final Runnable mRefresh = new Runnable() {
        public void run() {
            refresh();
        }
    };

    private TripDetailsLoader getTripDetailsLoader() {
        // If the Fragment hasn't been attached to an Activity yet, return null
        if (!isAdded()) {
            return null;
        }

        Loader<ObaTripDetailsResponse> l =
                getLoaderManager().getLoader(TRIP_DETAILS_LOADER);
        return (TripDetailsLoader) l;
    }

    private void refresh() {
        if (isAdded()) {
            UIUtils.showProgress(this, true);
            getTripDetailsLoader().onContentChanged();
        }
    }

    private final class TripDetailsLoaderCallback
            implements LoaderManager.LoaderCallbacks<ObaTripDetailsResponse> {

        @Override
        public Loader<ObaTripDetailsResponse> onCreateLoader(int id, Bundle args) {
            return new TripDetailsLoader(getActivity(), mTripId);
        }

        @Override
        public void onLoadFinished(Loader<ObaTripDetailsResponse> loader,
                                   ObaTripDetailsResponse data) {
            setTripDetails(data);

            // The list should now be shown.
            if (isResumed()) {
                setListShown(true);
            } else {
                setListShownNoAnimation(true);
            }

            // Clear any pending refreshes
            mRefreshHandler.removeCallbacks(mRefresh);

            // Post an update
            mRefreshHandler.postDelayed(mRefresh, REFRESH_PERIOD);
        }

        @Override
        public void onLoaderReset(Loader<ObaTripDetailsResponse> loader) {
            // Nothing to do right here...
        }
    }

    private final static class TripDetailsLoader extends AsyncTaskLoader<ObaTripDetailsResponse> {

        private final String mTripId;

        private ObaTripDetailsResponse mLastGoodResponse;

        private long mLastResponseTime = 0;

        private long mLastGoodResponseTime = 0;

        TripDetailsLoader(Context context, String tripId) {
            super(context);
            mTripId = tripId;
        }

        @Override
        public ObaTripDetailsResponse loadInBackground() {
            return ObaTripDetailsRequest.newRequest(getContext(), mTripId).call();
        }

        @Override
        public void deliverResult(ObaTripDetailsResponse data) {
            mLastResponseTime = System.currentTimeMillis();
            if (data.getCode() == ObaApi.OBA_OK) {
                mLastGoodResponse = data;
                mLastGoodResponseTime = mLastResponseTime;
            }
            super.deliverResult(data);
        }

        public long getLastResponseTime() {
            return mLastResponseTime;
        }

        public ObaTripDetailsResponse getLastGoodResponse() {
            return mLastGoodResponse;
        }

        public long getLastGoodResponseTime() {
            return mLastGoodResponseTime;
        }
    }

    private final class TripDetailsAdapter extends BaseAdapter {

        LayoutInflater mInflater;

        ObaTripSchedule mSchedule;
        ObaReferences mRefs;
        ObaTripStatus mStatus;

        Integer mNextStopIndex;

        public TripDetailsAdapter() {
            this.mInflater = LayoutInflater.from(TripDetailsListFragment.this.getActivity());

            updateData();
        }

        private void updateData() {
            this.mSchedule = mTripInfo.getSchedule();
            this.mRefs = mTripInfo.getRefs();
            this.mStatus = mTripInfo.getStatus();

            mNextStopIndex = null;
            if (mStatus == null) {
                // We don't have real-time data - clear next stop index
                return;
            }

            // Based on real-time data, set the index for the next stop
            String stopId = mStatus.getNextStop();
            int i;
            for (i = 0; i < mSchedule.getStopTimes().length; i++) {
                ObaTripSchedule.StopTime time = mSchedule.getStopTimes()[i];
                if (time.getStopId().equals(stopId)) {
                    mNextStopIndex = i;
                    break;
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            updateData();
            super.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mSchedule.getStopTimes().length;
        }

        @Override
        public Object getItem(int position) {
            return mSchedule.getStopTimes()[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.trip_details_listitem, parent, false);
            }

            ObaTripSchedule.StopTime stopTime = mSchedule.getStopTimes()[position];
            ObaStop stop = mRefs.getStop(stopTime.getStopId());
            ObaTrip trip = mRefs.getTrip(mTripId);
            ObaRoute route = mRefs.getRoute(trip.getRouteId());

            TextView stopName = (TextView) convertView.findViewById(R.id.stop_name);
            stopName.setText(UIUtils.formatDisplayText(stop.getName()));

            TextView time = (TextView) convertView.findViewById(R.id.time);
            ViewGroup realtime = (ViewGroup) convertView.findViewById(
                    R.id.eta_realtime_indicator);
            realtime.setVisibility(View.GONE);

            long date;
            long deviation = 0;
            int statusColor;
            if (mStatus != null) {
                // If we have real-time info, use that
                date = mStatus.getServiceDate();
                deviation = mStatus.getScheduleDeviation();
                long deviationMin = TimeUnit.SECONDS.toMinutes(mStatus.getScheduleDeviation());
                if (mStatus.isPredicted()) {
                    statusColor = ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
                } else {
                    statusColor = R.color.stop_info_scheduled_time;
                }
            } else {
                // Use current date - its only to offset time correctly from midnight
                Calendar cal = new GregorianCalendar();
                // Reset to midnight of today
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                date = cal.getTime().getTime();
                statusColor = R.color.stop_info_scheduled_time;
            }
            time.setText(DateUtils.formatDateTime(getActivity(),
                    date + stopTime.getArrivalTime() * 1000
                            + deviation * 1000,
                    DateUtils.FORMAT_SHOW_TIME |
                            DateUtils.FORMAT_NO_NOON |
                            DateUtils.FORMAT_NO_MIDNIGHT
            ));

            ImageView bus = (ImageView) convertView.findViewById(R.id.bus_icon);
            ImageView stopIcon = (ImageView) convertView.findViewById(R.id.stop_icon);
            ImageView topLine = (ImageView) convertView.findViewById(R.id.top_line);
            ImageView bottomLine = (ImageView) convertView.findViewById(R.id.bottom_line);
            ImageView transitStop = (ImageView) convertView.findViewById(R.id.transit_stop);

            // Set bus and realtime indicator to match status color
            bus.setColorFilter(getResources().getColor(statusColor));
            UIUtils.setRealtimeIndicatorColor(realtime, getResources().getColor(statusColor),
                    android.R.color.transparent);

            int routeColor;
            // If a route color (other than the default white) is included in the API response, then use it
            if (route.getColor() != null && route.getColor() != android.R.color.white) {
                routeColor = route.getColor();
            } else {
                // Use the theme color
                routeColor = getResources().getColor(R.color.theme_primary);
            }

            stopIcon.setColorFilter(routeColor);
            topLine.setColorFilter(routeColor);
            bottomLine.setColorFilter(routeColor);
            transitStop.setColorFilter(routeColor);

            if (position == 0) {
                // First stop in trip - hide the top half of the transit line
                topLine.setVisibility(View.INVISIBLE);
                bottomLine.setVisibility(View.VISIBLE);
            } else if (position == mSchedule.getStopTimes().length - 1) {
                // Last stop in trip - hide the bottom half of the transit line
                topLine.setVisibility(View.VISIBLE);
                bottomLine.setVisibility(View.INVISIBLE);
            } else {
                // Middle of trip - show top and bottom transit lines
                topLine.setVisibility(View.VISIBLE);
                bottomLine.setVisibility(View.VISIBLE);
            }

            if (mStopIndex != null && mStopIndex == position) {
                // Show the selected stop
                stopIcon.setVisibility(View.VISIBLE);
            } else {
                stopIcon.setVisibility(View.GONE);
            }

            if (mNextStopIndex != null) {
                if (position == mNextStopIndex - 1) {
                    // The bus just passed this stop - show the bus icon here
                    bus.setVisibility(View.VISIBLE);

                    if (mStatus != null && mStatus.isPredicted()) {
                        // Show realtime indicator
                        realtime.setVisibility(View.VISIBLE);
                    }
                } else {
                    bus.setVisibility(View.INVISIBLE);
                }

                if (position < mNextStopIndex) {
                    // Bus passed stop - fade out these stops
                    stopName.setTextColor(getResources().getColor(R.color.trip_details_passed));
                    time.setTextColor(getResources().getColor(R.color.trip_details_passed));
                } else {
                    // Bus hasn't yet passed this stop - leave full color
                    stopName.setTextColor(getResources().getColor(R.color.trip_details_not_passed));
                    time.setTextColor(getResources().getColor(R.color.trip_details_not_passed));
                }
            } else {
                // No "next stop" info - hide the bus icon
                bus.setVisibility(View.INVISIBLE);
                // Bus hasn't passed this stop - leave full color
                stopName.setTextColor(getResources().getColor(R.color.trip_details_not_passed));
                time.setTextColor(getResources().getColor(R.color.trip_details_not_passed));
            }
            return convertView;
        }

        /**
         * Returns the next stop index based on real-time info, or null no estimated next stop
         * exists
         *
         * @return the next stop index based on real-time info, or null no estimated next stop
         * exists
         */
        public Integer getNextStopIndex() {
            return mNextStopIndex;
        }
    }

    private void setScroller(final ListView listView)
    {
        if (mScrollMode == null) {
            // Don't automatically scroll to stop or vehicle icon
            return;
        }

        switch (mScrollMode) {
            case SCROLL_MODE_VEHICLE: {
                // Scroll to bus marker if scroll mode is set to vehicle & we have index.
                final Integer nextStop = mAdapter.getNextStopIndex();
                if (nextStop != null) {
                    listView.post(new Runnable() {
                        @Override
                        public void run() {
                            listView.setSelection(nextStop - 1);
                        }
                    });
                } else {
                    // If we couldn't get the bus marker, fall back to stop.
                    mStopIndex = findIndexForStop(mTripInfo.getSchedule().getStopTimes(), mStopId);
                    if (mStopIndex != null) {
                        listView.post(new Runnable() {
                            @Override
                            public void run() {
                                listView.setSelection(mStopIndex);
                            }
                        });
                    }
                }
                break;
            }

            case SCROLL_MODE_STOP: {
                // Scroll to stop if we have the stopId available
                if (mStopId != null) {
                    mStopIndex = findIndexForStop(mTripInfo.getSchedule().getStopTimes(), mStopId);
                    if (mStopIndex != null) {
                        listView.post(new Runnable() {
                            @Override
                            public void run() {
                                listView.setSelection(mStopIndex);
                            }
                        });
                    }
                } else {
                    // If we don't have a stop, then scroll to the current position of the bus
                    final Integer nextStop = mAdapter.getNextStopIndex();
                    if (nextStop != null) {
                        listView.post(new Runnable() {
                            @Override
                            public void run() {
                                listView.setSelection(nextStop - 1);
                            }
                        });
                    }
                }
                break;
            }
        }
        mAdapter.notifyDataSetChanged();
    }
}
