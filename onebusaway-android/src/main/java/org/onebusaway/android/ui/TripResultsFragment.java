/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.model.Direction;
import org.onebusaway.android.directions.realtime.RealtimeService;
import org.onebusaway.android.directions.realtime.RealtimeServiceImpl;
import org.onebusaway.android.directions.util.ConversionUtils;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.directions.util.DirectionExpandableListAdapter;
import org.onebusaway.android.directions.util.DirectionsGenerator;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.opentripplanner.api.model.Itinerary;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripResultsFragment extends Fragment {

    private static final String TAG = "TripResultsFragment";

    private BaseMapFragment mMapFragment;
    private ExpandableListView mDirectionsListView;
    private boolean mShowingMap;

    private LinearLayout mSwitchViewLayout;
    private ImageView mSwitchViewImageView;
    private TextView mSwitchViewTextView;

    private RoutingOptionPicker[] mOptions = new RoutingOptionPicker[3];

    private RealtimeService mRealtimeService;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View view = inflater.inflate(R.layout.fragment_trip_plan_results, container, false);

        mSwitchViewLayout = (LinearLayout) view.findViewById(R.id.switchViewLayout);
        mSwitchViewTextView = (TextView) view.findViewById(R.id.switchViewText);
        mSwitchViewImageView = (ImageView) view.findViewById(R.id.switchViewImageView);

        mDirectionsListView = (ExpandableListView) view.findViewById(R.id.directionsListView);

        mOptions[0] = new RoutingOptionPicker(view, R.id.option1LinearLayout, R.id.option1Title, R.id.option1Duration, R.id.option1Interval);
        mOptions[1] = new RoutingOptionPicker(view, R.id.option2LinearLayout, R.id.option2Title, R.id.option2Duration, R.id.option2Interval);
        mOptions[2] = new RoutingOptionPicker(view, R.id.option3LinearLayout, R.id.option3Title, R.id.option3Duration, R.id.option3Interval);

        mSwitchViewLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMap(!mShowingMap);
            }
        });


        mRealtimeService = new RealtimeServiceImpl(getActivity().getApplicationContext(), getActivity(), getArguments());

        initMap();
        initInfo(0);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealtimeService.disableListenForTripUpdates();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavHelp.goUp(getActivity());
            return true;
        }
        if (item.getItemId() == R.id.show_on_map) {
            showMap(true);
        }
        if (item.getItemId() == R.id.list) {
            showMap(false);
        }
        return false;
    }

    private void initMap() {
        mMapFragment = new BaseMapFragment();

        Bundle bundle = new Bundle();
        bundle.putString(MapParams.MODE, MapParams.MODE_DIRECTIONS);

        Intent intent = new Intent().putExtras(bundle);
        getActivity().setIntent(intent);

        getChildFragmentManager().beginTransaction()
                .add(R.id.mapFragment, mMapFragment).commit();

        showMap(false);
    }

    private Bundle mMapBundle = new Bundle();

    private void showMap(boolean show) {

        mShowingMap = show;
        if (show) {
            getActivity().findViewById(R.id.mapFragment).bringToFront();
            mMapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mMapBundle);
            mSwitchViewImageView.setImageResource(R.drawable.ic_more_vert);
            mSwitchViewTextView.setText(getString(R.string.trip_plan_list_view));
        } else {
            mDirectionsListView.bringToFront();
            mSwitchViewImageView.setImageResource(R.drawable.ic_arrivals_styleb_action_map);
            mSwitchViewTextView.setText(getString(R.string.trip_plan_map_view));
        }
    }

    public void initInfo(int trip) {

        for (int i = 0; i < mOptions.length; i++) {
            mOptions[i].setItinerary(i);
        }

        mOptions[trip].updateInfo();
        mOptions[trip].updateMap();

    }

    public void displayNewResults() {
        showMap(mShowingMap);
        initInfo(0);
    }

    private String toDateFmt(long ms) {
        Date d = new Date(ms);
        String s = new SimpleDateFormat(OTPConstants.TRIP_RESULTS_TIME_STRING_FORMAT_SUMMARY, Locale.getDefault()).format(d);
        return s.substring(0, 6).toLowerCase();
    }

    private String formatTimeString(String ms, double durationSec) {
        long start = Long.parseLong(ms);
        String fromString = toDateFmt(start);
        String toString = toDateFmt(start + (long) durationSec);

        return fromString + " - " + toString;
    }

    private List<Itinerary> getItineraries() {
        return (List<Itinerary>) getArguments().getSerializable(OTPConstants.ITINERARIES);
    }

    private class RoutingOptionPicker {
        LinearLayout linearLayout;
        TextView titleView;
        TextView durationView;
        TextView intervalView;

        Itinerary itinerary;

        RoutingOptionPicker(View view, int linearLayout, int titleView, int durationView, int intervalView) {
            this.linearLayout = (LinearLayout) view.findViewById(linearLayout);
            this.titleView = (TextView) view.findViewById(titleView);
            this.durationView = (TextView) view.findViewById(durationView);
            this.intervalView = (TextView) view.findViewById(intervalView);

            this.linearLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateInfo();
                    updateMap();
                    RoutingOptionPicker.this.select();
                }
            });
        }

        void select() {
            for (RoutingOptionPicker picker : mOptions)
                picker.linearLayout.setBackgroundColor(getResources().getColor(R.color.trip_option_background));
            linearLayout.setBackgroundResource(R.drawable.bottom_line_grey_blue);
        }


        void setItinerary(int rank) {
            List<Itinerary> trips = getItineraries();
            if (rank >= trips.size()) {
                this.itinerary = null;
                linearLayout.setVisibility(View.GONE);
                return;
            }

            this.itinerary = trips.get(rank);


            String title = new DirectionsGenerator(itinerary.legs, getContext()).getItineraryTitle();
            String duration = ConversionUtils.getFormattedDurationTextNoSeconds(itinerary.duration, false, getContext());
            String interval = formatTimeString(itinerary.startTime, itinerary.duration * 1000);

            titleView.setText(title);
            durationView.setText(duration);
            intervalView.setText(interval);
        }

        void updateInfo() {
            DirectionsGenerator gen = new DirectionsGenerator(itinerary.legs, getActivity().getApplicationContext());
            List<Direction> directions = gen.getDirections();
            Direction direction_data[] = directions.toArray(new Direction[directions.size()]);

            DirectionExpandableListAdapter adapter = new DirectionExpandableListAdapter(
                    getActivity(),
                    R.layout.list_direction_item, R.layout.list_subdirection_item, direction_data);

            mDirectionsListView.setAdapter(adapter);

            mDirectionsListView.setGroupIndicator(null);

            if(Application.getPrefs()
                    .getBoolean(getString(R.string.preference_key_trip_plan_notifications), true)) {
                mRealtimeService.onItinerarySelected(itinerary);
            }
        }

        private void updateMap() {
            mMapBundle.putSerializable(MapParams.ITINERARY, itinerary);
            mMapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mMapBundle);
        }
    }
}
