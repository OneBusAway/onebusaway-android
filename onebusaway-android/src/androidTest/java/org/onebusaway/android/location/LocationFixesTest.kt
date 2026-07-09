/*
 * Copyright (C) 2017 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.location

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.time.WallTime

/**
 * Behavioral tests for [LocationFixes] against real [Location] instances (whose setters are stubbed
 * on the JVM, so these run instrumented). The pure branch logic is unit-tested in the JVM
 * `IsBetterFixTest`; the `now` a comparison is judged against is a fixed value here, making the
 * previously wall-clock-dependent cases deterministic.
 */
@RunWith(AndroidJUnit4::class)
class LocationFixesTest {

    // NOT named `time`: inside a `Location(...).apply { }` block the receiver is the Location, so an
    // unqualified `time` would resolve to `Location.getTime()` (0 on a fresh fix), not this constant —
    // silently zeroing every timestamp the cases build. `baseTime` has no such collision.
    private val baseTime = 1_700_000_000_000L
    private val now = WallTime(baseTime)
    private val timeThresholdMs = LocationFixes.TIME_THRESHOLD.inWholeMilliseconds

    @Test
    fun testLocationComparisonByTime() {
        // Non-null location is preferred
        assertTrue(LocationFixes.compareLocationsByTime(Location("test"), null))
        assertFalse(LocationFixes.compareLocationsByTime(null, Location("test")))

        // Location with greater (i.e., newer) timestamp is preferred
        val a = Location("test").apply { this.time = 1001 }
        val b = Location("test").apply { this.time = 1000 }
        assertTrue(LocationFixes.compareLocationsByTime(a, b))

        a.time = 1000
        b.time = 1001
        assertFalse(LocationFixes.compareLocationsByTime(a, b))
    }

    @Test
    fun testLocationComparison() {
        // Non-null location is preferred
        assertTrue(LocationFixes.compareLocations(Location("test"), null, now))
        assertFalse(LocationFixes.compareLocations(null, Location("test"), now))

        // We always want the newer location
        var a = Location("test").apply { this.time = baseTime + 1 }
        var b = Location("test").apply { this.time = baseTime }
        assertTrue(LocationFixes.compareLocations(a, b, now))

        a.time = baseTime
        b.time = baseTime + 1
        assertFalse(LocationFixes.compareLocations(a, b, now))

        // The new location is saved if the old location is older than the time threshold, even if
        // its accuracy is worse
        a = Location("test").apply {
            this.time = baseTime // A is newer
            accuracy = LocationFixes.ACC_THRESHOLD_METERS + 1 // 1 meter worse than threshold
        }
        b = Location("test").apply {
            accuracy = LocationFixes.ACC_THRESHOLD_METERS - 1 // 1 meter better than threshold
            this.time = baseTime - timeThresholdMs - 1 // older than time threshold
        }
        assertTrue(LocationFixes.compareLocations(a, b, now))

        // A is older, so this should fail, since we never want an older location
        a = Location("test").apply {
            this.time = baseTime - timeThresholdMs - 2 // A is older
            accuracy = LocationFixes.ACC_THRESHOLD_METERS + 1 // 1 meter worse than threshold
        }
        b = Location("test").apply {
            accuracy = LocationFixes.ACC_THRESHOLD_METERS - 1 // 1 meter better than threshold
            this.time = baseTime - timeThresholdMs - 1 // older than time threshold
        }
        assertFalse(LocationFixes.compareLocations(a, b, now))

        // A newer location is preferred, as long as it has a reasonable accuracy
        a = Location("test").apply {
            this.time = baseTime + 1 // A is newer
            accuracy = LocationFixes.ACC_THRESHOLD_METERS - 1 // 1 meter better than threshold
        }
        b = Location("test").apply { this.time = baseTime }
        assertTrue(LocationFixes.compareLocations(a, b, now))

        a = Location("test").apply {
            this.time = baseTime + 1 // A is newer
            accuracy = LocationFixes.ACC_THRESHOLD_METERS + 1 // 1 meter worse than threshold
        }
        b = Location("test").apply { this.time = baseTime }
        assertFalse(LocationFixes.compareLocations(a, b, now))
    }

    @Test
    fun testIsDuplicate() {
        val locA = Location("A").apply {
            time = 1234
            latitude = 33.3
            longitude = 66.6
        }

        // Location that is the same
        val locDupA = Location("A").apply {
            time = 1234
            latitude = 33.3
            longitude = 66.6
        }
        assertTrue(LocationFixes.isDuplicate(locA, locDupA))

        // Locations that aren't the same
        val locBTimeDiff = Location("A").apply {
            time = 9876
            latitude = 33.3
            longitude = 66.6
        }
        assertFalse(LocationFixes.isDuplicate(locA, locBTimeDiff))

        val locBLatDiff = Location("A").apply {
            time = 1234
            latitude = 89.9
            longitude = 66.6
        }
        assertFalse(LocationFixes.isDuplicate(locA, locBLatDiff))

        val locBLonDiff = Location("A").apply {
            time = 1234
            latitude = 33.3
            longitude = 10.0
        }
        assertFalse(LocationFixes.isDuplicate(locA, locBLonDiff))
    }
}
