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
package org.onebusaway.android.util;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.google.android.gms.location.LocationServices.FusedLocationApi;

/**
 * A helper class that keeps listeners updated with the best location available from
 * multiple providers
 */
public class LocationHelper implements com.google.android.gms.location.LocationListener,
        android.location.LocationListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public interface Listener {

        /**
         * Called every time there is an update to the best location available
         */
        void onLocationChanged(Location location);
    }

    static final String TAG = "LocationHelper";

    Context mContext;

    LocationManager mLocationManager;

    ArrayList<Listener> mListeners = new ArrayList<Listener>();

    /**
     * GoogleApiClient being used for Location Services
     */
    protected GoogleApiClient mGoogleApiClient;

    LocationRequest mLocationRequest;

    private static final int MILLISECONDS_PER_SECOND = 1000;

    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;

    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;

    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;

    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    private static final String PREFERENCE_SHOWED_DIALOG
            = "showed_location_security_exception_dialog";

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
            try {
                registerAllProviders();
            } catch (SecurityException e) {
                Log.e(TAG, "User may have denied location permission - " + e);
                maybeShowSecurityDialog();
            }
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

    /**
     * Returns the GoogleApiClient being used for fused provider location updates
     *
     * @return the GoogleApiClient being used for fused provider location updates
     */
    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    public synchronized void onResume() {
        try {
            registerAllProviders();
        } catch (SecurityException e) {
            Log.e(TAG, "User may have denied location permission - " + e);
            maybeShowSecurityDialog();
        }
    }

    public synchronized void onPause() {
        try {
            mLocationManager.removeUpdates(this);

            // Tear down GoogleApiClient
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "User may have denied location permission - " + e);
            maybeShowSecurityDialog();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // Offer this location to the centralized location store, it case its better than currently
        // stored location
        Application.setLastKnownLocation(location);
        // Notify listeners with the newest location from the central store (which could be the one
        // that was just generated above)
        Location lastLocation = Application.getLastKnownLocation(mContext, mGoogleApiClient);
        if (lastLocation != null) {
            // We need to copy the location, it case this object is reset in Application
            Location locationForListeners = new Location("for listeners");
            locationForListeners.set(lastLocation);
            for (Listener l : mListeners) {
                l.onLocationChanged(locationForListeners);
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

    private void registerAllProviders() throws SecurityException {
        List<String> providers = mLocationManager.getProviders(true);
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            mLocationManager.requestLocationUpdates(i.next(), 0, 0, this);
        }

        // Make sure GoogleApiClient is connected, if available
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
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
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        if (api.isGooglePlayServicesAvailable(mContext)
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        }
    }

    /**
     * Shows the security dialog once if the user has disabled location permissions manually
     */
    private void maybeShowSecurityDialog() {
        if (mContext != null && UIUtils.canManageDialog(mContext)) {
            final SharedPreferences sp = Application.getPrefs();
            if (!sp.getBoolean(PREFERENCE_SHOWED_DIALOG, false)) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setTitle(R.string.location_security_exception_title);
                builder.setCancelable(false);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        sp.edit().putBoolean(PREFERENCE_SHOWED_DIALOG, true).commit();
                    }
                });
                builder.setMessage(R.string.location_security_exception_message);
                builder.create().show();
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Location Services connected");
        // Request location updates
        try {
            FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        } catch (SecurityException e) {
            Log.e(TAG, "User may have denied location permission - " + e);
            maybeShowSecurityDialog();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
