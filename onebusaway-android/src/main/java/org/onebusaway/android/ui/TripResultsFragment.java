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
import org.onebusaway.android.directions.util.ConversionUtils;
import org.onebusaway.android.directions.util.DirectionExpandableListAdapter;
import org.onebusaway.android.directions.util.DirectionsGenerator;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.opentripplanner.api.model.Itinerary;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripResultsFragment extends Fragment {

    private static final String TAG = "TripResultsFragment";

    private static final int LIST_TAB_POSITION = 0;
    private static final int MAP_TAB_POSITION = 1;

    private View mDirectionsFrame;
    private BaseMapFragment mMapFragment;
    private ExpandableListView mDirectionsListView;
    private View mMapFragmentFrame;
    private boolean mShowingMap = false;

    private RoutingOptionPicker[] mOptions = new RoutingOptionPicker[3];

    private Listener mListener;

    private Bundle mMapBundle = new Bundle();

    /**
     * This listener is a helper for the parent activity to handle the sliding panel,
     * which interacts with sliding views (i.e., list view and map view) in subtle ways.
     */
    public interface Listener {

        /**
         * Called when the result views have been created
         *
         * @param containerView the view which contains the directions list and the map
         * @param listView the directions list view
         * @param mapView the map frame
         */
        void onResultViewCreated(View containerView, ListView listView, View mapView);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View view = inflater.inflate(R.layout.fragment_trip_plan_results, container, false);

        mDirectionsFrame = view.findViewById(R.id.directionsFrame);
        mDirectionsListView = (ExpandableListView) view.findViewById(R.id.directionsListView);
        mMapFragmentFrame = view.findViewById(R.id.mapFragment);

        mOptions[0] = new RoutingOptionPicker(view, R.id.option1LinearLayout, R.id.option1Title, R.id.option1Duration, R.id.option1Interval);
        mOptions[1] = new RoutingOptionPicker(view, R.id.option2LinearLayout, R.id.option2Title, R.id.option2Duration, R.id.option2Interval);
        mOptions[2] = new RoutingOptionPicker(view, R.id.option3LinearLayout, R.id.option3Title, R.id.option3Duration, R.id.option3Interval);

        int rank = getArguments().getInt(OTPConstants.SELECTED_ITINERARY); // defaults to 0
        mShowingMap = getArguments().getBoolean(OTPConstants.SHOW_MAP);

        initInfoAndMap(rank);

        TabLayout tabLayout = (TabLayout) view.findViewById(R.id.tab_layout_switch_view);
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean show = (tab.getPosition() == MAP_TAB_POSITION);
                showMap(show);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // unused
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // unused
            }
        });

        setTabDrawable(tabLayout.getTabAt(LIST_TAB_POSITION), R.drawable.ic_list);
        setTabDrawable(tabLayout.getTabAt(MAP_TAB_POSITION), R.drawable.ic_arrivals_styleb_action_map);

        if (mShowingMap) {
            tabLayout.getTabAt(MAP_TAB_POSITION).select();
        }

        if (mListener != null) {
            mListener.onResultViewCreated(mDirectionsFrame, mDirectionsListView, mMapFragmentFrame);
        }

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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

    /**
     * Set the listener for this fragment.
     *
     * @param listener the new listener
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Get whether map is showing.
     *
     * @return true if map is showing, false otherwise
     */
    public boolean isMapShowing() {
        return mShowingMap;
    }

    private void setTabDrawable(TabLayout.Tab tab, @DrawableRes int res) {
        View view = tab.getCustomView();
        TextView tv = ((TextView) view.findViewById(android.R.id.text1));

        Drawable drawable = getResources().getDrawable(res);

        int dp = (int) getResources().getDimension(R.dimen.trip_results_icon_size);
        drawable.setBounds(0, 0, dp, dp);

        drawable.setColorFilter(getResources().getColor(R.color.trip_option_icon_tint), PorterDuff.Mode.SRC_IN);

        tv.setCompoundDrawables(drawable, null, null, null);
    }

    private void initMap(int trip) {
        Itinerary itinerary = getItineraries().get(trip);
        mMapBundle.putString(MapParams.MODE, MapParams.MODE_DIRECTIONS);
        mMapBundle.putSerializable(MapParams.ITINERARY, itinerary);

        Intent intent = new Intent().putExtras(mMapBundle);
        getActivity().setIntent(intent);

        FragmentManager fm = getChildFragmentManager();
        if (mMapFragment == null) {
            // First check to see if an instance of BaseMapFragment already exists
            mMapFragment = (BaseMapFragment) fm.findFragmentByTag(BaseMapFragment.TAG);

            if (mMapFragment == null) {
                // No existing fragment was found, so create a new one
                Log.d(TAG, "Creating new BaseMapFragment");
                mMapFragment = BaseMapFragment.newInstance();
                fm.beginTransaction()
                        .add(R.id.mapFragment, mMapFragment, BaseMapFragment.TAG)
                        .commit();
            }
        }
    }

    private void showMap(boolean show) {

        mShowingMap = show;
        if (show) {
            mMapFragmentFrame.bringToFront();
            mMapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mMapBundle);
        } else {
            mDirectionsListView.bringToFront();
        }

        getArguments().putBoolean(OTPConstants.SHOW_MAP, mShowingMap);
    }

    private void initInfoAndMap(int trip) {

        initMap(trip);

        for (int i = 0; i < mOptions.length; i++) {
            mOptions[i].setItinerary(i);
        }

        mOptions[trip].select();

        showMap(mShowingMap);
    }

    public void displayNewResults() {
        int rank = getArguments().getInt(OTPConstants.SELECTED_ITINERARY);
        showMap(mShowingMap);
        initInfoAndMap(rank);
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
        int rank;

        RoutingOptionPicker(View view, int linearLayout, int titleView, int durationView, int intervalView) {
            this.linearLayout = (LinearLayout) view.findViewById(linearLayout);
            this.titleView = (TextView) view.findViewById(titleView);
            this.durationView = (TextView) view.findViewById(durationView);
            this.intervalView = (TextView) view.findViewById(intervalView);

            this.linearLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RoutingOptionPicker.this.select();
                }
            });
        }

        void select() {
            for (RoutingOptionPicker picker : mOptions) {
                picker.linearLayout.setBackgroundColor(getResources().getColor(R.color.trip_option_background));
            }
            linearLayout.setBackgroundResource(R.drawable.trip_option_selected_item);

            getArguments().putInt(OTPConstants.SELECTED_ITINERARY, rank);

            updateInfo();
            updateMap();
        }


        void setItinerary(int rank) {
            List<Itinerary> trips = getItineraries();
            if (rank >= trips.size()) {
                this.itinerary = null;
                linearLayout.setVisibility(View.GONE);
                return;
            }

            this.itinerary = trips.get(rank);
            this.rank = rank;

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

                RealtimeService.start(getActivity(), getArguments());
            }
        }

        void updateMap() {
            mMapBundle.putSerializable(MapParams.ITINERARY, itinerary);
            mMapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mMapBundle);
        }
    }
}
