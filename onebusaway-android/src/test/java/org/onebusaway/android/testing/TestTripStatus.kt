/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.testing

import android.location.Location
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status

/**
 * A minimal [ObaTripStatus] for JVM unit tests. Location-typed accessors return null by default, so
 * the android.location.Location stubs (which throw "Stub!") are never invoked — no Robolectric
 * needed. Pass [hasLocation] = true when a test needs `lastKnownLocation` to be non-null (e.g.
 * to exercise `isLocationRealtime`); the returned sentinel is only ever null-checked, never called.
 */
fun testTripStatus(
    distanceAlongTrip: Double? = null,
    totalDistanceAlongTrip: Double? = null,
    lastUpdateTime: Long = 0L,
    vehicleId: String? = null,
    activeTripId: String? = null,
    predicted: Boolean = false,
    hasLocation: Boolean = false,
): ObaTripStatus = TestTripStatus(
    distanceAlongTrip = distanceAlongTrip,
    totalDistanceAlongTrip = totalDistanceAlongTrip,
    lastUpdateTime = lastUpdateTime,
    vehicleId = vehicleId,
    activeTripId = activeTripId,
    predicted = predicted,
    hasLocation = hasLocation,
)

private class TestTripStatus(
    distanceAlongTrip: Double?,
    totalDistanceAlongTrip: Double?,
    lastUpdateTime: Long,
    vehicleId: String?,
    activeTripId: String?,
    predicted: Boolean,
    private val hasLocation: Boolean,
) : ObaTripStatus {
    override val serviceDate: Long = 0L
    override val isPredicted: Boolean = predicted
    override val scheduleDeviation: Long = 0L
    override val vehicleId: String? = vehicleId
    override val closestStop: String? = null
    override val closestStopTimeOffset: Long = 0L
    override val position: Location? = null
    override val activeTripId: String? = activeTripId
    override val distanceAlongTrip: Double? = distanceAlongTrip
    override val scheduledDistanceAlongTrip: Double? = null
    override val totalDistanceAlongTrip: Double? = totalDistanceAlongTrip
    override val orientation: Double? = null
    override val nextStop: String? = null
    override val nextStopTimeOffset: Long? = null
    override val phase: String? = null
    override val status: Status? = null
    override val lastUpdateTime: Long = lastUpdateTime
    override val lastKnownLocation: Location?
        get() = if (hasLocation) FAKE_LOCATION else null
    override val lastLocationUpdateTime: Long = 0L
    override val lastKnownDistanceAlongTrip: Double? = null
    override val lastKnownOrientation: Double? = null
    override val blockTripSequence: Int = 0
    override val occupancyStatus: Occupancy? = null

    companion object {
        /**
         * A non-null [Location] sentinel. The android.jar stubs on the JVM test classpath let the
         * class resolve but make its constructor and methods throw "Stub!", so we allocate without
         * the constructor and only ever null-check the reference — never call methods on it.
         */
        private val FAKE_LOCATION: Location = try {
            Location("test")
        } catch (e: RuntimeException) {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = theUnsafe.get(null)
            val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
            allocate.invoke(unsafe, Location::class.java) as Location
        }
    }
}
