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
import org.onebusaway.util.comparators.AlphanumComparator;

import android.content.ContentValues;
import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Styles of arrival times used by York Region Transit
 */
public class ArrivalsListAdapterStyleB extends ArrivalsListAdapterBase<CombinedArrivalInfoStyleB> {

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

        TextView routeNew = (TextView) view.findViewById(R.id.routeNew);
        TextView destinationNew = (TextView) view.findViewById(R.id.destinationNew);

        TextView scheduledTime1 = (TextView) view.findViewById(R.id.scheduledTime1);
        TextView arrivalTime1 = (TextView) view.findViewById(R.id.arrivalTime1);
        TextView scheduledTime2 = (TextView) view.findViewById(R.id.scheduledTime2);
        TextView arrivalTime2 = (TextView) view.findViewById(R.id.arrivalTime2);
        TextView scheduledTime3 = (TextView) view.findViewById(R.id.scheduledTime3);
        TextView arrivalTime3 = (TextView) view.findViewById(R.id.arrivalTime3);

        TableRow arrival2Row = (TableRow) view.findViewById(R.id.arrivalRow2);
        TableRow arrival3Row = (TableRow) view.findViewById(R.id.arrivalRow3);

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
        final Context context = getContext();

        for (int i = 0; i <= 2; i++) {
            if (i + 1 > combinedArrivalInfoStyleB.getArrivalInfoList().size()) {
                if (i == 1) {
                    arrival2Row.setVisibility(View.GONE);
                } else if (i == 2) {
                    arrival3Row.setVisibility(View.GONE);
                }
                continue;
            }

            ArrivalInfo arrivalRow = combinedArrivalInfoStyleB.getArrivalInfoList().get(i);
            final ObaArrivalInfo tempArrivalInfo = arrivalRow.getInfo();
            long scheduledTime = tempArrivalInfo.getScheduledArrivalTime();
            if (tempArrivalInfo.getStopSequence() == 0) {
                tempArrivalInfo.getScheduledDepartureTime();
            }

            if (i == 0) {
                scheduledTime1.setText(DateUtils.formatDateTime(context,
                        scheduledTime,
                        DateUtils.FORMAT_SHOW_TIME |
                                DateUtils.FORMAT_NO_NOON |
                                DateUtils.FORMAT_NO_MIDNIGHT));
                if (arrivalRow.getPredicted()) {
                    long eta = arrivalRow.getEta();
                    if (eta == 0) {
                        arrivalTime1.setText(R.string.stop_info_eta_now);
                    } else {
                        arrivalTime1.setText(String.valueOf(Math.abs(eta)) + " min");
                    }
                } else {
                    arrivalTime1.setText(R.string.stop_info_eta_unknown);
                }
            } else if (i == 1) {
                scheduledTime2.setText(DateUtils.formatDateTime(context,
                        scheduledTime,
                        DateUtils.FORMAT_SHOW_TIME |
                                DateUtils.FORMAT_NO_NOON |
                                DateUtils.FORMAT_NO_MIDNIGHT));
                if (arrivalRow.getPredicted()) {
                    long eta = arrivalRow.getEta();
                    if (eta == 0) {
                        arrivalTime2.setText(R.string.stop_info_eta_now);
                    } else {
                        arrivalTime2.setText(String.valueOf(Math.abs(eta)) + " min");
                    }
                } else {
                    arrivalTime2.setText(R.string.stop_info_eta_unknown);
                }
            } else {
                scheduledTime3.setText(DateUtils.formatDateTime(context,
                        scheduledTime,
                        DateUtils.FORMAT_SHOW_TIME |
                                DateUtils.FORMAT_NO_NOON |
                                DateUtils.FORMAT_NO_MIDNIGHT));
                if (arrivalRow.getPredicted()) {
                    long eta = arrivalRow.getEta();
                    if (eta == 0) {
                        arrivalTime3.setText(R.string.stop_info_eta_now);
                    } else {
                        arrivalTime3.setText(String.valueOf(Math.abs(eta)) + " min");
                    }
                } else {
                    arrivalTime3.setText(R.string.stop_info_eta_unknown);
                }
            }
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
