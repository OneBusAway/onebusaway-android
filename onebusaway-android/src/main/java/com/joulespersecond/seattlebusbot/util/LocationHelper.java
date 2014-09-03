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
package com.joulespersecond.seattlebusbot.util;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;

import com.joulespersecond.seattlebusbot.Application;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implements a helper to keep listeners updated with the best location available from
 * multiple providers
 */
public class LocationHelper implements com.google.android.gms.location.LocationListener,
        android.location.LocationListener, GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {

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

    static GeomagneticField mGeomagneticField;

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    /**
     * Google Location Services in Play Services
     */
    protected LocationClient mLocationClient;

    LocationRequest mLocationRequest;

    private static final int MILLISECONDS_PER_SECOND = 1000;

    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;

    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;

    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;

    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    public LocationHelper(Context context) {
        mContext = context;
        mLocationManager = (LocationManager) Application.get().getBaseContext()
                .getSystemService(Context.LOCATION_SERVICE);
        setupGooglePlayServices();
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

        // Tear down LocationClient
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
            mLocationClient.disconnect();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        final float ACC_THRESHOLD = 50f;
        // If the new location is the first location, or has an accuracy better than 50m and
        // is newer than the last location, save it
        if (mLastLocation == null || (mLastLocation.getAccuracy() < ACC_THRESHOLD
                && location.getTime() > mLastLocation.getTime())) {
            mLastLocation = location;

            mGeomagneticField = new GeomagneticField(
                    (float) location.getLatitude(),
                    (float) location.getLongitude(),
                    (float) location.getAltitude(),
                    System.currentTimeMillis());

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

    /**
     * Returns the declination of the horizontal component of the magnetic field from true north,
     * in
     * degrees (i.e. positive means the magnetic field is rotated east that much from true north).
     *
     * @return declination of the horizontal component of the magnetic field from true north, in
     * degrees (i.e. positive means the magnetic field is rotated east that much from true north),
     * or null if its not available
     */
    public static Float getMagneticDeclination() {
        if (mGeomagneticField != null) {
            return mGeomagneticField.getDeclination();
        } else {
            return null;
        }
    }

    private void registerAllProviders() {
        List<String> providers = mLocationManager.getProviders(true);
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            mLocationManager.requestLocationUpdates(i.next(), 0, 0, this);
        }

        // Make sure LocationClient is connected, if available
        if (mLocationClient != null && !mLocationClient.isConnected()) {
            mLocationClient.connect();
        }
    }

    private void setupGooglePlayServices() {
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext)
                == ConnectionResult.SUCCESS) {
            mLocationClient = new LocationClient(mContext, this, this);
            mLocationClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Location Services connected");
        // Request location updates
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
