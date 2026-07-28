/* Copyright (C) 2014-2024 University of South Florida */
package org.onebusaway.android.map.maplibre

import android.location.Location
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.onebusaway.android.region.Region

/** Utilities for MapLibre map values. */
object MapHelpMapLibre {
    const val TAG = "MapHelpMapLibre"

    @JvmStatic fun makeLatLng(lat: Double, lon: Double) = LatLng(lat, lon)

    @JvmStatic fun makeLatLng(location: Location) = makeLatLng(location.latitude, location.longitude)

    @JvmStatic
    fun makeLocation(latLng: LatLng) = Location("FromLatLng").apply {
        latitude = latLng.latitude
        longitude = latLng.longitude
    }

    @JvmStatic
    fun getRegionBounds(region: Region): LatLngBounds {
        var latMin = 90.0
        var latMax = -90.0
        var lonMin = 180.0
        var lonMax = -180.0
        region.bounds.forEach { bound ->
            val latHalf = bound.latSpan / 2.0
            latMin = minOf(latMin, bound.lat - latHalf)
            latMax = maxOf(latMax, bound.lat + latHalf)
            val lonHalf = bound.lonSpan / 2.0
            lonMin = minOf(lonMin, bound.lon - lonHalf)
            lonMax = maxOf(lonMax, bound.lon + lonHalf)
        }
        return LatLngBounds.Builder()
            .include(makeLatLng(latMin, lonMin))
            .include(makeLatLng(latMax, lonMax))
            .build()
    }

    @JvmStatic fun isMapsInstalled() = true
}
