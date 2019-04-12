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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.onebusaway.android.app.Application;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;
import static org.onebusaway.android.util.PermissionUtils.LOCATION_PERMISSIONS;

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

    LocationCallback mLocationCallback;

    private static final int MILLISECONDS_PER_SECOND = 1000;

    private static final int UPDATE_INTERVAL_IN_SECONDS = 5;

    private long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;

    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;

    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    public LocationHelper(Context context) {
        mContext = context;
        mLocationManager = (LocationManager) Application.get().getBaseContext()
                .getSystemService(Context.LOCATION_SERVICE);
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
    }

    /**
     *
     * @param context
     * @param interval Faster interval in seconds.
     */
    public LocationHelper(Context context, int interval) {
        mContext = context;
        UPDATE_INTERVAL = interval*MILLISECONDS_PER_SECOND;
        mLocationManager = (LocationManager) Application.get().getBaseContext()
                .getSystemService(Context.LOCATION_SERVICE);
        setupGooglePlayServices();
    }

    /**
     * Registers the provided listener for location updates, but first checks to see if Location
     * permissions are granted.  If permissions haven't been granted, returns false and does not
     * register any listeners.  After the caller has received permission from the user it can
     * call this method again.
     * @param listener listener for updates
     * @return true if permissions have been granted and the listener was registered, false if
     * permissions have not been granted and no listener was registered
     */
    public synchronized boolean registerListener(Listener listener) {
        if (!PermissionUtils.hasGrantedPermissions(mContext, LOCATION_PERMISSIONS)) {
            return false;
        }
        // User has granted permissions - continue to register listener for location updates
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }

        // If this is the first listener, make sure we're monitoring the sensors to provide updates
        if (mListeners.size() == 1) {
            // Listen for location
            registerAllProviders();
        }
        return true;
    }

    public synchronized void unregisterListener(Listener listener) {
        mListeners.remove(listener);

        if (mListeners.size() == 0) {
            try {
                mLocationManager.removeUpdates(this);
            } catch (SecurityException e) {
                // We're just unregistering listeners here, so just log exception if user revoked
                // permissions after the listener was registered
                Log.w(TAG, "User may have denied location permission - " + e);
            }
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
            // If we resume after the user has denied location permissions, log the warning and continue
            Log.w(TAG, "User may have denied location permission - " + e);
        }
    }

    public synchronized void onPause() {
        try {
            mLocationManager.removeUpdates(this);

            // Tear down GoogleApiClient
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()&& mLocationCallback != null) {
                FusedLocationProviderClient client = getFusedLocationProviderClient(mContext);
                client.removeLocationUpdates(mLocationCallback);
                mGoogleApiClient.disconnect();
            }
        } catch (SecurityException e) {
            // We're just unregistering listeners here, so just log exception if user revoked
            // permissions after the listener was registered
            Log.w(TAG, "User may have denied location permission - " + e);
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
        // Register the network and GPS provider (and anything else available)
        List<String> providers = mLocationManager.getProviders(true);
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            mLocationManager.requestLocationUpdates(i.next(), 0, 0, this);
        }

        setupGooglePlayServices();
    }

    /**
     * Request connection to Google Play Services for the fused location provider.
     * onConnected() will be called after connection.
     */
    private void setupGooglePlayServices() {
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

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "Location Services connected");
        // Request location updates from the fused location provider
        FusedLocationProviderClient client = getFusedLocationProviderClient(mContext);

        if (mLocationCallback == null) {
            mLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    onLocationChanged(locationResult.getLastLocation());
                }
            };
        }
        try {
            client.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        } catch (SecurityException e) {
            // We only register the fused provider if permission was granted, so if it was revoked
            // in between log the warning and continue
            Log.w(TAG, "User may have denied location permission - " + e);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }
}
