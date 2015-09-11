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
import org.onebusaway.android.util.MyTextUtils;
import org.onebusaway.android.util.UIHelp;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.format.DateUtils;
import android.view.View;
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
                    ArrivalInfo.convertObaArrivalInfo(getContext(),
                            arrivals, routesFilter, currentTime);
            setData(list);
        } else {
            setData(null);
        }
    }

    @Override
    protected void initView(View view, ArrivalInfo stopInfo) {
        TextView route = (TextView) view.findViewById(R.id.route);
        TextView destination = (TextView) view.findViewById(R.id.destination);
        TextView time = (TextView) view.findViewById(R.id.time);
        TextView status = (TextView) view.findViewById(R.id.status);
        TextView etaView = (TextView) view.findViewById(R.id.eta);

        final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();
        final Context context = getContext();

        route.setText(arrivalInfo.getShortName());
        destination.setText(MyTextUtils.toTitleCase(arrivalInfo.getHeadsign()));
        status.setText(stopInfo.getStatusText());

        long eta = stopInfo.getEta();
        if (eta == 0) {
            etaView.setText(R.string.stop_info_eta_now);
        } else {
            etaView.setText(String.valueOf(eta));
        }

        Integer colorCode = stopInfo.getColor();
        if (colorCode != null) {
            int color = context.getResources().getColor(colorCode);
            etaView.setTextColor(color);

            status.setBackgroundResource(R.drawable.round_corners_style_b_status);
            GradientDrawable d = (GradientDrawable) status.getBackground();
            if (colorCode != null) {
                // Set real-time color
                d.setColor(context.getResources().getColor(colorCode));
            } else {
                // Set scheduled color
                d.setColor(
                        context.getResources().getColor(R.color.stop_info_estimated_time));
            }
        }

        // Set padding on status view
        int pSides = UIHelp.dpToPixels(context, 5);
        int pTopBottom = UIHelp.dpToPixels(context, 2);
        status.setPadding(pSides, pTopBottom, pSides, pTopBottom);

        time.setText(DateUtils.formatDateTime(context,
                stopInfo.getDisplayTime(),
                DateUtils.FORMAT_SHOW_TIME |
                        DateUtils.FORMAT_NO_NOON |
                        DateUtils.FORMAT_NO_MIDNIGHT
        ));

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
            DrawableCompat.setTint(d.mutate(),
                    view.getResources().getColor(R.color.button_material_dark));
            reminder.setCompoundDrawables(d, null, null, null);
            reminder.setVisibility(View.VISIBLE);
        } else {
            // Explicitly set this to invisible because we might be reusing
            // this view.
            View reminder = view.findViewById(R.id.reminder);
            reminder.setVisibility(View.GONE);
        }
    }
}
