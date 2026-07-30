/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.util

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.text.DecimalFormat
import java.text.NumberFormat
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.api.bridge.RegionsClient
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.region.Region

/** Utility methods related to handling multiple regions in OneBusAway. */
object RegionUtils {
    private const val TAG = "RegionUtils"

    const val TAMPA_REGION_ID = 0
    const val PUGET_SOUND_REGION_ID = 1
    const val METERS_TO_MILES = 0.000621371
    const val METERS_TO_FEET = 3.28084
    private const val DISTANCE_LIMITER = 100

    fun getClosestRegion(
        context: Context,
        regions: List<Region>,
        loc: Location?,
        enforceThreshold: Boolean
    ): Region? {
        loc ?: return null
        var minDist = Float.MAX_VALUE
        var closestRegion: Region? = null
        val fmt: NumberFormat = NumberFormat.getInstance()
        if (fmt is DecimalFormat) fmt.maximumFractionDigits = 1
        if (BuildConfig.DEBUG) Log.d(TAG, "Finding region closest to ${loc.latitude},${loc.longitude}")

        for (region in regions) {
            if (!isRegionUsable(context, region)) {
                Log.d(TAG, "Excluding '${region.name}' from 'closest region' consideration")
                continue
            }
            val distance = getDistanceAway(region, loc.latitude, loc.longitude)
            if (distance == null) {
                Log.e(TAG, "Couldn't measure distance to region '${region.name}'")
                continue
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Region '${region.name}' is ${fmt.format(distance * METERS_TO_MILES)} miles away")
            }
            if (distance < minDist) {
                closestRegion = region
                minDist = distance
            }
        }
        return if (!enforceThreshold || minDist * METERS_TO_MILES < DISTANCE_LIMITER) closestRegion else null
    }

    fun getObaRegionName(context: Context): String? {
        val region = RegionEntryPoint.get(context).currentRegion()
        if (region != null) return region.name
        val customApiUrl =
            PreferencesEntryPoint.get(context)
                .getString(context.getString(R.string.preference_key_oba_api_url), null)
        return customApiUrl?.let { CustomApiUrlLabel.forUrl(context, it) }
    }

    fun getDistanceAway(region: Region, lat: Double, lon: Double): Float? {
        if (region.bounds.isEmpty()) return null
        val results = FloatArray(1)
        var minDistance = Float.MAX_VALUE
        for (bound in region.bounds) {
            Location.distanceBetween(lat, lon, bound.lat, bound.lon, results)
            if (results[0] < minDistance) minDistance = results[0]
        }
        return minDistance
    }

    fun getDistanceAway(region: Region, loc: Location): Float? = getDistanceAway(region, loc.latitude, loc.longitude)

    fun getRegionSpan(region: Region, results: DoubleArray) {
        require(results.size >= 4) { "Results array is < 4" }
        var latMin = 90.0
        var latMax = -90.0
        var lonMin = 180.0
        var lonMax = -180.0
        for (bound in region.bounds) {
            val latSpanHalf = bound.latSpan / 2.0
            latMin = minOf(latMin, bound.lat - latSpanHalf)
            latMax = maxOf(latMax, bound.lat + latSpanHalf)
            val lonSpanHalf = bound.lonSpan / 2.0
            lonMin = minOf(lonMin, bound.lon - lonSpanHalf)
            lonMax = maxOf(lonMax, bound.lon + lonSpanHalf)
        }
        results[0] = latMax - latMin
        results[1] = lonMax - lonMin
        results[2] = latMin + (latMax - latMin) / 2.0
        results[3] = lonMin + (lonMax - lonMin) / 2.0
    }

    fun isLocationWithinRegion(location: Location, regionSpan: DoubleArray): Boolean {
        require(regionSpan.size >= 4) { "regionSpan is null or has length < 4" }
        require(location.longitude in -180.0..180.0 && location.latitude in -90.0..90.0) {
            "Location must be a valid location"
        }
        val minLat = regionSpan[2] - regionSpan[0] / 2
        val minLon = regionSpan[3] - regionSpan[1] / 2
        val maxLat = regionSpan[2] + regionSpan[0] / 2
        val maxLon = regionSpan[3] + regionSpan[1] / 2
        return location.latitude in minLat..maxLat && location.longitude in minLon..maxLon
    }

    fun isLocationWithinRegion(location: Location, region: Region): Boolean = isLocationWithinRegion(location, DoubleArray(4).also { getRegionSpan(region, it) })

    fun isRegionUsable(context: Context, region: Region): Boolean {
        if (!region.active) {
            Log.d(TAG, "Region '${region.name}' is not active.")
            return false
        }
        if (!region.supportsObaDiscoveryApis) {
            Log.d(TAG, "Region '${region.name}' does not support OBA Discovery APIs.")
            return false
        }
        if (!region.supportsObaRealtimeApis) {
            Log.d(TAG, "Region '${region.name}' does not support OBA Realtime APIs.")
            return false
        }
        if (region.experimental && !PreferenceUtils.getBoolean(context.getString(R.string.preference_key_experimental_regions), false)) {
            Log.d(TAG, "Region '${region.name}' is experimental and user hasn't opted in.")
            return false
        }
        return true
    }

    fun formatOtpBaseUrl(baseUrl: String): String = baseUrl.removeSuffix("/")

    @Synchronized
    fun getRegionsFromServer(context: Context): List<Region> = RegionsClient.fetchRegionsFromServer(context)

    fun getRegionsFromResources(context: Context): List<Region> = RegionsClient.parseBundledRegions(context)

    @VisibleForTesting
    fun open311ServersFrom(jurisdictionId: String?, apiKey: String?, baseUrl: String?): Array<Region.Open311Server> = if (baseUrl.isNullOrBlank()) emptyArray() else arrayOf(Region.Open311Server(jurisdictionId, apiKey, baseUrl))

    fun getRegionFromBuildFlavor(): Region {
        val name = requireNotNull(BuildConfig.FIXED_REGION_NAME) {
            "FIXED_REGION_NAME must be set in the build flavor when USE_FIXED_REGION is true"
        }
        return Region(
            id = Int.MAX_VALUE.toLong(),
            name = name,
            active = true,
            obaBaseUrl = BuildConfig.FIXED_REGION_OBA_BASE_URL,
            siriBaseUrl = BuildConfig.FIXED_REGION_SIRI_BASE_URL,
            bounds = arrayOf(Region.Bounds(BuildConfig.FIXED_REGION_BOUNDS_LAT, BuildConfig.FIXED_REGION_BOUNDS_LON, BuildConfig.FIXED_REGION_BOUNDS_LAT_SPAN, BuildConfig.FIXED_REGION_BOUNDS_LON_SPAN)),
            open311Servers = open311ServersFrom(BuildConfig.FIXED_REGION_OPEN311_JURISDICTION_ID, BuildConfig.FIXED_REGION_OPEN311_API_KEY, BuildConfig.FIXED_REGION_OPEN311_BASE_URL),
            language = BuildConfig.FIXED_REGION_LANG,
            contactEmail = BuildConfig.FIXED_REGION_CONTACT_EMAIL,
            supportsObaDiscoveryApis = BuildConfig.FIXED_REGION_SUPPORTS_OBA_DISCOVERY_APIS,
            supportsObaRealtimeApis = BuildConfig.FIXED_REGION_SUPPORTS_OBA_REALTIME_APIS,
            supportsSiriRealtimeApis = BuildConfig.FIXED_REGION_SUPPORTS_SIRI_REALTIME_APIS,
            twitterUrl = BuildConfig.FIXED_REGION_TWITTER_URL,
            experimental = false,
            stopInfoUrl = BuildConfig.FIXED_REGION_STOP_INFO_URL,
            otpBaseUrl = BuildConfig.FIXED_REGION_OTP_BASE_URL,
            otpContactEmail = BuildConfig.FIXED_REGION_OTP_CONTACT_EMAIL,
            supportsOtpBikeshare = BuildConfig.FIXED_REGION_SUPPORTS_OTP_BIKESHARE,
            paymentAndroidAppId = BuildConfig.FIXED_REGION_PAYMENT_ANDROID_APP_ID,
            paymentWarningTitle = BuildConfig.FIXED_REGION_PAYMENT_WARNING_TITLE,
            paymentWarningBody = BuildConfig.FIXED_REGION_PAYMENT_WARNING_BODY,
            sidecarBaseUrl = BuildConfig.FIXED_REGION_SIDECAR_BASE_URL,
            plausibleAnalyticsServerUrl = BuildConfig.FIXED_REGION_PLAUSIBLE_ANALYTICS_SERVER_URL
        )
    }
}
