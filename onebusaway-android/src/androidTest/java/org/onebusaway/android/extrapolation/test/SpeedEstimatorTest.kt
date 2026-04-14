/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.test

import android.location.Location
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.extrapolation.data.TripDataManager
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.elements.Occupancy
import org.onebusaway.android.io.elements.Status

/** Tests for the speed estimation framework classes. */
@RunWith(AndroidJUnit4::class)
class SpeedEstimatorTest {

    private val dm = TripDataManager

    @Before
    fun setUp() {
        dm.clearAll()
    }

    // --- TripDataManager tests ---

    @Test
    fun testTrackerEmptyHistory() {
        val history = dm.getHistory("trip1")
        assertEquals(0, history.size)
    }

    @Test
    fun testTrackerRecordAndRetrieve() {
        val status = createStatus("v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L)

        dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())

        val history = dm.getHistory("trip1")
        assertEquals(1, history.size)
        assertEquals(100.0, history[0].distanceAlongTrip!!, 1e-12)
    }

    @Test
    fun testTrackerRetainsFullHistory() {
        for (i in 0 until 50) {
            val status =
                    createStatus(
                            "v1",
                            "trip1",
                            47.0 + i * 0.001,
                            -122.0,
                            100.0 * i,
                            100.0 * i,
                            5000.0,
                            1000L + i * 30000L
                    )
            dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())
        }

        val history = dm.getHistory("trip1")
        assertEquals(50, history.size)
    }

    @Test
    fun testTrackerSeparateTrips() {
        val status1 = createStatus("v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L)
        val status2 = createStatus("v2", "trip2", 47.5, -122.5, 200.0, 200.0, 6000.0, 1000L)

        dm.recordStatus(status1, System.currentTimeMillis(), System.currentTimeMillis())
        dm.recordStatus(status2, System.currentTimeMillis(), System.currentTimeMillis())

        assertEquals(1, dm.getHistory("trip1").size)
        assertEquals(1, dm.getHistory("trip2").size)
    }

    @Test
    fun testTrackerClearAll() {
        val status = createStatus("v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L)
        dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())
        assertEquals(1, dm.getHistory("trip1").size)

        dm.clearAll()
        assertEquals(0, dm.getHistory("trip1").size)
    }

    @Test
    fun testTrackerDefensiveCopy() {
        val status = createStatus("v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L)
        dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())

        val history = dm.getHistory("trip1")
        history.toMutableList().clear() // Modifying a copy

        // Internal history should be unaffected
        assertEquals(1, dm.getHistory("trip1").size)
    }

    @Test
    fun testTrackerNullActiveTripIdIgnored() {
        // Create a status with null activeTripId
        val pos = createLocation(47.0, -122.0)
        val statusWithNullTrip =
                TestTripStatus(
                        vehicleId = "v1",
                        activeTripId = null,
                        position = pos,
                        lastKnownLocation = pos,
                        distanceAlongTrip = 100.0,
                        lastKnownDistanceAlongTrip = null,
                        scheduledDistanceAlongTrip = 100.0,
                        totalDistanceAlongTrip = 5000.0,
                        lastUpdateTime = 1000L,
                        lastLocationUpdateTime = 1000L,
                        scheduleDeviation = 0L,
                        predicted = true
                )
        dm.recordStatus(statusWithNullTrip, System.currentTimeMillis(), System.currentTimeMillis())
        // Should not throw, just silently ignore
    }

    @Test
    fun testTrackerNullStatusIgnored() {
        dm.recordStatus(null, System.currentTimeMillis(), System.currentTimeMillis())
        assertEquals(0, dm.getHistory("trip1").size)
    }

    // --- Eviction tests ---

    @Test
    fun testEvictionUsesAccessOrder() {
        // Insert MAX_TRACKED_TRIPS distinct trips so we're sitting at the cap with the registry
        // ordered by insertion. trip0 is the oldest by insertion order.
        val cap = 100
        for (i in 0 until cap) {
            val status =
                    createStatus("v$i", "trip$i", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L + i)
            dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())
        }
        assertEquals(cap, dm.getTrackedTripIds().size)

        // Touch trip0 (the insertion-oldest) to promote it to most-recently-accessed.
        // Any read goes through getTrip(); getLastState exercises that path.
        assertNotNull(dm.getLastState("trip0"))

        // Now insert one more trip. With access ordering, the LRU is trip1, not trip0.
        val pushOver = createStatus("vNew", "tripNew", 47.0, -122.0, 100.0, 100.0, 5000.0, 9999L)
        dm.recordStatus(pushOver, System.currentTimeMillis(), System.currentTimeMillis())

        val tracked = dm.getTrackedTripIds()
        assertEquals(cap, tracked.size)
        assertTrue("trip0 should survive eviction because it was just accessed", "trip0" in tracked)
        assertFalse("trip1 should have been evicted as the LRU", "trip1" in tracked)
        assertTrue("tripNew should be tracked", "tripNew" in tracked)
    }

    @Test
    fun testEvictionRecordingExistingTripDoesNotEvict() {
        // Recording an update for an already-tracked trip must not push another trip out:
        // it bumps the existing entry to the tail without growing the registry.
        val cap = 100
        for (i in 0 until cap) {
            val status =
                    createStatus("v$i", "trip$i", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L + i)
            dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())
        }
        assertEquals(cap, dm.getTrackedTripIds().size)

        // Re-record trip0 with a fresh status — same tripId, different distance and timestamp
        // so the dedup in Trip.recordStatus actually appends.
        val update = createStatus("v0", "trip0", 47.001, -122.0, 200.0, 200.0, 5000.0, 2000L)
        dm.recordStatus(update, System.currentTimeMillis(), System.currentTimeMillis())

        val tracked = dm.getTrackedTripIds()
        assertEquals(cap, tracked.size)
        assertTrue("trip0 should still be present after self-update", "trip0" in tracked)
        assertEquals(2, dm.getHistorySize("trip0"))
    }

    // --- TripDataManager schedule cache tests ---

    @Test
    fun testTrackerScheduleCacheEmpty() {
        assertNull(dm.getSchedule("trip1"))
        assertFalse(dm.isScheduleCached("trip1"))
    }

    @Test
    fun testTrackerPutAndGetSchedule() {
        val schedule = ObaTripSchedule.EMPTY_OBJECT
        dm.putSchedule("trip1", schedule)
        assertNotNull(dm.getSchedule("trip1"))
        assertTrue(dm.isScheduleCached("trip1"))
    }

    @Test
    fun testTrackerPutScheduleNullIgnored() {
        dm.putSchedule(null, ObaTripSchedule.EMPTY_OBJECT)
        dm.putSchedule("trip1", null)
        assertNull(dm.getSchedule("trip1"))
    }

    @Test
    fun testTrackerClearAllClearsScheduleCache() {
        dm.putSchedule("trip1", ObaTripSchedule.EMPTY_OBJECT)
        assertTrue(dm.isScheduleCached("trip1"))

        dm.clearAll()
        assertFalse(dm.isScheduleCached("trip1"))
        assertNull(dm.getSchedule("trip1"))
    }

    // --- Polyline interpolation tests ---

    @Test
    fun testPolylineSinglePoint() {
        val points = listOf(createLocation(47.0, -122.0))
        dm.putShape("trip1", points)
        val poly = dm.getPolyline("trip1")!!
        val result = poly.interpolate(50.0)!!
        assertEquals(47.0, result.latitude, 1e-12)
        assertEquals(-122.0, result.longitude, 1e-12)
    }

    @Test
    fun testPolylineTwoPoints() {
        val points =
                listOf(
                        createLocation(47.0, -122.0),
                        createLocation(47.001, -122.0) // ~111 meters north
                )
        dm.putShape("trip1", points)
        val poly = dm.getPolyline("trip1")!!
        // Interpolate at 0 — should return first point
        val start = poly.interpolate(0.0)!!
        assertEquals(47.0, start.latitude, 1e-12)
        // Interpolate beyond end — should return last point
        val end = poly.interpolate(1000.0)!!
        assertEquals(47.001, end.latitude, 1e-6)
    }

    @Test
    fun testPolylineMultiplePointsMidInterpolation() {
        val points =
                listOf(
                        createLocation(47.0, -122.0),
                        createLocation(47.001, -122.0), // ~111m
                        createLocation(47.002, -122.0), // ~222m total
                        createLocation(47.003, -122.0) // ~333m total
                )
        dm.putShape("trip1", points)
        val poly = dm.getPolyline("trip1")!!
        // Interpolate at ~166m — should be between second and third point
        val mid = poly.interpolate(166.0)!!
        assertTrue(
                "Should be between 47.001 and 47.002",
                mid.latitude > 47.001 && mid.latitude < 47.002
        )
    }

    // --- Schedule-only filtering tests ---

    // --- GammaExtrapolator tests ---

    @Test
    fun testGammaExtrapolator_returnsDistanceDistribution() {
        val serviceDate = 1L
        val queryTime = 200_000L

        val schedule =
                createSchedule(
                        doubleArrayOf(0.0, 1000.0),
                        longArrayOf(0, 200),
                        longArrayOf(100, 300)
                )
        dm.putSchedule("trip1", schedule)
        dm.putServiceDate("trip1", serviceDate)

        val status1 = createStatus("v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 170_000L)
        val status2 = createStatus("v1", "trip1", 47.001, -122.0, 400.0, 400.0, 5000.0, queryTime)

        dm.recordStatus(status1, System.currentTimeMillis(), System.currentTimeMillis())
        dm.recordStatus(status2, System.currentTimeMillis(), System.currentTimeMillis())

        val result = dm.getOrCreateTrip("trip1").extrapolate(queryTime + 5000)
        assertTrue("Should succeed", result is ExtrapolationResult.Success)
        val dist = (result as ExtrapolationResult.Success).distribution
        assertTrue("Median distance should be > last distance", dist.median() > 400.0)
    }

    @Test
    fun testGammaExtrapolator_noScheduleReturnsMissingSchedule() {
        val timestamp = 1000L

        val status = createStatus("v1", "trip1", 47.0, -122.0, 100.0, null, 5000.0, timestamp)
        dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())

        val result = dm.getOrCreateTrip("trip1").extrapolate(timestamp)
        assertTrue("Should be MissingSchedule", result is ExtrapolationResult.MissingSchedule)
    }

    // --- Integration test ---

    @Test
    fun testEndToEndSpeedEstimation() {
        // departureTimes[0]=0s, so trip starts at serviceDate + 0 = serviceDate
        // serviceDate must be > 0 for putServiceDate to accept it
        val serviceDate = 1L
        val baseTime = 1_000_000L // 1000s after service date
        val baseDistance = 0.0

        val schedule =
                createSchedule(
                        doubleArrayOf(0.0, 5000.0, 10000.0),
                        longArrayOf(0, 500, 1000),
                        longArrayOf(0, 500, 1000)
                )
        dm.putSchedule("trip1", schedule)
        dm.putServiceDate("trip1", serviceDate)

        // Record 5 position updates, each 30 seconds apart, 300m apart (~10 m/s)
        for (i in 0 until 5) {
            val status =
                    createStatus(
                            "v1",
                            "trip1",
                            47.0 + i * 0.003,
                            -122.0,
                            baseDistance + i * 300.0,
                            baseDistance + i * 300.0,
                            10000.0,
                            baseTime + i * 30_000L
                    )
            dm.recordStatus(status, System.currentTimeMillis(), System.currentTimeMillis())
        }

        val latestTimestamp = baseTime + 120_000L
        val latestStatus =
                createStatus(
                        "v1",
                        "trip1",
                        47.012,
                        -122.0,
                        1200.0,
                        1200.0,
                        10000.0,
                        latestTimestamp
                )

        dm.recordStatus(latestStatus, System.currentTimeMillis(), System.currentTimeMillis())
        val result = dm.getOrCreateTrip("trip1").extrapolate(latestTimestamp)
        assertTrue("Should succeed", result is ExtrapolationResult.Success)
        val dist = (result as ExtrapolationResult.Success).distribution
        assertTrue("Extrapolated distance should be positive", dist.median() > 0)
    }

    // --- Helper methods ---

    private fun createStatus(
            vehicleId: String,
            activeTripId: String,
            lat: Double,
            lng: Double,
            distanceAlongTrip: Double?,
            scheduledDistance: Double?,
            totalDistance: Double?,
            lastLocationUpdateTime: Long,
            predicted: Boolean = true
    ): ObaTripStatus {
        val pos = createLocation(lat, lng)
        return TestTripStatus(
                vehicleId = vehicleId,
                activeTripId = activeTripId,
                position = pos,
                lastKnownLocation = pos,
                distanceAlongTrip = distanceAlongTrip,
                lastKnownDistanceAlongTrip = null,
                scheduledDistanceAlongTrip = scheduledDistance,
                totalDistanceAlongTrip = totalDistance,
                lastUpdateTime = lastLocationUpdateTime,
                lastLocationUpdateTime = lastLocationUpdateTime,
                scheduleDeviation = 0L,
                predicted = predicted
        )
    }

    private fun createLocation(lat: Double, lng: Double): Location {
        val loc = Location("test")
        loc.latitude = lat
        loc.longitude = lng
        return loc
    }

    /**
     * Creates an ObaTripSchedule using reflection, since the constructor is package-private and
     * Jackson normally handles population.
     */
    private fun createSchedule(
            distances: DoubleArray,
            arrivalTimes: LongArray,
            departureTimes: LongArray
    ): ObaTripSchedule {
        try {
            val stopTimeClass = ObaTripSchedule.StopTime::class.java
            val stCtor = stopTimeClass.getDeclaredConstructor()
            stCtor.isAccessible = true

            val stopTimesArray = java.lang.reflect.Array.newInstance(stopTimeClass, distances.size)
            for (i in distances.indices) {
                val st = stCtor.newInstance()
                setField(st, "distanceAlongTrip", distances[i])
                setField(st, "arrivalTime", arrivalTimes[i])
                setField(st, "departureTime", departureTimes[i])
                setField(st, "stopId", "stop_$i")
                java.lang.reflect.Array.set(stopTimesArray, i, st)
            }

            val schedCtor = ObaTripSchedule::class.java.getDeclaredConstructor()
            schedCtor.isAccessible = true
            val schedule = schedCtor.newInstance()
            setField(schedule, "stopTimes", stopTimesArray)

            return schedule
        } catch (e: Exception) {
            throw RuntimeException("Failed to create test schedule", e)
        }
    }

    private fun setField(obj: Any, fieldName: String, value: Any?) {
        val field = obj.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true

        try {
            val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
        } catch (e: NoSuchFieldException) {
            // On newer JVMs, modifiers field may not be accessible; use Unsafe instead
        }

        field.set(obj, value)
    }
}

/** Test-only implementation of ObaTripStatus for creating test fixtures without reflection. */
private class TestTripStatus(
        private val vehicleId: String?,
        private val activeTripId: String?,
        private val position: Location?,
        private val lastKnownLocation: Location?,
        private val distanceAlongTrip: Double?,
        private val lastKnownDistanceAlongTrip: Double?,
        private val scheduledDistanceAlongTrip: Double?,
        private val totalDistanceAlongTrip: Double?,
        private val lastUpdateTime: Long,
        private val lastLocationUpdateTime: Long,
        private val scheduleDeviation: Long,
        private val predicted: Boolean
) : ObaTripStatus {
    override fun getServiceDate(): Long = 0L
    override fun isPredicted(): Boolean = predicted
    override fun getScheduleDeviation(): Long = scheduleDeviation
    override fun getVehicleId(): String? = vehicleId
    override fun getClosestStop(): String? = null
    override fun getClosestStopTimeOffset(): Long = 0L
    override fun getPosition(): Location? = position
    override fun getActiveTripId(): String? = activeTripId
    override fun getDistanceAlongTrip(): Double? = distanceAlongTrip
    override fun getScheduledDistanceAlongTrip(): Double? = scheduledDistanceAlongTrip
    override fun getTotalDistanceAlongTrip(): Double? = totalDistanceAlongTrip
    override fun getOrientation(): Double? = null
    override fun getNextStop(): String? = null
    override fun getNextStopTimeOffset(): Long? = null
    override fun getPhase(): String? = null
    override fun getStatus(): Status? = null
    override fun getLastUpdateTime(): Long = lastUpdateTime
    override fun getLastKnownLocation(): Location? = lastKnownLocation
    override fun getLastLocationUpdateTime(): Long = lastLocationUpdateTime
    override fun getLastKnownDistanceAlongTrip(): Double? = lastKnownDistanceAlongTrip
    override fun getLastKnownOrientation(): Double? = null
    override fun getBlockTripSequence(): Int = 0
    override fun getOccupancyStatus(): Occupancy? = null
}
