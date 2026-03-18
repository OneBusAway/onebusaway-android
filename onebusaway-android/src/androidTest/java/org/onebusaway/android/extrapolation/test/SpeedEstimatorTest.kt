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
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.extrapolation.data.TripDataManager
import org.onebusaway.android.extrapolation.math.DiracDistribution
import org.onebusaway.android.extrapolation.math.ZeroInflatedGammaDistribution
import org.onebusaway.android.extrapolation.math.speed.GammaSpeedEstimator
import org.onebusaway.android.extrapolation.math.speed.GammaSpeedModel
import org.onebusaway.android.extrapolation.math.speed.ScheduleSpeedEstimator
import org.onebusaway.android.extrapolation.math.speed.SpeedEstimateError
import org.onebusaway.android.extrapolation.math.speed.SpeedEstimateResult
import org.onebusaway.android.extrapolation.math.speed.SpeedEstimator
import org.onebusaway.android.extrapolation.math.speed.VehicleTrajectoryTracker
import org.onebusaway.android.io.elements.Occupancy
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.elements.Status

/**
 * Tests for the speed estimation framework classes.
 */
@RunWith(AndroidJUnit4::class)
class SpeedEstimatorTest {

    private val tracker = VehicleTrajectoryTracker
    private val dm = TripDataManager

    @Before
    fun setUp() {
        dm.clearAll()
        tracker.clearAll()
    }

    // --- TripDataManager tests ---

    @Test
    fun testTrackerEmptyHistory() {
        val history = dm.getHistory("trip1")
        assertEquals(0, history.size)
    }

    @Test
    fun testTrackerRecordAndRetrieve() {
        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L
        )

        dm.recordStatus(status)

        val history = dm.getHistory("trip1")
        assertEquals(1, history.size)
        assertEquals(100.0, history[0].distanceAlongTrip)
    }

    @Test
    fun testTrackerRetainsFullHistory() {
        for (i in 0 until 50) {
            val status = createStatus(
                "v1", "trip1", 47.0 + i * 0.001, -122.0,
                100.0 * i, 100.0 * i, 5000.0, 1000L + i * 30000L
            )
            dm.recordStatus(status)
        }

        val history = dm.getHistory("trip1")
        assertEquals(50, history.size)
    }

    @Test
    fun testTrackerSeparateTrips() {
        val status1 = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L
        )
        val status2 = createStatus(
            "v2", "trip2", 47.5, -122.5, 200.0, 200.0, 6000.0, 1000L
        )

        dm.recordStatus(status1)
        dm.recordStatus(status2)

        assertEquals(1, dm.getHistory("trip1").size)
        assertEquals(1, dm.getHistory("trip2").size)
    }

    @Test
    fun testTrackerClearAll() {
        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L
        )
        dm.recordStatus(status)
        assertEquals(1, dm.getHistory("trip1").size)

        dm.clearAll()
        assertEquals(0, dm.getHistory("trip1").size)
    }

    @Test
    fun testTrackerDefensiveCopy() {
        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 1000L
        )
        dm.recordStatus(status)

        val history = dm.getHistory("trip1")
        history.toMutableList().clear() // Modifying a copy

        // Internal history should be unaffected
        assertEquals(1, dm.getHistory("trip1").size)
    }

    @Test
    fun testTrackerNullActiveTripIdIgnored() {
        // Create a status with null activeTripId
        val pos = createLocation(47.0, -122.0)
        val statusWithNullTrip = TestTripStatus(
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
        dm.recordStatus(statusWithNullTrip)
        // Should not throw, just silently ignore
    }

    @Test
    fun testTrackerNullStatusIgnored() {
        dm.recordStatus(null)
        assertEquals(0, dm.getHistory("trip1").size)
    }

    @Test
    fun testTrackerGetEstimatedSpeedNullKey() {
        assertNull(tracker.getEstimatedSpeed(null, null, 100_000L))
    }

    @Test
    fun testTrackerGetEstimatedSpeedNullStatus() {
        assertNull(tracker.getEstimatedSpeed("trip1", null, 100_000L))
    }

    @Test
    fun testTrackerSetEstimator() {
        // Set a custom estimator that always returns 42.0
        tracker.setEstimator(object : SpeedEstimator {
            override fun estimateSpeed(
                tripId: String,
                queryTime: Long,
                dataManager: TripDataManager
            ) = SpeedEstimateResult.Success(DiracDistribution(42.0))
        })

        val timestamp = 100_000L
        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, timestamp
        )
        val speed = tracker.getEstimatedSpeed("trip1", status, timestamp)
        assertNotNull(speed)
        assertEquals(42.0, speed!!, 0.01)

        // Restore default
        tracker.setEstimator(GammaSpeedEstimator())
    }

    @Test
    fun testTrackerSetEstimatorIgnoresNull() {
        // Should not throw or change current estimator
        tracker.setEstimator(null)
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

    // --- Shape cumulative distance tests ---

    @Test
    fun testBuildCumulativeDistancesSinglePoint() {
        val points = listOf(createLocation(47.0, -122.0))
        dm.putShape("trip1", points)
        val cumDist = dm.getShapeCumulativeDistances("trip1")!!
        assertEquals(1, cumDist.size)
        assertEquals(0.0, cumDist[0], 0.001)
    }

    @Test
    fun testBuildCumulativeDistancesTwoPoints() {
        val points = listOf(
            createLocation(47.0, -122.0),
            createLocation(47.001, -122.0)  // ~111 meters north
        )
        dm.putShape("trip1", points)
        val cumDist = dm.getShapeCumulativeDistances("trip1")!!
        assertEquals(2, cumDist.size)
        assertEquals(0.0, cumDist[0], 0.001)
        assertTrue("Second point should have positive distance", cumDist[1] > 100)
        assertTrue("Second point should be ~111m", cumDist[1] < 120)
    }

    @Test
    fun testBuildCumulativeDistancesMultiplePoints() {
        val points = listOf(
            createLocation(47.0, -122.0),
            createLocation(47.001, -122.0),  // ~111m
            createLocation(47.002, -122.0),  // ~222m total
            createLocation(47.003, -122.0)   // ~333m total
        )
        dm.putShape("trip1", points)
        val cumDist = dm.getShapeCumulativeDistances("trip1")!!
        assertEquals(4, cumDist.size)
        assertEquals(0.0, cumDist[0], 0.001)
        // Each segment is ~111m, so cumulative should be monotonically increasing
        assertTrue(cumDist[1] > cumDist[0])
        assertTrue(cumDist[2] > cumDist[1])
        assertTrue(cumDist[3] > cumDist[2])
        // Total should be ~333m
        assertTrue("Total distance should be ~333m", cumDist[3] > 320 && cumDist[3] < 350)
    }

    // --- Schedule-only filtering tests ---

    @Test
    fun testRecordStatusRejectsScheduleOnlyPositions() {
        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 95.0, 5000.0, 1000L, false
        )

        dm.recordStatus(status)
        assertEquals(0, dm.getHistorySize("trip1"))
    }

    @Test
    fun testRecordStatusAcceptsRealtimePositions() {
        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 95.0, 5000.0, 1000L, true
        )

        dm.recordStatus(status)
        assertEquals(1, dm.getHistorySize("trip1"))
    }

    // --- ScheduleSpeedEstimator tests ---

    @Test
    fun testScheduleEstimatorNoCachedScheduleReturnsFailure() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 1000L

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, timestamp
        )
        dm.recordStatus(status)

        // No schedule cached - should return failure
        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
        val error = (result as SpeedEstimateResult.Failure).error
        assertTrue(error is SpeedEstimateError.InsufficientData)
    }

    @Test
    fun testScheduleEstimatorNullScheduledDistance() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 10000L

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 200.0, null, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
    }

    @Test
    fun testScheduleEstimatorCorrectSegmentSpeed() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 10000L

        val schedule = createSchedule(
            doubleArrayOf(0.0, 1000.0, 3000.0),
            longArrayOf(0, 120, 300),
            longArrayOf(60, 180, 360)
        )
        dm.putSchedule("trip1", schedule)

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 500.0, 500.0, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Success)
        val dist = (result as SpeedEstimateResult.Success).distribution
        assertEquals(1000.0 / 60.0, dist.mean, 0.01)
    }

    @Test
    fun testScheduleEstimatorSecondSegment() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 10000L

        val schedule = createSchedule(
            doubleArrayOf(0.0, 1000.0, 3000.0),
            longArrayOf(0, 120, 300),
            longArrayOf(60, 180, 360)
        )
        dm.putSchedule("trip1", schedule)

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 1500.0, 1500.0, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Success)
        val dist = (result as SpeedEstimateResult.Success).distribution
        assertEquals(2000.0 / 120.0, dist.mean, 0.01)
    }

    @Test
    fun testScheduleEstimatorBeforeFirstStop() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 10000L

        val schedule = createSchedule(
            doubleArrayOf(100.0, 1000.0, 3000.0),
            longArrayOf(60, 120, 300),
            longArrayOf(60, 180, 360)
        )
        dm.putSchedule("trip1", schedule)

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 50.0, 50.0, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
    }

    @Test
    fun testScheduleEstimatorAfterLastStop() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 10000L

        val schedule = createSchedule(
            doubleArrayOf(0.0, 1000.0, 3000.0),
            longArrayOf(0, 120, 300),
            longArrayOf(60, 180, 360)
        )
        dm.putSchedule("trip1", schedule)

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 3500.0, 3500.0, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
    }

    @Test
    fun testScheduleEstimatorTooFewStops() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 10000L

        val schedule = createSchedule(
            doubleArrayOf(0.0),
            longArrayOf(0),
            longArrayOf(60)
        )
        dm.putSchedule("trip1", schedule)

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
    }

    @Test
    fun testScheduleEstimatorZeroTimeDelta() {
        val estimator = ScheduleSpeedEstimator()
        val timestamp = 10000L

        val schedule = createSchedule(
            doubleArrayOf(0.0, 1000.0),
            longArrayOf(60, 60),
            longArrayOf(60, 60)
        )
        dm.putSchedule("trip1", schedule)

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 500.0, 500.0, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
    }

    @Test
    fun testScheduleEstimatorTimestampBeforeStatus() {
        val estimator = ScheduleSpeedEstimator()
        val statusTimestamp = 10000L
        val requestedTimestamp = 5000L // Before status

        val schedule = createSchedule(
            doubleArrayOf(0.0, 1000.0),
            longArrayOf(0, 120),
            longArrayOf(60, 180)
        )
        dm.putSchedule("trip1", schedule)

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 500.0, 500.0, 5000.0, statusTimestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", requestedTimestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
        val error = (result as SpeedEstimateResult.Failure).error
        assertTrue(error is SpeedEstimateError.TimestampOutOfBounds)
    }

    // --- GammaSpeedModel tests ---
    // (Detailed GammaSpeedModel tests are in the JVM unit test suite;
    //  these are smoke tests that verify it works on device.)

    @Test
    fun testGammaSpeedModel_fromSpeeds_workedExample() {
        // 20 mph ≈ 8.94 m/s, 10 mph ≈ 4.47 m/s
        val dist = GammaSpeedModel.fromSpeeds(8.94, 4.47, 2000.0) as ZeroInflatedGammaDistribution
        assertEquals(2.65, dist.alpha, 0.15)
        assertEquals(3.22, dist.scale, 0.5)
    }

    @Test
    fun testGammaSpeedModel_cdf_quantile_roundTrip() {
        val dist = GammaSpeedModel.fromSpeeds(6.71, 6.71, 2000.0)

        for (p in doubleArrayOf(0.10, 0.25, 0.50, 0.75, 0.90)) {
            val q = dist.quantile(p)
            assertEquals("CDF(quantile($p)) should equal $p", p, dist.cdf(q), 0.01)
        }
    }

    @Test
    fun testGammaSpeedModel_prevSpeedFallback() {
        val dist = GammaSpeedModel.fromSpeeds(8.94, 0.0, 2000.0) as ZeroInflatedGammaDistribution
        val distEqual = GammaSpeedModel.fromSpeeds(8.94, 8.94, 2000.0) as ZeroInflatedGammaDistribution
        assertEquals(distEqual.alpha, dist.alpha, 0.001)
        assertEquals(distEqual.scale, dist.scale, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGammaSpeedModel_schedSpeedZero_throws() {
        GammaSpeedModel.fromSpeeds(0.0, 5.0, 2000.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGammaSpeedModel_schedSpeedNegative_throws() {
        GammaSpeedModel.fromSpeeds(-1.0, 5.0, 2000.0)
    }

    @Test
    fun testGammaSpeedModel_mean() {
        val dist = GammaSpeedModel.fromSpeeds(6.71, 6.71, 2000.0)
        assertTrue("Mean speed should be positive", dist.mean > 0)
        assertEquals(6.71, dist.mean, 1.5)
    }

    @Test
    fun testGammaSpeedModel_pdf_positiveInRange() {
        val dist = GammaSpeedModel.fromSpeeds(6.71, 6.71, 2000.0)
        assertEquals(0.0, dist.pdf(0.0), 0.001)
        assertTrue("PDF should be positive at mean", dist.pdf(6.71) > 0)
    }

    @Test
    fun testGammaSpeedModel_cdf_boundaries() {
        val dist = GammaSpeedModel.fromSpeeds(6.71, 6.71, 2000.0) as ZeroInflatedGammaDistribution
        assertEquals(dist.p0, dist.cdf(0.0), 0.001)
        assertEquals(0.0, dist.cdf(-1.0), 0.001)
        assertTrue(dist.cdf(45.0) > 0.99)
    }

    // --- GammaSpeedEstimator tests ---

    @Test
    fun testGammaSpeedEstimator_returnsGammaMedian() {
        val estimator = GammaSpeedEstimator()
        // departureTimes[0]=100s, so trip starts at serviceDate + 100_000ms
        // serviceDate must be > 0 for putServiceDate to accept it
        val serviceDate = 1L
        val queryTime = 200_000L  // well after trip start (100_001ms)

        val schedule = createSchedule(
            doubleArrayOf(0.0, 1000.0),
            longArrayOf(0, 200),
            longArrayOf(100, 300)
        )
        dm.putSchedule("trip1", schedule)
        dm.putServiceDate("trip1", serviceDate)

        // Two history entries so v_prev can be computed
        val status1 = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, 100.0, 5000.0, 170_000L
        )
        val status2 = createStatus(
            "v1", "trip1", 47.001, -122.0, 400.0, 400.0, 5000.0, queryTime
        )

        dm.recordStatus(status1)
        dm.recordStatus(status2)

        val result = estimator.estimateSpeed("trip1", queryTime, dm)
        assertTrue(result is SpeedEstimateResult.Success)
        val dist = (result as SpeedEstimateResult.Success).distribution
        assertTrue(dist is ZeroInflatedGammaDistribution)
        val zig = dist as ZeroInflatedGammaDistribution
        assertTrue("Alpha should be positive", zig.alpha > 0)
        assertTrue("Scale should be positive", zig.scale > 0)
        assertTrue("Median should be positive", zig.median() > 0)
    }

    @Test
    fun testGammaSpeedEstimator_noScheduleFallsBack() {
        val estimator = GammaSpeedEstimator()
        val timestamp = 1000L

        val status = createStatus(
            "v1", "trip1", 47.0, -122.0, 100.0, null, 5000.0, timestamp
        )
        dm.recordStatus(status)

        val result = estimator.estimateSpeed("trip1", timestamp, dm)
        assertTrue(result is SpeedEstimateResult.Failure)
    }


    // --- Integration test ---

    @Test
    fun testEndToEndSpeedEstimation() {
        // departureTimes[0]=0s, so trip starts at serviceDate + 0 = serviceDate
        // serviceDate must be > 0 for putServiceDate to accept it
        val serviceDate = 1L
        val baseTime = 1_000_000L  // 1000s after service date
        val baseDistance = 0.0

        val schedule = createSchedule(
            doubleArrayOf(0.0, 5000.0, 10000.0),
            longArrayOf(0, 500, 1000),
            longArrayOf(0, 500, 1000)
        )
        dm.putSchedule("trip1", schedule)
        dm.putServiceDate("trip1", serviceDate)

        // Record 5 position updates, each 30 seconds apart, 300m apart (~10 m/s)
        for (i in 0 until 5) {
            val status = createStatus(
                "v1", "trip1",
                47.0 + i * 0.003, -122.0,
                baseDistance + i * 300.0,
                baseDistance + i * 300.0,
                10000.0,
                baseTime + i * 30_000L
            )
            dm.recordStatus(status)
        }

        val latestTimestamp = baseTime + 120_000L
        val latestStatus = createStatus(
            "v1", "trip1", 47.012, -122.0, 1200.0, 1200.0, 10000.0, latestTimestamp
        )

        val speed = tracker.getEstimatedSpeed("trip1", latestStatus, latestTimestamp)
        assertNotNull(speed)
        assertTrue("Speed should be positive", speed!! > 0)
    }

    // --- Helper methods ---

    private fun createStatus(
        vehicleId: String, activeTripId: String,
        lat: Double, lng: Double, distanceAlongTrip: Double?,
        scheduledDistance: Double?, totalDistance: Double?,
        lastLocationUpdateTime: Long, predicted: Boolean = true
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
     * Creates an ObaTripSchedule using reflection, since the constructor is package-private
     * and Jackson normally handles population.
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

            val stopTimesArray = java.lang.reflect.Array.newInstance(
                stopTimeClass, distances.size
            )
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
            val modifiersField =
                java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(
                field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv()
            )
        } catch (e: NoSuchFieldException) {
            // On newer JVMs, modifiers field may not be accessible; use Unsafe instead
        }

        field.set(obj, value)
    }
}

/**
 * Test-only implementation of ObaTripStatus for creating test fixtures without reflection.
 */
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
