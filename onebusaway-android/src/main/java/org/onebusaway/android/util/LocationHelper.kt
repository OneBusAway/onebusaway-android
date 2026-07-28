/* Copyright (C) 2014 Sean J. Barbeau, University of South Florida */
package org.onebusaway.android.util

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.onebusaway.android.app.di.LocationEntryPoint

/** Keeps listeners updated with the best fix available from framework and fused providers. */
class LocationHelper(context: Context, interval: Int) : LocationListener {
    fun interface Listener {
        fun onLocationChanged(location: Location)
    }

    private val context = context.applicationContext
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listeners = ArrayList<Listener>()
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        interval.toLong() * MILLISECONDS_PER_SECOND
    ).setMinUpdateIntervalMillis(FASTEST_INTERVAL).build()
    private var locationCallback: LocationCallback? = null

    @Synchronized
    fun registerListener(listener: Listener): Boolean {
        if (!PermissionUtils.hasGrantedAtLeastOnePermission(context, PermissionUtils.LOCATION_PERMISSIONS)) {
            return false
        }
        if (listener !in listeners) listeners += listener
        if (listeners.size == 1) registerAllProviders()
        return true
    }

    @Synchronized
    fun unregisterListener(listener: Listener) {
        listeners -= listener
        if (listeners.isEmpty()) {
            try {
                locationManager.removeUpdates(this)
                removeFusedLocationUpdates()
            } catch (error: SecurityException) {
                Log.w(TAG, "User may have denied location permission - $error")
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        LocationEntryPoint.getSink(context).update(location)
        LocationEntryPoint.get(context).lastKnownLocation()?.let { latest ->
            val copy = Location("for listeners").apply { set(latest) }
            listeners.toList().forEach { it.onLocationChanged(copy) }
        }
    }

    @Deprecated("Required by the API 23 LocationListener contract")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun registerAllProviders() {
        try {
            locationManager.getProviders(true).forEach { provider ->
                locationManager.requestLocationUpdates(provider, 0, 0f, this)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "User may have denied location permission - $error")
        }
        requestFusedLocationUpdates()
    }

    private fun requestFusedLocationUpdates() {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            return
        }
        val callback = locationCallback ?: object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::onLocationChanged)
            }
        }.also { locationCallback = it }
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .requestLocationUpdates(locationRequest, callback, null)
        } catch (error: SecurityException) {
            Log.w(TAG, "User may have denied location permission - $error")
        }
    }

    private fun removeFusedLocationUpdates() {
        locationCallback?.let(LocationServices.getFusedLocationProviderClient(context)::removeLocationUpdates)
    }

    companion object {
        const val TAG = "LocationHelper"
        private const val MILLISECONDS_PER_SECOND = 1_000L
        private const val FASTEST_INTERVAL = MILLISECONDS_PER_SECOND
    }
}
