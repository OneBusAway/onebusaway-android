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
package org.onebusaway.android.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.WallTime

/**
 * Pure-logic unit tests for [LocationFixes.isBetterFix] — the branchy core of `compareLocations`,
 * extracted so it is JVM-testable without `android.location.Location`.
 */
class IsBetterFixTest {

    private val base = 1_000_000L
    private val current = WallTime(base)
    private val thresholdMs = LocationFixes.TIME_THRESHOLD.inWholeMilliseconds
    private val goodAccuracy = LocationFixes.ACC_THRESHOLD_METERS - 1
    private val badAccuracy = LocationFixes.ACC_THRESHOLD_METERS + 1

    private fun isBetter(candidateMs: Long, accuracy: Float, nowMs: Long) =
        LocationFixes.isBetterFix(WallTime(candidateMs), accuracy, current, WallTime(nowMs))

    @Test
    fun `newer with good accuracy wins`() {
        assertTrue(isBetter(base + 1, goodAccuracy, nowMs = base))
    }

    @Test
    fun `newer with bad accuracy loses while the saved fix is still fresh`() {
        assertFalse(isBetter(base + 1, badAccuracy, nowMs = base + 100))
    }

    @Test
    fun `newer with bad accuracy wins once the saved fix is older than the time threshold`() {
        assertTrue(isBetter(base + 1, badAccuracy, nowMs = base + thresholdMs + 1))
    }

    @Test
    fun `age exactly at the threshold does not trigger the override`() {
        // now - current == TIME_THRESHOLD is not strictly greater, so a bad-accuracy fix still loses.
        assertFalse(isBetter(base + 1, badAccuracy, nowMs = base + thresholdMs))
    }

    @Test
    fun `an older fix never wins, even with good accuracy`() {
        assertFalse(isBetter(base - 1, goodAccuracy, nowMs = base))
    }

    @Test
    fun `an older fix never wins, even past the time threshold`() {
        assertFalse(isBetter(base - 1, badAccuracy, nowMs = base + thresholdMs + 1))
    }

    @Test
    fun `same timestamp is not newer, so it loses`() {
        assertFalse(isBetter(base, goodAccuracy, nowMs = base))
    }

    @Test
    fun `accuracy exactly at the threshold loses`() {
        // The accuracy gate is a strict less-than.
        assertFalse(isBetter(base + 1, LocationFixes.ACC_THRESHOLD_METERS, nowMs = base))
    }
}
