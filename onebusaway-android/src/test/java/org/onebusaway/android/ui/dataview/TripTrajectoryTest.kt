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
package org.onebusaway.android.ui.dataview

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.junit.Test

class TripTrajectoryTest {

    private class UniformDist(private val width: Double) : ProbDistribution {
        override val mean = width / 2
        override fun pdf(x: Double) = if (x in 0.0..width) 1.0 / width else 0.0
        override fun cdf(x: Double) = (x / width).coerceIn(0.0, 1.0)
        override fun quantile(p: Double) = p * width
    }

    private class TriangularDist(private val c: Double) : ProbDistribution {
        override val mean = c
        override fun pdf(x: Double) = when {
            x < 0 || x > 2 * c -> 0.0
            x <= c -> x / (c * c)
            else -> (2 * c - x) / (c * c)
        }
        override fun cdf(x: Double) = when {
            x <= 0 -> 0.0
            x <= c -> x * x / (2 * c * c)
            x < 2 * c -> 1 - (2 * c - x) * (2 * c - x) / (2 * c * c)
            else -> 1.0
        }
        override fun quantile(p: Double) =
            if (p <= 0.5) c * sqrt(2 * p) else 2 * c - c * sqrt(2 * (1 - p))
    }

    private object NaNQuantileDist : ProbDistribution {
        override val mean = Double.NaN
        override fun pdf(x: Double) = 0.0
        override fun cdf(x: Double) = Double.NaN
        override fun quantile(p: Double) = Double.NaN
    }

    // --- pdfBins ---

    @Test
    fun `pdf bins span the quantile window and normalize to the peak`() {
        val bins = pdfBins(UniformDist(1500.0))
        assertEquals(PDF_BIN_COUNT, bins.size)
        bins.forEach { assertEquals("constant PDF -> all bins at the peak", 1.0, it.normalizedHeight, 1e-9) }
        assertTrue("first bin center is just inside the low quantile", bins.first().distanceMeters > 0.0)
        assertTrue("last bin center is below the 95th pct (1425)", bins.last().distanceMeters < 1425.0)
    }

    @Test
    fun `pdf bin heights peak at one for a non-uniform distribution`() {
        val bins = pdfBins(TriangularDist(500.0))
        assertEquals(PDF_BIN_COUNT, bins.size)
        assertEquals(1.0, bins.maxOf { it.normalizedHeight }, 1e-9)
        assertTrue(bins.all { it.normalizedHeight in 0.0..1.0 })
    }

    @Test
    fun `pdf bin count is configurable`() {
        assertEquals(20, pdfBins(UniformDist(1000.0), binCount = 20).size)
    }

    @Test
    fun `a degenerate distribution yields no pdf bins`() {
        assertTrue(pdfBins(NaNQuantileDist).isEmpty())
    }

    // --- dataBounds ---

    @Test
    fun `data bounds pad the extent of the points`() {
        val bounds = dataBounds(distances = listOf(100.0, 500.0, 300.0), times = listOf(1_000L, 5_000L))
        assertEquals(80.0, bounds.minDist, 1e-9) // 100 - 5% of 400
        assertEquals(520.0, bounds.maxDist, 1e-9) // 500 + 5% of 400
        assertEquals(0L, bounds.minTime) // 1000 - max(5% of 4000, 1000) = 1000 - 1000
        assertEquals(6_000L, bounds.maxTime) // 5000 + 1000
    }

    @Test
    fun `empty data bounds are a non-degenerate unit box`() {
        val bounds = dataBounds(emptyList(), emptyList())
        assertTrue("distance extent is non-zero", bounds.maxDist > bounds.minDist)
        assertTrue("time extent is non-zero", bounds.maxTime > bounds.minTime)
    }

    // --- buildTrajectory ---

    @Test
    fun `an empty snapshot yields an empty trajectory with a drawable viewport`() {
        val trajectory = buildTrajectory(TripState("trip1"), nowMs = 10_000L)
        assertTrue(trajectory.observations.isEmpty())
        assertTrue(trajectory.schedule.isEmpty())
        assertNull("no anchor -> no extrapolation", trajectory.extrapolation)
        assertTrue(trajectory.bounds.maxDist > trajectory.bounds.minDist)
        assertTrue(trajectory.bounds.maxTime > trajectory.bounds.minTime)
    }

    // --- interpolateScheduleTime ---

    private fun stop(dist: Double, arriveMs: Long, departMs: Long = arriveMs) =
        ScheduleStop(distanceMeters = dist, arrivalMs = arriveMs, departureMs = departMs, stopId = null)

    @Test
    fun `schedule time interpolates linearly within the bracketing stops`() {
        val schedule = listOf(stop(0.0, 1_000L), stop(100.0, 3_000L))
        // 75% of the way to the next stop -> 75% of the way through the time span.
        assertEquals(2_500L, interpolateScheduleTime(schedule, 75.0))
    }

    @Test
    fun `schedule time interpolates from the prior departure across a dwell`() {
        val schedule = listOf(stop(0.0, 1_000L, departMs = 2_000L), stop(100.0, 6_000L))
        // Segment runs from stop0's departure (2000) to stop1's arrival (6000).
        assertEquals(4_000L, interpolateScheduleTime(schedule, 50.0))
    }

    @Test
    fun `schedule time is zero outside the interpolatable span or with too few stops`() {
        val schedule = listOf(stop(0.0, 1_000L), stop(100.0, 3_000L))
        assertEquals(0L, interpolateScheduleTime(schedule, 150.0))
        assertEquals(0L, interpolateScheduleTime(listOf(stop(0.0, 1_000L)), 0.0))
        assertEquals(0L, interpolateScheduleTime(emptyList(), 0.0))
    }

    @Test
    fun `a distance below the first stop returns the sentinel, not a negative-fraction time`() {
        // Guards the lower-bound check: without it, -10 on a [0,100] schedule would compute a negative
        // fraction and return a time before the first departure instead of the 0 sentinel.
        val schedule = listOf(stop(0.0, 1_000L), stop(100.0, 3_000L))
        assertEquals(0L, interpolateScheduleTime(schedule, -10.0))

        // Same below-origin case when the first stop is not at distance 0.
        val offsetSchedule = listOf(stop(50.0, 1_000L), stop(150.0, 3_000L))
        assertEquals(0L, interpolateScheduleTime(offsetSchedule, 40.0))
    }

    @Test
    fun `a distance exactly on an interior stop maps to a single segment, not both`() {
        val schedule = listOf(
            stop(0.0, 1_000L, departMs = 2_000L),
            stop(100.0, 6_000L, departMs = 7_000L), // dwell: arrives 6000, departs 7000
            stop(200.0, 10_000L),
        )
        // Exactly at the middle stop, the half-open [d0, d1) segments give it to the *next* segment,
        // so it reads the departure (7000) deterministically rather than ambiguously matching the
        // prior segment's arrival (6000).
        assertEquals(7_000L, interpolateScheduleTime(schedule, 100.0))
    }

    @Test
    fun `a distance exactly at the final stop interpolates to its arrival`() {
        val schedule = listOf(
            stop(0.0, 1_000L),
            stop(100.0, 6_000L),
            stop(200.0, 10_000L),
        )
        // The trip's end distance is excluded by the half-open segments, so it's mapped to the last
        // segment's arrival (the final stop, 10000) rather than falling through to the 0 sentinel.
        assertEquals(10_000L, interpolateScheduleTime(schedule, 200.0))
    }

    @Test
    fun `the trip-end distance stays interpolatable when degenerate stops trail the end`() {
        val schedule = listOf(
            stop(0.0, 1_000L),
            stop(100.0, 6_000L),
            stop(100.0, 10_000L), // trailing zero-length pair (shared shape_dist_traveled)
        )
        // The last *interpolatable* pair is 0 -> 100 (the trailing 100 -> 100 pair forms no segment),
        // so the max distance maps to that segment's arrival (6000) instead of falling through to 0.
        assertEquals(6_000L, interpolateScheduleTime(schedule, 100.0))
    }

    @Test
    fun `a distance exactly at the first stop interpolates to the start time`() {
        val schedule = listOf(stop(0.0, 1_000L, departMs = 2_000L), stop(100.0, 6_000L))
        // The span is lower-inclusive, so the trip origin resolves to the first segment's start
        // (stop0's departure, 2000) rather than the sentinel.
        assertEquals(2_000L, interpolateScheduleTime(schedule, 0.0))
    }

    @Test
    fun `an interior degenerate segment is skipped and a later segment interpolates`() {
        val schedule = listOf(
            stop(0.0, 1_000L),
            stop(100.0, 6_000L, departMs = 7_000L),
            stop(100.0, 8_000L), // degenerate middle pair: skipped
            stop(200.0, 12_000L),
        )
        // 150 lands in the 100 -> 200 segment (departure 8000 to arrival 12000), proving the loop
        // advances past the degenerate middle pair.
        assertEquals(10_000L, interpolateScheduleTime(schedule, 150.0))
    }

    // --- scheduleSegments ---

    @Test
    fun `segments span each stop pair from the prior departure to the next arrival`() {
        val schedule = listOf(stop(0.0, 1_000L, departMs = 2_000L), stop(100.0, 6_000L))
        assertEquals(listOf(ScheduleSegment(0.0, 100.0, 2_000L, 6_000L)), scheduleSegments(schedule))
    }

    @Test
    fun `fewer than two stops yields no segments`() {
        assertTrue(scheduleSegments(emptyList()).isEmpty())
        assertTrue(scheduleSegments(listOf(stop(0.0, 1_000L))).isEmpty())
    }

    @Test
    fun `a trailing zero-length stop pair is dropped`() {
        val schedule = listOf(stop(0.0, 1_000L), stop(100.0, 6_000L), stop(100.0, 10_000L))
        // The degenerate 100 -> 100 pair produces no segment; the real 0 -> 100 segment remains the last.
        assertEquals(listOf(ScheduleSegment(0.0, 100.0, 1_000L, 6_000L)), scheduleSegments(schedule))
    }

    @Test
    fun `a run of equal-distance stops collapses to first-arrival-in, last-departure-out`() {
        val schedule = listOf(
            stop(0.0, 1_000L),
            stop(100.0, 6_000L, departMs = 7_000L), // first at 100: its arrival (6000) ends the entering segment
            stop(100.0, 8_000L), // last at 100: its departure (8000) starts the leaving segment
            stop(200.0, 12_000L),
        )
        assertEquals(
            listOf(
                ScheduleSegment(0.0, 100.0, 1_000L, 6_000L),
                ScheduleSegment(100.0, 200.0, 8_000L, 12_000L),
            ),
            scheduleSegments(schedule),
        )
    }

    @Test
    fun `a ScheduleSegment rejects non-increasing distance`() {
        // The factory never emits a degenerate segment, but the guard catches any future
        // out-of-factory construction with d1 <= d0 loudly instead of dividing by zero downstream.
        assertThrows(IllegalArgumentException::class.java) { ScheduleSegment(100.0, 100.0, 0L, 1L) }
        assertThrows(IllegalArgumentException::class.java) { ScheduleSegment(100.0, 50.0, 0L, 1L) }
    }

    // --- formatDeviationLabel ---

    @Test
    fun `deviation label reads late, early, or on time`() {
        assertEquals("on time", formatDeviationLabel(0))
        assertEquals("30s late", formatDeviationLabel(30))
        assertEquals("30s early", formatDeviationLabel(-30))
        assertEquals("2m late", formatDeviationLabel(120))
        assertEquals("1m30s early", formatDeviationLabel(-90))
    }
}
