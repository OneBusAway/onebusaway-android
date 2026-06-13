/*
 * Copyright (C) 2026 University of South Florida
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
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.onebusaway.android.R;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.SimpleCursorAdapter;

/**
 * Adapter for starred stops list that displays arrival badges inline.
 */
class StarredStopsAdapter extends SimpleCursorAdapter {

    private static final int BADGE_PADDING_H_DP = 8;

    private static final int BADGE_PADDING_V_DP = 4;

    private static final int BADGE_MARGIN_END_DP = 6;

    private static final int BADGE_CORNER_RADIUS_DP = 4;

    private static final int BADGE_TEXT_SIZE_SP = 12;

    private HashMap<String, ArrayList<ArrivalInfo>> mArrivalsData;

    StarredStopsAdapter(Context context) {
        super(context,
                R.layout.starred_stop_list_item,
                null,
                new String[]{
                        ObaContract.Stops.UI_NAME,
                        ObaContract.Stops.DIRECTION,
                        ObaContract.Stops.FAVORITE
                },
                new int[]{
                        R.id.stop_name,
                        R.id.direction,
                        R.id.stop_favorite
                },
                0);

        setViewBinder(new ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == QueryUtils.StopList.Columns.COL_FAVORITE) {
                    ImageView favorite = view.findViewById(R.id.stop_favorite);
                    if (cursor.getInt(columnIndex) == 1) {
                        favorite.setVisibility(View.VISIBLE);
                        favorite.setColorFilter(
                                ContextCompat.getColor(context, R.color.navdrawer_icon_tint));
                    } else {
                        favorite.setVisibility(View.GONE);
                    }
                    return true;
                } else if (columnIndex == QueryUtils.StopList.Columns.COL_DIRECTION) {
                    UIUtils.setStopDirection(view.findViewById(R.id.direction),
                            cursor.getString(columnIndex), true);
                    return true;
                }
                return false;
            }
        });
    }

    void setArrivalsData(HashMap<String, ArrayList<ArrivalInfo>> data) {
        mArrivalsData = data;
        notifyDataSetChanged();
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        super.bindView(view, context, cursor);

        String stopId = cursor.getString(QueryUtils.StopList.Columns.COL_ID);
        HorizontalScrollView arrivalsScroll = view.findViewById(R.id.arrivals_scroll);
        LinearLayout arrivalsContainer = view.findViewById(R.id.arrivals_container);
        ProgressBar arrivalsLoading = view.findViewById(R.id.arrivals_loading);

        if (mArrivalsData == null) {
            // Still loading
            arrivalsScroll.setVisibility(View.GONE);
            arrivalsLoading.setVisibility(View.VISIBLE);
            return;
        }

        arrivalsLoading.setVisibility(View.GONE);
        ArrayList<ArrivalInfo> arrivals = mArrivalsData.get(stopId);

        if (arrivals == null || arrivals.isEmpty()) {
            arrivalsScroll.setVisibility(View.GONE);
            return;
        }

        arrivalsScroll.setVisibility(View.VISIBLE);
        arrivalsContainer.removeAllViews();

        float density = context.getResources().getDisplayMetrics().density;
        int paddingH = (int) (BADGE_PADDING_H_DP * density);
        int paddingV = (int) (BADGE_PADDING_V_DP * density);
        int marginEnd = (int) (BADGE_MARGIN_END_DP * density);
        int cornerRadius = (int) (BADGE_CORNER_RADIUS_DP * density);

        for (ArrivalInfo arrival : arrivals) {
            TextView badge = new TextView(context);

            String routeName = UIUtils.getRouteDisplayName(arrival.getInfo());
            long eta = arrival.getEta();
            String etaText;
            if (eta <= 0) {
                etaText = context.getString(R.string.starred_stop_arrival_now);
            } else {
                etaText = context.getString(R.string.starred_stop_arrival_min, (int) eta);
            }
            badge.setText(context.getString(R.string.starred_stop_arrival_badge,
                    routeName, etaText));
            String contentDesc;
            if (eta <= 0) {
                contentDesc = context.getString(
                        R.string.arrival_badge_content_description_now, routeName);
            } else {
                contentDesc = context.getString(
                        R.string.arrival_badge_content_description_min, routeName, (int) eta);
            }
            badge.setContentDescription(contentDesc);
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

            badge.setTextSize(BADGE_TEXT_SIZE_SP);
            badge.setTextColor(ContextCompat.getColor(context, android.R.color.white));
            badge.setPadding(paddingH, paddingV, paddingH, paddingV);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(context, getBadgeColor(arrival.getColor())));
            bg.setCornerRadius(cornerRadius);
            badge.setBackground(bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(marginEnd);
            badge.setLayoutParams(params);

            arrivalsContainer.addView(badge);
        }
    }

    private static int getBadgeColor(int arrivalColor) {
        if (arrivalColor == R.color.stop_info_ontime) {
            return R.color.badge_ontime;
        } else if (arrivalColor == R.color.stop_info_delayed) {
            return R.color.badge_delayed;
        } else if (arrivalColor == R.color.stop_info_early) {
            return R.color.badge_early;
        } else {
            return R.color.badge_scheduled;
        }
    }
}
