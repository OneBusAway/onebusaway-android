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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.extrapolation.BAND_SEGMENT_COUNT
import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.extrapolation.buildTripExtrapolation
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
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

    /** A straight ~3.3 km line heading north along longitude -122. */
    private fun northLine() = Polyline(
        listOf(loc(47.00, -122.0), loc(47.01, -122.0), loc(47.02, -122.0), loc(47.03, -122.0))
    )

    @Test
    fun successProjectsVehicleFastAndBandOntoTheShape() {
        val extrapolation = buildTripExtrapolation(
            TripState("t", polyline = northLine()),
            ExtrapolationResult.Success(UniformDist(3000.0)),
            nowMs = 0L,
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
    fun missingShapeYieldsNoExtrapolation() {
        assertNull(
            buildTripExtrapolation(TripState("t"), ExtrapolationResult.Success(UniformDist(1000.0)), 0L)
        )
    }

    @Test
    fun nonSuccessHidesTheEstimate() {
        val extrapolation = buildTripExtrapolation(
            TripState("t", polyline = northLine()),
            ExtrapolationResult.NoData,
            nowMs = 0L,
        )!!
        assertNull(extrapolation.vehiclePoint)
        assertNull(extrapolation.fastEstimatePoint)
        assertTrue(extrapolation.band.isEmpty())
        assertNull(extrapolation.dataAge)
    }
}
