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
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CorrectionSmoother]: the decaying-correction marker glide. Coordinates are kept
 * tiny so 1° ≈ 111 km dwarfs the 2 km snap gate only where a test wants it to; otherwise jumps stay
 * well under the gate so they smooth rather than snap.
 */
class CorrectionSmootherTest {

    private val duration = 600L

    private fun smoother() = CorrectionSmoother(duration)

    private fun p(lat: Double, lng: Double) = GeoPoint(lat, lng)

    @Test
    fun firstSighting_returnsTargetExactly() {
        val s = smoother()
        val out = s.displayPosition("k", p(1.0, 2.0), fixTimeMs = 100L, nowMs = 0L)
        assertEquals(1.0, out.latitude, 1e-12)
        assertEquals(2.0, out.longitude, 1e-12)
        assertFalse(s.isSettling("k"))
    }

    @Test
    fun sameFix_tracksTargetWithNoCorrection() {
        val s = smoother()
        s.displayPosition("k", p(1.0, 2.0), fixTimeMs = 100L, nowMs = 0L)
        // Same fix, advanced (dead-reckoned) target: returned verbatim, nothing settling.
        val out = s.displayPosition("k", p(1.0001, 2.0001), fixTimeMs = 100L, nowMs = 50L)
        assertEquals(1.0001, out.latitude, 1e-12)
        assertEquals(2.0001, out.longitude, 1e-12)
        assertFalse(s.isSettling("k"))
    }

    @Test
    fun freshFix_isPositionContinuousThenConverges() {
        val s = smoother()
        // Establish a shown position at the first fix.
        val shown = s.displayPosition("k", p(1.0, 2.0), fixTimeMs = 100L, nowMs = 0L)

        // A fresh fix whose dead-reckon target differs (a small correction, well under the snap gate).
        val target = p(1.001, 2.001)

        // At frac=0 the displayed point must equal the previously shown point (no jump).
        val atFix = s.displayPosition("k", target, fixTimeMs = 200L, nowMs = 1_000L)
        assertEquals(shown.latitude, atFix.latitude, 1e-9)
        assertEquals(shown.longitude, atFix.longitude, 1e-9)
        assertTrue(s.isSettling("k"))

        // Midway, the displayed point sits strictly between the old shown point and the new target
        // (here shown.lat < target.lat).
        val mid = s.displayPosition("k", target, fixTimeMs = 200L, nowMs = 1_000L + duration / 2)
        assertTrue(mid.latitude > shown.latitude && mid.latitude < target.latitude)

        // At/after the duration it has converged onto the target and stopped settling.
        val settled = s.displayPosition("k", target, fixTimeMs = 200L, nowMs = 1_000L + duration)
        assertEquals(target.latitude, settled.latitude, 1e-9)
        assertEquals(target.longitude, settled.longitude, 1e-9)
        assertFalse(s.isSettling("k"))
    }

    @Test
    fun freshFix_correctionDecaysMonotonically() {
        val s = smoother()
        s.displayPosition("k", p(0.0, 0.0), fixTimeMs = 1L, nowMs = 0L)
        val target = p(0.001, 0.0)
        var prevErr = Double.MAX_VALUE
        var t = 0L
        while (t <= duration) {
            val out = s.displayPosition("k", target, fixTimeMs = 2L, nowMs = 10_000L + t)
            val err = kotlin.math.abs(out.latitude - target.latitude) // shrinks from 0.001 toward 0
            assertTrue("error should not grow at t=$t", err <= prevErr + 1e-12)
            prevErr = err
            t += 50
        }
    }

    @Test
    fun implausibleJump_snaps() {
        val s = smoother()
        s.displayPosition("k", p(0.0, 0.0), fixTimeMs = 1L, nowMs = 0L)
        // ~111 km away (> 2 km gate): a fresh fix here must snap, not smooth.
        val target = p(1.0, 0.0)
        val out = s.displayPosition("k", target, fixTimeMs = 2L, nowMs = 1_000L)
        assertEquals(target.latitude, out.latitude, 1e-12)
        assertEquals(target.longitude, out.longitude, 1e-12)
        assertFalse(s.isSettling("k"))
    }

    @Test
    fun snapGate_smoothsJustUnderAndSnapsJustOver() {
        // 1° latitude ≈ 111.2 km, so these bracket the ~2 km snap gate.
        val under = smoother()
        under.prime("k", p(0.0, 0.0), fixTimeMs = 1L)
        under.displayPosition("k", p(0.013, 0.0), fixTimeMs = 2L, nowMs = 1_000L) // ~1.45 km
        assertTrue(under.isSettling("k"))

        val over = smoother()
        over.prime("k", p(0.0, 0.0), fixTimeMs = 1L)
        val out = over.displayPosition("k", p(0.022, 0.0), fixTimeMs = 2L, nowMs = 1_000L) // ~2.45 km
        assertFalse(over.isSettling("k"))
        assertEquals(0.022, out.latitude, 1e-12)
    }

    @Test
    fun settle_stepVanishesNearTheEnd() {
        // Zero terminal slope: the per-tick step shrinks toward the end (no lurch at the handoff).
        val s = smoother()
        s.prime("k", p(0.0, 0.0), fixTimeMs = 1L)
        val target = p(0.001, 0.0)
        val start = 10_000L
        s.displayPosition("k", target, fixTimeMs = 2L, nowMs = start) // arm at frac 0
        fun at(t: Long) = s.displayPosition("k", target, fixTimeMs = 2L, nowMs = t).latitude
        val eps = 5L
        val midStep = kotlin.math.abs(at(start + duration / 2 + eps) - at(start + duration / 2))
        val endStep = kotlin.math.abs(at(start + duration - eps) - at(start + duration - 2 * eps))
        assertTrue("step should shrink toward the end", endStep < midStep)
    }

    @Test
    fun prime_recordsShownPositionForLaterCorrection() {
        val s = smoother()
        // Prime at a creation point; the same fix then tracks the live target with no correction.
        s.prime("k", p(0.0, 0.0), fixTimeMs = 1L)
        val tracked = s.displayPosition("k", p(0.0001, 0.0), fixTimeMs = 1L, nowMs = 100L)
        assertEquals(0.0001, tracked.latitude, 1e-12)
        assertFalse(s.isSettling("k"))

        // A fresh fix corrects from the primed/shown baseline, not from the new target (no pop).
        val atFix = s.displayPosition("k", p(0.001, 0.0), fixTimeMs = 2L, nowMs = 1_000L)
        assertEquals(0.0001, atFix.latitude, 1e-9) // == the previously shown latitude
        assertTrue(s.isSettling("k"))
    }

    @Test
    fun retainOnly_dropsDepartedKeys() {
        val s = smoother()
        // Arm a correction on "gone" so it would be settling if retained.
        s.displayPosition("gone", p(0.0, 0.0), fixTimeMs = 1L, nowMs = 0L)
        s.displayPosition("gone", p(0.001, 0.0), fixTimeMs = 2L, nowMs = 100L)
        assertTrue(s.isSettling("gone"))

        s.retainOnly(setOf("kept"))
        assertFalse(s.isSettling("gone"))

        // After being dropped, the key is treated as a first sighting again (returns target exactly).
        val out = s.displayPosition("gone", p(5.0, 5.0), fixTimeMs = 9L, nowMs = 200L)
        assertEquals(5.0, out.latitude, 1e-12)
        assertFalse(s.isSettling("gone"))
    }
}
