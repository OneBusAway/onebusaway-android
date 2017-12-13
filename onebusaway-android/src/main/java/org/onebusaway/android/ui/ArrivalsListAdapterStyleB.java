/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * Sean J. Barbeau (sjbarbeau@gmail.com),
 * York Region Transit / VIVA,
 * Microsoft Corporation
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
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.EmbeddedSocialUtils;
import org.onebusaway.android.util.UIUtils;
import org.onebusaway.util.comparators.AlphanumComparator;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
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
                    ArrivalInfoUtils.convertObaArrivalInfo(getContext(),
                            arrivals, routesFilter, currentTime, true);

            // Sort list by route and headsign, in that order
            Collections.sort(list, new Comparator<ArrivalInfo>() {
                @Override
                public int compare(ArrivalInfo s1, ArrivalInfo s2) {
                    int routeCompare = mAlphanumComparator
                            .compare(s1.getInfo().getRouteId(), s2.getInfo().getRouteId());
                    if (routeCompare != 0) {
                        return routeCompare;
                    } else {
                        // Compare headsigns when the route is the same
                        return mAlphanumComparator
                                .compare(s1.getInfo().getHeadsign(), s2.getInfo().getHeadsign());
                    }
                }
            });

            if (list.size() > 0) {
                ArrayList<CombinedArrivalInfoStyleB> newList
                        = new ArrayList<CombinedArrivalInfoStyleB>();
                String currentRouteName = null;
                String currentHeadsign = null;
                CombinedArrivalInfoStyleB cArrivalInfo = new CombinedArrivalInfoStyleB();
                for (int i = 0; i < list.size(); i++) {
                    if (currentRouteName == null) {
                        // Initialize fields
                        currentRouteName = list.get(i).getInfo().getRouteId();
                        currentHeadsign = list.get(i).getInfo().getHeadsign();
                    } else {
                        if (!currentRouteName.equals(list.get(i).getInfo().getRouteId()) ||
                                !currentHeadsign.equals(list.get(i).getInfo().getHeadsign())) {
                            // Create a new card
                            newList.add(cArrivalInfo);
                            cArrivalInfo = new CombinedArrivalInfoStyleB();
                            currentRouteName = list.get(i).getInfo().getRouteId();
                            currentHeadsign = list.get(i).getInfo().getHeadsign();
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

        TextView routeName = (TextView) view.findViewById(R.id.routeName);
        TextView destination = (TextView) view.findViewById(R.id.routeDestination);

        // TableLayout that we will fill with TableRows of arrival times
        TableLayout arrivalTimesLayout = (TableLayout) view.findViewById(R.id.arrivalTimeLayout);
        arrivalTimesLayout.removeAllViews();

        Resources r = view.getResources();

        ImageButton starBtn = (ImageButton) view.findViewById(R.id.route_star);
        starBtn.setColorFilter(r.getColor(R.color.theme_primary));

        ImageButton mapImageBtn = (ImageButton) view.findViewById(R.id.mapImageBtn);
        mapImageBtn.setColorFilter(r.getColor(R.color.theme_primary));

        ImageButton discussBtn = (ImageButton) view.findViewById(R.id.route_discussion);
        discussBtn.setColorFilter(r.getColor(R.color.theme_primary));

        ImageButton routeMoreInfo = (ImageButton) view.findViewById(R.id.route_more_info);
        routeMoreInfo.setColorFilter(r.getColor(R.color.switch_thumb_normal_material_dark));

        starBtn.setImageResource(stopInfo.isRouteAndHeadsignFavorite() ?
                R.drawable.focus_star_on :
                R.drawable.focus_star_off);

        starBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show dialog for setting route favorite
                RouteFavoriteDialogFragment dialog = new RouteFavoriteDialogFragment.Builder(
                        stopInfo.getInfo().getRouteId(), stopInfo.getInfo().getHeadsign())
                        .setRouteShortName(stopInfo.getInfo().getShortName())
                        .setRouteLongName(stopInfo.getInfo().getRouteLongName())
                        .setStopId(stopInfo.getInfo().getStopId())
                        .setFavorite(!stopInfo.isRouteAndHeadsignFavorite())
                        .build();

                dialog.setCallback(new RouteFavoriteDialogFragment.Callback() {
                    @Override
                    public void onSelectionComplete(boolean savedFavorite) {
                        if (savedFavorite) {
                            mFragment.refreshLocal();
                        }
                    }
                });
                dialog.show(mFragment.getFragmentManager(), RouteFavoriteDialogFragment.TAG);
            }
        });

        // Setup map
        mapImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFragment.showRouteOnMap(stopInfo);
            }
        });

        // Setup discussion
        discussBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        context.getString(R.string.analytics_action_button_press),
                        context.getString(R.string.analytics_label_button_press_social_route_style_b));
                mFragment.openRouteDiscussion(arrivalInfo.getRouteId());
            }
        });

        ObaRegion currentRegion = Application.get().getCurrentRegion();
        if (currentRegion != null && !EmbeddedSocialUtils.isSocialEnabled(context)) {
            discussBtn.setVisibility(View.GONE);
        }

        // Setup more
        routeMoreInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFragment.showListItemMenu(view, stopInfo);
            }
        });

        routeName.setText(arrivalInfo.getShortName());
        destination.setText(UIUtils.formatDisplayText(arrivalInfo.getHeadsign()));

        // Loop through the arrival times and create the TableRows that contains the data
        for (int i = 0; i < combinedArrivalInfoStyleB.getArrivalInfoList().size(); i++) {
            final ArrivalInfo arrivalRow = combinedArrivalInfoStyleB.getArrivalInfoList().get(i);
            final ObaArrivalInfo tempArrivalInfo = arrivalRow.getInfo();
            long scheduledTime = tempArrivalInfo.getScheduledArrivalTime();

            // Create a new row to be added
            final TableRow tr = (TableRow) inflater
                    .inflate(R.layout.arrivals_list_tr_template_style_b, null);

            // Layout and views to inflate from XML templates
            RelativeLayout layout;
            TextView scheduleView, estimatedView, statusView;
            View divider;

            if (i == 0) {
                // Use larger styled layout/view for next arrival time
                layout = (RelativeLayout) inflater
                        .inflate(R.layout.arrivals_list_rl_template_style_b_large, null);
                scheduleView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_schedule_large, null);
                estimatedView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_estimated_large, null);
                statusView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_status_large, null);
            } else {
                // Use smaller styled layout/view for further out times
                layout = (RelativeLayout) inflater
                        .inflate(R.layout.arrivals_list_rl_template_style_b_small, null);
                scheduleView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_schedule_small, null);
                estimatedView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_estimated_small, null);
                statusView = (TextView) inflater
                        .inflate(R.layout.arrivals_list_tv_template_style_b_status_small, null);
            }

            // Set arrival times and status in views
            scheduleView.setText(UIUtils.formatTime(context, scheduledTime));
            if (arrivalRow.getPredicted()) {
                long eta = arrivalRow.getEta();
                if (eta == 0) {
                    estimatedView.setText(R.string.stop_info_eta_now);
                } else {
                    estimatedView.setText(eta + " min");
                }
            } else {
                estimatedView.setText(R.string.stop_info_eta_unknown);
            }
            statusView.setText(arrivalRow.getStatusText());
            int colorCode = arrivalRow.getColor();
            statusView.setBackgroundResource(R.drawable.round_corners_style_b_status);
            GradientDrawable d = (GradientDrawable) statusView.getBackground();
            d.setColor(context.getResources().getColor(colorCode));

            int alpha;
            if (i == 0) {
                // Set next arrival
                alpha = (int) (1.0f * 255);  // X percent transparency
            } else {
                // Set smaller rows
                alpha = (int) (.35f * 255);  // X percent transparency
            }
            d.setAlpha(alpha);
            // Set text color w/ alpha, but increase it a bit to give text better contrast
            estimatedView.setTextColor(UIUtils.getTransparentColor(
                    context.getResources().getColor(colorCode), alpha * 2));

            // Set padding on status view
            int pSides = UIUtils.dpToPixels(context, 5);
            int pTopBottom = UIUtils.dpToPixels(context, 2);
            statusView.setPadding(pSides, pTopBottom, pSides, pTopBottom);

            // Add TextViews to layout
            layout.addView(scheduleView);
            layout.addView(statusView);
            layout.addView(estimatedView);

            // Make sure the TextViews align left/center/right of parent relative layout
            RelativeLayout.LayoutParams params1 = (RelativeLayout.LayoutParams) scheduleView
                    .getLayoutParams();
            params1.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params1.addRule(RelativeLayout.CENTER_VERTICAL);
            scheduleView.setLayoutParams(params1);

            RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams) statusView
                    .getLayoutParams();
            params2.addRule(RelativeLayout.CENTER_IN_PARENT);
            // Give status view a little extra margin
            int p = UIUtils.dpToPixels(context, 3);
            params2.setMargins(p, p, p, p);
            statusView.setLayoutParams(params2);

            RelativeLayout.LayoutParams params3 = (RelativeLayout.LayoutParams) estimatedView
                    .getLayoutParams();
            params3.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params3.addRule(RelativeLayout.CENTER_VERTICAL);
            estimatedView.setLayoutParams(params3);

            // Add layout to TableRow
            tr.addView(layout);

            // Add the divider, if its not the first row
            if (i != 0) {
                int dividerHeight = UIUtils.dpToPixels(context, 1);
                divider = inflater.inflate(R.layout.arrivals_list_divider_template_style_b, null);
                divider.setLayoutParams(
                        new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                                dividerHeight));
                arrivalTimesLayout.addView(divider);
            }

            // Add click listener
            tr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFragment.showListItemMenu(tr, arrivalRow);
                }
            });

            // Add TableRow to container layout
            arrivalTimesLayout.addView(tr,
                    new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.MATCH_PARENT));
        }

        // Show or hide reminder for this trip
        ContentValues values = null;
        if (mTripsForStop != null) {
            values = mTripsForStop.getValues(arrivalInfo.getTripId());
        }
        if (values != null) {
            String reminderName = values.getAsString(ObaContract.Trips.NAME);

            TextView reminder = (TextView) view.findViewById(R.id.reminder);
            if (reminderName.length() == 0) {
                reminderName = context.getString(R.string.trip_info_noname);
            }
            reminder.setText(reminderName);
            Drawable d = reminder.getCompoundDrawables()[0];
            d = DrawableCompat.wrap(d);
            DrawableCompat.setTint(d.mutate(), view.getResources().getColor(R.color.theme_primary));
            reminder.setCompoundDrawables(d, null, null, null);
            reminder.setVisibility(View.VISIBLE);
        } else {
            // Explicitly set reminder to invisible because we might be reusing
            // this view.
            View reminder = view.findViewById(R.id.reminder);
            reminder.setVisibility(View.GONE);
        }
    }
}
