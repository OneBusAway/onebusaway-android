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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.LinkedHashSet;

import com.google.android.material.tabs.TabLayout;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.model.Direction;
import org.onebusaway.android.directions.realtime.RealtimeService;
import org.onebusaway.android.directions.util.DirectionExpandableListAdapter;
import org.onebusaway.android.directions.util.DirectionsGenerator;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.ObaMapFragment;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripResultsFragment extends Fragment {

    private static final String TAG = "TripResultsFragment";

    private static final int LIST_TAB_POSITION = 0;
    private static final int MAP_TAB_POSITION = 1;

    private View mDirectionsFrame;
    private ObaMapFragment mMapFragment;
    private ExpandableListView mDirectionsListView;
    private View mMapFragmentFrame;
    private boolean mShowingMap = false;

    private HorizontalScrollView mOptionsScrollView;
    private LinearLayout mOptionsContainer;
    private int mSelectedIndex = 0;

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

        mOptionsScrollView = view.findViewById(R.id.tripOptionsScrollView);
        mOptionsContainer = view.findViewById(R.id.tripOptionsContainer);

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
            // First check to see if an instance of the map fragment already exists
            mMapFragment = (ObaMapFragment) fm.findFragmentByTag(ObaMapFragment.TAG);

            if (mMapFragment == null) {
                // No existing fragment was found, so create a new one
                Log.d(TAG, "Creating new ObaMapFragment");
                mMapFragment = ObaMapFragment.newInstance();
                fm.beginTransaction()
                        .add(R.id.mapFragment, mMapFragment.asFragment(), ObaMapFragment.TAG)
                        .commit();
            }
        }
    }

    private void showMap(boolean show) {

        mShowingMap = show;
        if (show) {
            mMapFragmentFrame.bringToFront();
            if (mMapFragment != null) {
                mMapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mMapBundle);
            }
        } else {
            mDirectionsListView.bringToFront();
        }

        getArguments().putBoolean(OTPConstants.SHOW_MAP, mShowingMap);
    }

    private void initInfoAndMap(int trip) {
        List<Itinerary> itineraries = getItineraries();
        if (itineraries == null || itineraries.isEmpty()) {
            return;
        }

        if (trip >= itineraries.size()) {
            trip = 0;
        }

        initMap(trip);

        mSelectedIndex = trip;
        populateTripOptions(itineraries);

        selectOption(trip);

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

    private String toCompactTime(long ms) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(ms));
    }

    private String toCompactDuration(double durationSec) {
        long totalMin = (long) (durationSec / 60);
        long h = totalMin / 60;
        long m = totalMin % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return m + "m";
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

    private void selectOption(int position) {
        if (position < 0) {
            return;
        }

        List<Itinerary> itineraries = getItineraries();
        if (itineraries == null || position >= itineraries.size()) {
            return;
        }

        mSelectedIndex = position;
        getArguments().putInt(OTPConstants.SELECTED_ITINERARY, position);

        updateOptionStyles(itineraries);
        scrollToSelectedCard(position);

        Itinerary itinerary = itineraries.get(position);
        updateInfo(itinerary);
        updateMap(itinerary);
    }

    private void scrollToSelectedCard(int position) {
        if (mOptionsScrollView == null || mOptionsContainer == null) {
            return;
        }
        if (position < 0 || position >= mOptionsContainer.getChildCount()) {
            return;
        }
        View card = mOptionsContainer.getChildAt(position);
        card.post(() -> mOptionsScrollView.smoothScrollTo(card.getLeft(), 0));
    }

    private void updateInfo(Itinerary itinerary) {
        DirectionsGenerator gen = new DirectionsGenerator(itinerary.legs, getActivity().getApplicationContext());
        List<Direction> directions = gen.getDirections();
        Direction[] directionData = directions.toArray(new Direction[0]);

        DirectionExpandableListAdapter adapter = new DirectionExpandableListAdapter(
                getActivity(),
                R.layout.list_direction_item, R.layout.list_subdirection_item, directionData);

        mDirectionsListView.setAdapter(adapter);
        mDirectionsListView.setGroupIndicator(null);

        Context context = Application.get().getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = manager.getNotificationChannel(Application.CHANNEL_TRIP_PLAN_UPDATES_ID);
            if (channel.getImportance() != NotificationManager.IMPORTANCE_NONE) {
                RealtimeService.start(getActivity(), getArguments());
            }
        } else {
            if (Application.getPrefs()
                    .getBoolean(getString(R.string.preference_key_trip_plan_notifications), true)) {
                RealtimeService.start(getActivity(), getArguments());
            }
        }
    }

    private void updateMap(Itinerary itinerary) {
        mMapBundle.putSerializable(MapParams.ITINERARY, itinerary);
        if (mMapFragment != null) {
            mMapFragment.setMapMode(MapParams.MODE_DIRECTIONS, mMapBundle);
        }
    }

    private void populateTripOptions(List<Itinerary> itineraries) {
        mOptionsContainer.removeAllViews();
        Context ctx = getActivity();
        if (ctx == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardWidth = (int) (screenWidth * 0.72);
        float density = getResources().getDisplayMetrics().density;
        int iconSize = (int) (18 * density);

        for (int i = 0; i < itineraries.size(); i++) {
            Itinerary itinerary = itineraries.get(i);
            View card = LayoutInflater.from(ctx).inflate(R.layout.item_trip_option, mOptionsContainer, false);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    cardWidth, LinearLayout.LayoutParams.MATCH_PARENT);
            lp.setMarginEnd((int) (4 * density));
            card.setLayoutParams(lp);

            bindOptionCard(card, itinerary, i, ctx, iconSize);
            mOptionsContainer.addView(card);
        }
    }

    private void bindOptionCard(View card, Itinerary itinerary, int position,
                                Context ctx, int iconSize) {
        LinearLayout linearLayout = card.findViewById(R.id.tripOptionLinearLayout);
        LinearLayout modeChainLayout = card.findViewById(R.id.tripOptionModeChain);
        TextView titleView = card.findViewById(R.id.tripOptionTitle);
        View dividerView = card.findViewById(R.id.tripOptionDivider);
        TextView etaView = card.findViewById(R.id.tripOptionEta);
        TextView durationView = card.findViewById(R.id.tripOptionDuration);

        DirectionsGenerator gen = new DirectionsGenerator(itinerary.legs, ctx);
        String title = gen.getItineraryTitle();
        String duration = toCompactDuration(itinerary.duration);

        long startMs = Long.parseLong(itinerary.startTime);
        long endMs = startMs + (long) (itinerary.duration * 1000);
        String eta = "ETA " + toCompactTime(endMs);

        boolean isSelected = (position == mSelectedIndex);
        int selectedTextColor = getResources().getColor(R.color.trip_plan_header_text_selected);
        int defaultTextColor = getResources().getColor(R.color.header_text_color);
        int fadedTextColor = getResources().getColor(R.color.header_text_faded_color);
        int textColor = isSelected ? selectedTextColor : defaultTextColor;
        int iconColor = isSelected ? selectedTextColor
                : getResources().getColor(R.color.trip_option_icon_tint);

        modeChainLayout.removeAllViews();
        LinkedHashSet<String> seenModes = new LinkedHashSet<>();
        for (Leg leg : itinerary.legs) {
            if (seenModes.contains(leg.mode)) continue;
            seenModes.add(leg.mode);

            TraverseMode mode = TraverseMode.valueOf(leg.mode);
            int modeRes = DirectionsGenerator.getModeIcon(new TraverseModeSet(mode));
            if (modeRes == -1) continue;

            if (modeChainLayout.getChildCount() > 0) {
                TextView sep = new TextView(ctx);
                sep.setText(" > ");
                sep.setTextSize(14);
                sep.setTextColor(iconColor);
                modeChainLayout.addView(sep);
            }
            ImageView iv = new ImageView(ctx);
            iv.setImageResource(modeRes);
            iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            iv.setColorFilter(iconColor);
            modeChainLayout.addView(iv);
        }

        // Duration bold and large, right after icons
        TextView durationInChain = new TextView(ctx);
        durationInChain.setText("   " + duration);
        durationInChain.setTextSize(16);
        durationInChain.setTypeface(null, Typeface.BOLD);
        durationInChain.setTextColor(textColor);
        modeChainLayout.addView(durationInChain);

        titleView.setText(title);
        etaView.setText(eta);
        durationView.setText(duration + " travel");

        int dividerColor = 0x40808080;
        if (isSelected) {
            linearLayout.setBackgroundResource(R.drawable.trip_option_selected_item);
            titleView.setTextColor(selectedTextColor);
            etaView.setTextColor(selectedTextColor);
            durationView.setTextColor(selectedTextColor);
        } else {
            linearLayout.setBackgroundColor(getResources().getColor(R.color.trip_plan_card_background));
            titleView.setTextColor(fadedTextColor);
            etaView.setTextColor(fadedTextColor);
            durationView.setTextColor(fadedTextColor);
        }
        dividerView.setBackgroundColor(dividerColor);

        card.setOnClickListener(v -> selectOption(position));
    }

    private void updateOptionStyles(List<Itinerary> itineraries) {
        if (mOptionsContainer == null || itineraries == null) return;
        Context ctx = getActivity();
        if (ctx == null) return;

        float density = getResources().getDisplayMetrics().density;
        int iconSize = (int) (18 * density);

        for (int i = 0; i < mOptionsContainer.getChildCount() && i < itineraries.size(); i++) {
            View card = mOptionsContainer.getChildAt(i);
            bindOptionCard(card, itineraries.get(i), i, ctx, iconSize);
        }
    }
}
