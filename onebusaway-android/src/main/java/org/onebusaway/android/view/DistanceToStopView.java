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
package org.onebusaway.android.view;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.LocationHelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.AttributeSet;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * A TextView that updates itself with the distance to the given bus stop from the given location
 */
public class DistanceToStopView extends TextView implements LocationHelper.Listener {

    public interface Listener {

        /**
         * Called when the DistanceToStopView is showing information to the user
         */
        void onInitializationComplete();
    }

    private static String TAG = "DistanceToStopView";

    Context mContext;

    Location mStopLocation;

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    private static final float MILES_TO_METERS = 0.000621371f;

    private static final float MILES_THRESHOLD = 0.25f;

    private static final float KILOMETERS_THRESHOLD = 0.25f;

    private static final float MILES_TO_FEET = 5280;

    NumberFormat mNumberFormat;

    Locale mLocale;

    private static String IMPERIAL;

    private static String METRIC;

    private static String AUTOMATIC;

    SharedPreferences mSettings;

    private String preferredUnits;

    boolean mInitialized = false;

    public DistanceToStopView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mNumberFormat = NumberFormat.getInstance();
        mNumberFormat.setMaximumFractionDigits(1);

        mLocale = Locale.getDefault();
        IMPERIAL = mContext.getString(R.string.preferences_preferred_units_option_imperial);
        METRIC = mContext.getString(R.string.preferences_preferred_units_option_metric);
        AUTOMATIC = mContext.getString(R.string.preferences_preferred_units_option_automatic);

        mSettings = Application.getPrefs();
        preferredUnits = mSettings
                .getString(mContext.getString(R.string.preference_key_preferred_units),
                        AUTOMATIC);
    }

    public synchronized void registerListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public synchronized void unregisterListener(Listener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    /**
     * Sets the stop location that this view measures the distance to
     *
     * @param location stop location
     */
    public void setStopLocation(Location location) {
        mStopLocation = location;
    }

    /**
     * Should be called when the view is being shown again and the preferences
     * may have changed (typically, in onResume() of the parent context)
     */
    public void refreshUnitsPreference() {
        preferredUnits = mSettings
                .getString(mContext.getString(R.string.preference_key_preferred_units),
                        AUTOMATIC);
    }

    /**
     * Returns true if the view is initialized and ready to draw to the screen, false if it is not
     *
     * @return true if the view is initialized and ready to draw to the screen, false if it is not
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mStopLocation != null) {

            if (!mInitialized) {
                mInitialized = true;
                // Notify listeners that we have both stop and real-time location
                for (Listener l : mListeners) {
                    l.onInitializationComplete();
                }
            }

            Float distance = location.distanceTo(mStopLocation);

            if (distance != null) {
                double miles = distance * MILES_TO_METERS;
                distance /= 1000; // Convert meters to kilometers

                if (preferredUnits.equalsIgnoreCase(AUTOMATIC)) {
                    // Log.d(TAG, "Setting units automatically");
                    // If the country is set to USA, assume imperial, otherwise metric
                    // TODO - Method of guessing metric/imperial can definitely be improved
                    if (mLocale.getISO3Country().equalsIgnoreCase(Locale.US.getISO3Country())) {
                        // Assume imperial
                        setDistanceTextView(miles, IMPERIAL);
                    } else {
                        // Assume metric
                        setDistanceTextView(distance, METRIC);
                    }
                } else if (preferredUnits.equalsIgnoreCase(IMPERIAL)) {
                    setDistanceTextView(miles, IMPERIAL);
                } else if (preferredUnits.equalsIgnoreCase(METRIC)) {
                    setDistanceTextView(distance, METRIC);
                }
                return;
            }
        }

        // If we've gotten this far, distance isn't valid, so clear current text
        setText("");
    }

    /**
     * Sets the text view that contains distance with units based on input parameters
     *
     * @param distance the distance to be used, in miles (for imperial) or kilometers (for metric)
     * @param units    the units to be used from strings.xml, either preferences_preferred_units_option_metric
     *                 or preferences_preferred_units_option_imperial
     */
    private void setDistanceTextView(double distance, String units) {
        // Set TextView text
        // TODO - Set ContentDescription to be read by screen readers
        if (units.equalsIgnoreCase(
                mContext.getString(R.string.preferences_preferred_units_option_imperial))) {
            // IMPERIAL
            // If the distance is greater than a quarter mile, show in miles, else show in feet
            if (distance > MILES_THRESHOLD) {
                // MILES
                mNumberFormat.setMaximumFractionDigits(1);
                setText(mNumberFormat.format(distance) + " " + mContext
                        .getString(R.string.miles_abbreviation));
            } else {
                // FEET
                mNumberFormat.setMaximumFractionDigits(0);
                double feet = distance * MILES_TO_FEET;
                setText(mNumberFormat.format(feet) + " " + mContext
                        .getString(R.string.feet_abbreviation));
            }
        } else if (units.equalsIgnoreCase(mContext.getString(
                R.string.preferences_preferred_units_option_metric))) {
            // METRIC
            if (distance > KILOMETERS_THRESHOLD) {
                // KILOMETERS
                mNumberFormat.setMaximumFractionDigits(1);
                setText(mNumberFormat.format(distance) + " " + mContext
                        .getString(R.string.kilometers_abbreviation));
            } else {
                // METERS
                mNumberFormat.setMaximumFractionDigits(0);
                double meters = distance * 1000;
                setText(mNumberFormat.format(meters) + " " + mContext
                        .getString(R.string.meters_abbreviation));
            }
        }
    }
}
