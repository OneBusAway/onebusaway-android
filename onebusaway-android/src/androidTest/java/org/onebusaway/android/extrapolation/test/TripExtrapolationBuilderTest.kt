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

import org.onebusaway.android.time.WallTime
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.extrapolation.BAND_SEGMENT_COUNT
import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.extrapolation.buildTripExtrapolation
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.util.Polyline

/**
 * Geometry of [buildTripExtrapolation]: projecting a distribution onto the route shape. Instrumented
 * because [Polyline] interpolation uses [android.location.Location]. (The band-weighting math is
 * JVM-tested in UncertaintyBandTest.)
 */
@RunWith(AndroidJUnit4::class)
class TripExtrapolationBuilderTest {

    /** Uniform on [0, [width]]: median = width/2, quantile(p) = p*width, constant PDF. */
    private class UniformDist(private val width: Double) : ProbDistribution {
        override val mean = width / 2
        override fun pdf(x: Double) = if (x in 0.0..width) 1.0 / width else 0.0
        override fun cdf(x: Double) = (x / width).coerceIn(0.0, 1.0)
        override fun quantile(p: Double) = p * width
    }

    private fun loc(lat: Double, lng: Double) = Location("test").apply {
        latitude = lat
        longitude = lng
    }

    /** A minimal predicted [ObaTripStatus] anchor carrying only the fields the data-age marker reads. */
    private fun anchorStatus(position: Location?, distanceAlongTrip: Double?): ObaTripStatus =
        object : ObaTripStatus {
            override val serviceDate = 0L
            override val isPredicted = true
            override val scheduleDeviation = 0L
            override val vehicleId: String? = null
            override val closestStop: String? = null
            override val closestStopTimeOffset = 0L
            override val position = position
            override val activeTripId: String? = "t"
            override val distanceAlongTrip = distanceAlongTrip
            override val scheduledDistanceAlongTrip: Double? = null
            override val totalDistanceAlongTrip: Double? = null
            override val orientation: Double? = null
            override val nextStop: String? = null
            override val nextStopTimeOffset: Long? = null
            override val phase: String? = null
            override val status: Status? = null
            override val lastUpdateTime = 0L
            override val lastKnownLocation = position
            override val lastLocationUpdateTime = 0L
            override val lastKnownDistanceAlongTrip: Double? = null
            override val lastKnownOrientation: Double? = null
            override val blockTripSequence = 0
            override val occupancyStatus: Occupancy? = null
        }

    /** A straight ~3.3 km line heading north along longitude -122. */
    private fun northLine() = Polyline(
        listOf(loc(47.00, -122.0), loc(47.01, -122.0), loc(47.02, -122.0), loc(47.03, -122.0))
    )

    @Test
    fun successProjectsVehicleFastAndBandOntoTheShape() {
        val extrapolation = buildTripExtrapolation(
            TripState("t", polyline = northLine()),
            ExtrapolationResult.Success(UniformDist(3000.0)),
            nowMs = WallTime(0L),
        )!!

        val vehicle = extrapolation.vehiclePoint!!
        val fast = extrapolation.fastEstimatePoint!!
        // Both lie on the line (constant lng) between the endpoints; the fast estimate (q0.90) is
        // further along than the median.
        assertEquals(-122.0, vehicle.longitude, 1e-6)
        assertTrue(vehicle.latitude > 47.0 && vehicle.latitude < 47.03)
        assertTrue("fast estimate (q0.90) is further along than the median", fast.latitude > vehicle.latitude)

        assertEquals(BAND_SEGMENT_COUNT, extrapolation.band.size)
        extrapolation.band.forEach {
            // Uniform -> every slice at full weight (the color is applied later, by the view-model).
            assertEquals(1f, it.weight, 1e-4f)
            assertTrue(it.points.size >= 2)
        }
        assertNull("no anchor -> no data-age marker", extrapolation.dataAge)
    }

    @Test
    fun dataAgeMarkerFollowsProjectedAnchorDistanceNotRawPosition() {
        // Anchor's raw position is well OFF the shape (lng -122.5), but its distanceAlongTrip places it
        // mid-line (1500 m ~ lat 47.015 on the north line). The dot must follow the projected distance —
        // the same value the glide is seeded from — so it can't float ahead of the glide (regression from
        // a821321a8, which pinned the dot to the raw `position`).
        val extrapolation = buildTripExtrapolation(
            TripState(
                "t",
                anchor = anchorStatus(position = loc(47.5, -122.5), distanceAlongTrip = 1500.0),
                anchorLocalTimeMs = WallTime(1_000L),
                polyline = northLine(),
            ),
            ExtrapolationResult.Success(UniformDist(3000.0)),
            nowMs = WallTime(6_000L),
        )!!

        val dot = extrapolation.dataAge!!
        assertEquals("dot rides the shape (projected distance), not the off-route raw position",
            -122.0, dot.point.longitude, 1e-6)
        assertTrue(dot.point.latitude > 47.0 && dot.point.latitude < 47.03)
        // The projected dot coincides with the glide's origin: the median (q0.50 of the uniform over the
        // remaining shape) is at or ahead of it, never behind.
        assertTrue("glide median is not behind the data dot",
            extrapolation.vehiclePoint!!.latitude >= dot.point.latitude - 1e-9)
        assertEquals(5_000L, dot.ageMillis)
    }

    @Test
    fun dataAgeMarkerFallsBackToRawPositionWithoutDistance() {
        // No distanceAlongTrip to project (and no shape placement possible) → fall back to raw position.
        val extrapolation = buildTripExtrapolation(
            TripState(
                "t",
                anchor = anchorStatus(position = loc(47.5, -122.5), distanceAlongTrip = null),
                anchorLocalTimeMs = WallTime(1_000L),
                polyline = northLine(),
            ),
            ExtrapolationResult.NoData,
            nowMs = WallTime(1_000L),
        )!!

        val dot = extrapolation.dataAge!!
        assertEquals(-122.5, dot.point.longitude, 1e-6)
        assertEquals(47.5, dot.point.latitude, 1e-6)
    }

    @Test
    fun missingShapeYieldsNoExtrapolation() {
        assertNull(
            buildTripExtrapolation(TripState("t"), ExtrapolationResult.Success(UniformDist(1000.0)), WallTime(0L))
        )
    }

    @Test
    fun nonSuccessHidesTheEstimate() {
        val extrapolation = buildTripExtrapolation(
            TripState("t", polyline = northLine()),
            ExtrapolationResult.NoData,
            nowMs = WallTime(0L),
        )!!
        assertNull(extrapolation.vehiclePoint)
        assertNull(extrapolation.fastEstimatePoint)
        assertTrue(extrapolation.band.isEmpty())
        assertNull(extrapolation.dataAge)
    }
}
