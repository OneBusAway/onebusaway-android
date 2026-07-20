/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
@file:JvmName("Locations")

package org.onebusaway.android.util

import android.location.Location
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.onebusaway.android.time.ElapsedTime

/**
 * Converts a latitude/longitude to a provider-less [Location].
 */
fun locationOf(lat: Double, lon: Double): Location = Location("").apply {
    latitude = lat
    longitude = lon
}

/** An Android [Location] as a flavor-neutral [GeoPoint]. */
fun Location.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

/** A flavor-neutral [GeoPoint] as a provider-less Android [Location] (inverse of [toGeoPoint]). */
fun GeoPoint.toLocation(): Location = locationOf(latitude, longitude)

/**
 * Returns the human-readable details of a Location (provider, lat/long, accuracy, age) for
 * debug/email text, or the empty string when [loc] is null.
 *
 * Pure: [now] is the caller's monotonic reading (project rule — helpers don't read the clock). The
 * location's own [Location.getElapsedRealtimeNanos] is a monotonic reading in the same domain, so the
 * age is a same-domain subtraction with no clock-skew hazard.
 */
fun describeLocation(loc: Location?, now: ElapsedTime): String {
    if (loc == null) {
        return ""
    }
    val age = now - ElapsedTime(TimeUnit.NANOSECONDS.toMillis(loc.elapsedRealtimeNanos))
    val ageSeconds = age.inWholeMilliseconds / 1E3

    return buildString {
        append(loc.provider)
        append(' ')
        append(loc.latitude)
        append(',')
        append(loc.longitude)
        if (loc.hasAccuracy()) {
            append(' ')
            append(loc.accuracy)
        }
        append(", ")
        append(String.format(Locale.US, "%.0f", ageSeconds))
        append(" second(s) ago")
    }
}
