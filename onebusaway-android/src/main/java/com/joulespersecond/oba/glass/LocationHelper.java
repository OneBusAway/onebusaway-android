/*
 * Copyright (C) 2014 Sean J. Barbeau, University of South Florida
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
package com.joulespersecond.oba.glass;

import com.joulespersecond.seattlebusbot.Application;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implements a helper to keep listeners updated with the best location available from
 * multiple providers
 */
public class LocationHelper implements LocationListener {

    public interface Listener {

        /**
         * Called every time there is an update to the best location available
         */
        void onLocationChanged(Location location);
    }

    static final String TAG = "LocationHelper";

    Context mContext;

    LocationManager mLocationManager;

    Location mLastLocation;

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    public LocationHelper(Context context) {
        mContext = context;
        mLocationManager = (LocationManager) Application.get().getBaseContext()
                .getSystemService(Context.LOCATION_SERVICE);
    }

    public synchronized void registerListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }

        // If this is the first listener, make sure we're monitoring the sensors to provide updates
        if (mListeners.size() == 1) {
            // Listen for location
            registerAllProviders();
        }
    }

    public synchronized void unregisterListener(Listener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }

        if (mListeners.size() == 0) {
            mLocationManager.removeUpdates(this);
        }
    }

    public synchronized void onResume() {
        registerAllProviders();
    }

    public synchronized void onPause() {
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        final float ACC_THRESHOLD = 50f;
        // If the new location is the first location, or has an accuracy better than 50m and
        // is newer than the last location, save it
        if (mLastLocation == null || (mLastLocation.getAccuracy() < ACC_THRESHOLD
                && location.getTime() > mLastLocation.getTime())) {
            mLastLocation = location;

            for (Listener l : mListeners) {
                l.onLocationChanged(mLastLocation);
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private void registerAllProviders() {
        List<String> providers = mLocationManager.getProviders(true);
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            mLocationManager.requestLocationUpdates(i.next(), 0, 0, this);
        }
    }
}
