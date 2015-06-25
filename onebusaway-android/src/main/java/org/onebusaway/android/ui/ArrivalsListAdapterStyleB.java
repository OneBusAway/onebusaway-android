/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), Sean J. Barbeau (sjbarbeau@gmail.com),
 * York Region Transit / VIVA
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
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.MyTextUtils;
import org.onebusaway.android.util.UIHelp;
import org.onebusaway.util.comparators.AlphanumComparator;

import android.content.ContentValues;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Styles of arrival times used by York Region Transit
 */
public class ArrivalsListAdapterStyleB extends ArrivalsListAdapterBase<CombinedArrivalInfoStyleB> {

    private static final String TAG = "ArrivalsListAdapStyleB";

    AlphanumComparator mAlphanumComparator = new AlphanumComparator();

    ArrivalsListFragment mFragment;

    public ArrivalsListAdapterStyleB(Context context) {
        super(context, R.layout.arrivals_list_item_style_b);
    }

    public void setFragment(ArrivalsListFragment fragment) {
        mFragment = fragment;
    }

    /**
     * Sets the data to be used with the adapter
     *
     * @param routesFilter routeIds to filter for
     * @param currentTime  current time in milliseconds
     */
    public void setData(ObaArrivalInfo[] arrivals, ArrayList<String> routesFilter,
            long currentTime) {
        if (arrivals != null) {
            ArrayList<ArrivalInfo> list =
                    ArrivalInfo.convertObaArrivalInfo(getContext(),
                            arrivals, routesFilter, currentTime);

            // Sort list by route
            Collections.sort(list, new Comparator<ArrivalInfo>() {
                @Override
                public int compare(ArrivalInfo s1, ArrivalInfo s2) {
                    return mAlphanumComparator
                            .compare(s1.getInfo().getRouteId(), s2.getInfo().getRouteId());
                }
            });

            if (list.size() > 0) {
                ArrayList<CombinedArrivalInfoStyleB> newList
                        = new ArrayList<CombinedArrivalInfoStyleB>();
                String currentRouteName = null;
                CombinedArrivalInfoStyleB cArrivalInfo = new CombinedArrivalInfoStyleB();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getEta() < 0)
                        continue;

                    if (currentRouteName == null) {
                        currentRouteName = list.get(i).getInfo().getRouteId();
                    } else {
                        if (!currentRouteName.equals(list.get(i).getInfo().getRouteId())) {
                            newList.add(cArrivalInfo);
                            cArrivalInfo = new CombinedArrivalInfoStyleB();
                            currentRouteName = list.get(i).getInfo().getRouteId();
                        }
                    }
                    cArrivalInfo.getArrivalInfoList().add(list.get(i));
                }
                if (!cArrivalInfo.getArrivalInfoList().isEmpty()) {
                    newList.add(cArrivalInfo);
                    setData(newList);
                    return;
                }
            }
        }
        // If we get this far, we don't have any data to use
        setData(null);
    }

    @Override
    protected void initView(final View view, CombinedArrivalInfoStyleB combinedArrivalInfoStyleB) {
        final ArrivalInfo stopInfo = combinedArrivalInfoStyleB.getArrivalInfoList().get(0);
        final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();
        final Context context = getContext();

        LayoutInflater inflater = LayoutInflater.from(context);

        TextView routeNew = (TextView) view.findViewById(R.id.routeNew);
        TextView destinationNew = (TextView) view.findViewById(R.id.destinationNew);

        // TableLayout that we will fill with TableRows of arrival times
        TableLayout arrivalTimesLayout = (TableLayout) view.findViewById(R.id.arrivalTimeLayout);
        arrivalTimesLayout.removeAllViews();

        ImageView infoImageView = (ImageView) view.findViewById(R.id.infoImageView);
        infoImageView.setColorFilter(infoImageView.getResources().getColor(R.color.theme_primary));
        ImageView mapImageView = (ImageView) view.findViewById(R.id.mapImageView);
        mapImageView.setColorFilter(mapImageView.getResources().getColor(R.color.theme_primary));

        infoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFragment.showListItemMenu(view, stopInfo);
            }
        });

        mapImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HomeActivity.start(getContext(), stopInfo.getInfo().getRouteId());
            }
        });

        routeNew.setText(arrivalInfo.getShortName());
        destinationNew.setText(MyTextUtils.toTitleCase(arrivalInfo.getHeadsign()));

        // Loop through the arrival times and create the TableRows that contains the data
        for (int i = 0; i < combinedArrivalInfoStyleB.getArrivalInfoList().size(); i++) {
            ArrivalInfo arrivalRow = combinedArrivalInfoStyleB.getArrivalInfoList().get(i);
            final ObaArrivalInfo tempArrivalInfo = arrivalRow.getInfo();
            long scheduledTime = tempArrivalInfo.getScheduledArrivalTime();

            // Create a new row to be added
            TableRow tr = new TableRow(getContext());
            tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.MATCH_PARENT));
            tr.setFocusable(false);
            tr.setClickable(false);

            // Layout and views to inflate from XML templates
            RelativeLayout layout;
            TextView scheduleView, estimatedView;
            View divider;

            if (i == 0) {
                // Use larger styled layout/view for next arrival time
                layout = (RelativeLayout) inflater
                        .inflate(R.layout.arrivals_list_rl_template_style_b_large, null);
                scheduleView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_schedule_large, null);
                estimatedView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_estimated_large, null);
            } else {
                // Use smaller styled layout/view for further out times
                layout = (RelativeLayout) inflater
                        .inflate(R.layout.arrivals_list_rl_template_style_b_small, null);
                scheduleView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_schedule_small, null);
                estimatedView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_estimated_small, null);
            }

            // Add arrival times to schedule/estimated views
            scheduleView.setText(DateUtils.formatDateTime(context,
                    scheduledTime,
                    DateUtils.FORMAT_SHOW_TIME |
                            DateUtils.FORMAT_NO_NOON |
                            DateUtils.FORMAT_NO_MIDNIGHT));
            if (arrivalRow.getPredicted()) {
                long eta = arrivalRow.getEta();
                if (eta == 0) {
                    estimatedView.setText(R.string.stop_info_eta_now);
                } else {
                    estimatedView.setText(String.valueOf(Math.abs(eta)) + " min");
                }
            } else {
                estimatedView.setText(R.string.stop_info_eta_unknown);
            }

            // Add TextViews to layout
            layout.addView(scheduleView);
            layout.addView(estimatedView);

            // Make sure the TextViews align right/left of parent relative layout
            RelativeLayout.LayoutParams params1 = (RelativeLayout.LayoutParams) scheduleView
                    .getLayoutParams();
            params1.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            scheduleView.setLayoutParams(params1);

            RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams) estimatedView
                    .getLayoutParams();
            params2.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            estimatedView.setLayoutParams(params2);

            // Add layout to TableRow
            tr.addView(layout);

            // Add the divider, if its not the first row
            if (i != 0) {
                int dividerHeight = UIHelp.dpToPixels(context, 1);
                divider = inflater.inflate(R.layout.arrivals_list_divider_template_style_b, null);
                divider.setLayoutParams(
                        new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                                dividerHeight));
                arrivalTimesLayout.addView(divider);
            }

            // Add TableRow to container layout
            arrivalTimesLayout.addView(tr,
                    new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.MATCH_PARENT));
        }

        ContentValues values = null;
        if (mTripsForStop != null) {
            values = mTripsForStop.getValues(arrivalInfo.getTripId());
        }
        if (values != null) {
            String tripName = values.getAsString(ObaContract.Trips.NAME);

            TextView tripInfo = (TextView) view.findViewById(R.id.trip_info);
            if (tripName.length() == 0) {
                tripName = context.getString(R.string.trip_info_noname);
            }
            tripInfo.setText(tripName);
            tripInfo.setVisibility(View.VISIBLE);
        } else {
            // Explicitly set this to invisible because we might be reusing
            // this view.
            View tripInfo = view.findViewById(R.id.trip_info);
            tripInfo.setVisibility(View.GONE);
        }
    }
}
