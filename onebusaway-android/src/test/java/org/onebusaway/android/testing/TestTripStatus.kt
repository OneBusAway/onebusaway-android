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
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.elements.Occupancy
import org.onebusaway.android.io.elements.Status

/**
 * A minimal [ObaTripStatus] for JVM unit tests. Location-typed accessors return null by default, so
 * the android.location.Location stubs (which throw "Stub!") are never invoked — no Robolectric
 * needed. Pass [hasLocation] = true when a test needs `getLastKnownLocation()` to be non-null (e.g.
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
    private val distanceAlongTrip: Double?,
    private val totalDistanceAlongTrip: Double?,
    private val lastUpdateTime: Long,
    private val vehicleId: String?,
    private val activeTripId: String?,
    private val predicted: Boolean,
    private val hasLocation: Boolean,
) : ObaTripStatus {
    override fun getServiceDate(): Long = 0L
    override fun isPredicted(): Boolean = predicted
    override fun getScheduleDeviation(): Long = 0L
    override fun getVehicleId(): String? = vehicleId
    override fun getClosestStop(): String? = null
    override fun getClosestStopTimeOffset(): Long = 0L
    override fun getPosition(): Location? = null
    override fun getActiveTripId(): String? = activeTripId
    override fun getDistanceAlongTrip(): Double? = distanceAlongTrip
    override fun getScheduledDistanceAlongTrip(): Double? = null
    override fun getTotalDistanceAlongTrip(): Double? = totalDistanceAlongTrip
    override fun getOrientation(): Double? = null
    override fun getNextStop(): String? = null
    override fun getNextStopTimeOffset(): Long? = null
    override fun getPhase(): String? = null
    override fun getStatus(): Status? = null
    override fun getLastUpdateTime(): Long = lastUpdateTime
    override fun getLastKnownLocation(): Location? = if (hasLocation) FAKE_LOCATION else null
    override fun getLastLocationUpdateTime(): Long = 0L
    override fun getLastKnownDistanceAlongTrip(): Double? = null
    override fun getLastKnownOrientation(): Double? = null
    override fun getBlockTripSequence(): Int = 0
    override fun getOccupancyStatus(): Occupancy? = null

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
