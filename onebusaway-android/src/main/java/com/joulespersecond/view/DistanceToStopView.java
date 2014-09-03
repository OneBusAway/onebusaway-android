/*
 * Copyright (C) 2014 Sean J. Barbeau (sjbarbeau@gmail.com), University of South Florida
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
package com.joulespersecond.view;


import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.util.LocationHelper;

import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.widget.TextView;

import java.text.NumberFormat;

/**
 * A TextView that updates itself with the distance to the given bus stop from the given location
 */
public class DistanceToStopView extends TextView implements LocationHelper.Listener {

    Context mContext;

    ObaStop mObaStop;

    Location mStopLocation = new Location("stopLocation");

    private static final float MILES_TO_METERS = 0.000621371f;

    private static final float METERS_IN_QUARTER_MILE = 402.336f;

    private static final float METERS_TO_FEET = 3.28084f;

    NumberFormat mNumberFormat;

    public DistanceToStopView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mNumberFormat = NumberFormat.getInstance();
        mNumberFormat.setMaximumFractionDigits(1);
    }

    public void setObaStop(ObaStop stop) {
        mObaStop = stop;
        if (stop != null) {
            mStopLocation.setLatitude(mObaStop.getLocation().getLatitude());
            mStopLocation.setLongitude(mObaStop.getLocation().getLongitude());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mObaStop != null) {
            float distance = location.distanceTo(mStopLocation);

            // If the distance is greater than a quarter mile, show in miles, else show in feet
            if (distance > METERS_IN_QUARTER_MILE) {
                float miles = distance * MILES_TO_METERS;
                setText(mNumberFormat.format(miles) + " " + mContext
                        .getString(R.string.miles_abbreviation));
            } else {
                int feet = (int) (distance * METERS_TO_FEET);
                setText(feet + " " + mContext.getString(R.string.feet_abbreviation));
            }
        } else {
            // Clear current text
            setText("");
        }
    }
}
