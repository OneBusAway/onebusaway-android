/*
 * Copyright (C) 2018 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.nav.model

/**
 * A model class that holds a information for a single position recorded during navigation. Multiple
 * [Position] objects are included in a [PathLink] to define the data collected from the device as it
 * navigated the [PathLink].
 */
data class Position

/**
 * [pathLinkId] - the [PathLink] that this ooordinate belongs to
 * [coordinateId] - the unique ID for this Position.
 * [getReadyFlag] - true if the "Get Ready" alert has been announced to the user, false if it has not
 * [pullTheCordNowFlag] - true if the "Pull the Cord Now" alert has been announced to the user, false if it has not
 * [timeSinceAppStartedNanos] - the time in nanoseconds since the application started, from Location.getElapsedRealtimeNanos()
 * [timeUtc] - the time in UTC, from Location.getTime()
 * [latitude] - the latitude of the position, in degrees.
 * [longitude] - the longitude of the position, in degrees.
 * [altitude] - the altitude of the position,  in meters above the WGS 84 reference ellipsoid
 * [speed] -  the speed if it is available, in meters/second over ground
 * [bearing] - the bearing, in degrees
 * [horAccuracy] - the estimated horizontal accuracy of this location, radial, in meters
 * [numSatsUsed] - number of satellites used in fix
 * [locationProvider] - the name of the provider that generated this fix
 */
(val pathLinkId: Long,
 val coordinateId: Int,
 val getReadyFlag: Boolean,
 val pullTheCordNowFlag: Boolean,
 val timeSinceAppStartedNanos: Long,
 val timeUtc: Long,
 val latitude: Double,
 val longitude: Double,
 val altitude: Double,
 val speed: Float?,
 val bearing: Float?,
 val horAccuracy: Float,
 val numSatsUsed: Int,
 val locationProvider: String)