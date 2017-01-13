/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.UIUtils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Original style of arrivals for OBA Android
 */
public class ArrivalsListAdapterStyleA extends ArrivalsListAdapterBase<ArrivalInfo> {

    public ArrivalsListAdapterStyleA(Context context) {
        super(context, R.layout.arrivals_list_item);
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
                            arrivals, routesFilter, currentTime, false);
            setData(list);
        } else {
            setData(null);
        }
    }

    @Override
    protected void initView(View view, ArrivalInfo stopInfo) {
        final Context context = getContext();
        final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();

        TextView route = (TextView) view.findViewById(R.id.route);
        TextView destination = (TextView) view.findViewById(R.id.destination);
        TextView time = (TextView) view.findViewById(R.id.time);
        TextView status = (TextView) view.findViewById(R.id.status);
        TextView etaView = (TextView) view.findViewById(R.id.eta);
        TextView minView = (TextView) view.findViewById(R.id.eta_min);
        ViewGroup realtimeView = (ViewGroup) view.findViewById(R.id.eta_realtime_indicator);
        ImageView moreView = (ImageView) view.findViewById(R.id.more_horizontal);
        moreView.setColorFilter(
                context.getResources().getColor(R.color.switch_thumb_normal_material_dark));
        ImageView starView = (ImageView) view.findViewById(R.id.route_favorite);
        starView.setColorFilter(context.getResources().getColor(R.color.navdrawer_icon_tint));
        starView.setImageResource(stopInfo.isRouteAndHeadsignFavorite() ?
                R.drawable.focus_star_on :
                R.drawable.focus_star_off);

        String shortName = arrivalInfo.getShortName();
        route.setText(shortName);
        UIUtils.maybeShrinkRouteName(getContext(), route, shortName);

        destination.setText(UIUtils.formatDisplayText(arrivalInfo.getHeadsign()));
        status.setText(stopInfo.getStatusText());

        long eta = stopInfo.getEta();
        if (eta == 0) {
            etaView.setText(R.string.stop_info_eta_now);
            minView.setVisibility(View.GONE);
        } else {
            etaView.setText(String.valueOf(eta));
            minView.setVisibility(View.VISIBLE);
        }

        status.setBackgroundResource(R.drawable.round_corners_style_b_status);
        GradientDrawable d = (GradientDrawable) status.getBackground();

        Integer colorCode = stopInfo.getColor();
        int color = context.getResources().getColor(colorCode);
        if (stopInfo.getPredicted()) {
            // Show real-time indicator
            UIUtils.setRealtimeIndicatorColorByResourceCode(realtimeView, colorCode,
                    android.R.color.transparent);
            realtimeView.setVisibility(View.VISIBLE);
        } else {
            realtimeView.setVisibility(View.INVISIBLE);
        }

        etaView.setTextColor(color);
        minView.setTextColor(color);
        d.setColor(color);

        // Set padding on status view
        int pSides = UIUtils.dpToPixels(context, 5);
        int pTopBottom = UIUtils.dpToPixels(context, 2);
        status.setPadding(pSides, pTopBottom, pSides, pTopBottom);

        time.setText(stopInfo.getTimeText());

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
            Drawable d2 = reminder.getCompoundDrawables()[0];
            d2 = DrawableCompat.wrap(d2);
            DrawableCompat.setTint(d2.mutate(),
                    view.getResources().getColor(R.color.button_material_dark));
            reminder.setCompoundDrawables(d2, null, null, null);
            reminder.setVisibility(View.VISIBLE);
        } else {
            // Explicitly set this to invisible because we might be reusing
            // this view.
            View reminder = view.findViewById(R.id.reminder);
            reminder.setVisibility(View.GONE);
        }
    }
}
