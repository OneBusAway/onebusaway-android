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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.MenuItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.onebusaway.android.directions.model.Direction;
import org.onebusaway.android.directions.realtime.RealtimeService;
import org.onebusaway.android.directions.realtime.RealtimeServiceImpl;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.directions.util.DirectionExpandableListAdapter;
import org.onebusaway.android.directions.util.DirectionsGenerator;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.R;
import org.opentripplanner.api.model.Itinerary;

public class TripResultsFragment extends Fragment {
    private BaseMapFragment mapFragment;

    private ExpandableListView directionsListView;

    private boolean showingMap;

    private static final String TAG = "TripResultsFragment";

    private TextView arrivalDepartureTextView;
    private TextView originTextView;

    private TextView destinationTextView;
    private Button changeButton;
    private LinearLayout switchViewLayout;
    private ImageView switchViewImageView;
    private TextView switchViewTextView;

    private RoutingOptionPicker[] options = new RoutingOptionPicker[3];

    private RealtimeService realtimeService;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View view = inflater.inflate(R.layout.fragment_trip_plan_results, container, false);

        arrivalDepartureTextView = (TextView) view.findViewById(R.id.arrivalDepartureTextView);
        originTextView = (TextView) view.findViewById(R.id.originTextView);
        destinationTextView = (TextView) view.findViewById(R.id.destinationTextView);
        changeButton = (Button) view.findViewById(R.id.changeButton);

        switchViewLayout = (LinearLayout) view.findViewById(R.id.switchViewLayout);
        switchViewTextView = (TextView) view.findViewById(R.id.switchViewText);
        switchViewImageView = (ImageView) view.findViewById(R.id.switchViewImageView);

        directionsListView = (ExpandableListView) view.findViewById(R.id.directionsListView);

        options[0] = new RoutingOptionPicker(view, R.id.option1LinearLayout, R.id.option1Title, R.id.option1Duration);
        options[1] = new RoutingOptionPicker(view, R.id.option2LinearLayout, R.id.option2Title, R.id.option2Duration);
        options[2] = new RoutingOptionPicker(view, R.id.option3LinearLayout, R.id.option3Title, R.id.option3Duration);

        switchViewLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMap(!showingMap);
            }
        });

        changeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TripRequestBuilder builder = new TripRequestBuilder(getArguments());

                CustomAddress fromAddress = builder.getTo();
                CustomAddress toAddress = builder.getFrom();

                builder
                        .setFrom(fromAddress)
                        .setTo(toAddress);

                ((TripPlanActivity) getActivity()).route();
            }
        });

        realtimeService = new RealtimeServiceImpl(getActivity().getApplicationContext(), getActivity(), getArguments());

        initMap();
        initInfo(0);

        return view;
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
        mapFragment = new BaseMapFragment();

        Bundle bundle = new Bundle();
        bundle.putString(MapParams.MODE, MapParams.MODE_DIRECTIONS);

        Intent intent = new Intent().putExtras(bundle);
        getActivity().setIntent(intent);

        getChildFragmentManager().beginTransaction()
                .add(R.id.mapFragment, mapFragment).commit();
    }

    private Bundle mapBundle = new Bundle();

    private void showMap(boolean show) {

        showingMap = show;
        if (show) {
            getActivity().findViewById(R.id.mapFragment).bringToFront();
            mapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mapBundle);
            switchViewImageView.setImageResource(R.drawable.ic_action_dots);
            switchViewTextView.setText(getString(R.string.trip_plan_list_view));
        } else {
            directionsListView.bringToFront();
            switchViewImageView.setImageResource(R.drawable.ic_action_map);
            switchViewTextView.setText(getString(R.string.trip_plan_map_view));
        }
    }

    public void initInfo(int trip) {

        String startAddress = getFromAddress().getAddressLine(0);
        String endAddress = getToAddress().getAddressLine(0);

        originTextView.setText(startAddress);
        destinationTextView.setText(endAddress);

        Date date = getRequestDate();
        String format = String.format(OTPConstants.TRIP_RESULTS_TIME_STRING_FORMAT,
                getString(R.string.time_connector_before_time));
        String timeString = new SimpleDateFormat(format, Locale.getDefault()).format(date);
        arrivalDepartureTextView.setText(timeString);

        for (int i = 0; i < options.length; i++) {
            options[i].setItinerary(i);
        }

        options[trip].updateInfo();
        options[trip].updateMap();

    }

    public void displayNewResults() {
        showMap(showingMap);
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

    private CustomAddress getFromAddress() {
        return new TripRequestBuilder(getArguments()).getFrom();
    }

    private CustomAddress getToAddress() {
        return new TripRequestBuilder(getArguments()).getTo();
    }

    private Date getRequestDate() {
        return new TripRequestBuilder(getArguments()).getDateTime();
    }

    private class RoutingOptionPicker {
        LinearLayout linearLayout;
        TextView titleView;
        TextView durationView;

        Itinerary itinerary;
        int rank;

        RoutingOptionPicker(View view, int linearLayout, int titleView, int durationView) {
            this.linearLayout = (LinearLayout) view.findViewById(linearLayout);
            this.titleView = (TextView) view.findViewById(titleView);
            this.durationView = (TextView) view.findViewById(durationView);

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
            for (RoutingOptionPicker picker : options)
                picker.linearLayout.setBackgroundColor(getResources().getColor(R.color.trip_option_background));
            linearLayout.setBackgroundResource(R.drawable.bottom_line_grey_blue);
        }


        void setItinerary(int rank) {
            this.rank = rank;

            List<Itinerary> trips = getItineraries();
            if (rank >= trips.size()) {
                this.itinerary = null;
                linearLayout.setVisibility(View.GONE);
                return;
            }

            this.itinerary = trips.get(rank);
            String title = new DirectionsGenerator(itinerary.legs, getContext()).getItineraryTitle();
            String duration = formatTimeString(itinerary.startTime, itinerary.duration * 1000);
            titleView.setText(title);
            durationView.setText(duration);
        }

        void updateInfo() {
            DirectionsGenerator gen = new DirectionsGenerator(itinerary.legs, getActivity().getApplicationContext());
            List<Direction> directions = gen.getDirections();
            Direction direction_data[] = directions.toArray(new Direction[directions.size()]);

            DirectionExpandableListAdapter adapter = new DirectionExpandableListAdapter(
                    getActivity(),
                    R.layout.list_direction_item, R.layout.list_subdirection_item, direction_data);

            directionsListView.setAdapter(adapter);

            directionsListView.setGroupIndicator(null);

            realtimeService.onItinerarySelected(itinerary, rank);
        }

        private void updateMap() {
            mapBundle.putSerializable(MapParams.ITINERARY, itinerary);
            mapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mapBundle);
        }
    }
}
