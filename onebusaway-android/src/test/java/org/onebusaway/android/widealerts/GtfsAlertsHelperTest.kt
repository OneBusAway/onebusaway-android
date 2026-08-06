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
package org.onebusaway.android.widealerts

import com.google.transit.realtime.GtfsRealtime
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * Unit tests for the pure, [ServerTime]-driven [GtfsAlertsHelper.isStartDateWithin24Hours]. "Now" is
 * the feed's server clock (#1612), so these pin the 24h cutoff, the boundary, and the future-start
 * guard against the server time passed in. ([GtfsAlertsHelper.isValidEntity] additionally reads the
 * alerts DB via a Context, so it isn't a pure JVM unit and is out of scope here.)
 */
class GtfsAlertsHelperTest {

    // A round server "now": epoch seconds 1_700_000_000 on the feed's own clock.
    private val nowSec = 1_700_000_000L
    private val now = GtfsAlertsHelper.serverTimeFromGtfsSeconds(nowSec)
    private val hourSec = 3_600L

    @Test
    fun `start one hour ago is within 24 hours`() {
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alertStartingAt(nowSec - hourSec), now))
    }

    @Test
    fun `start 25 hours ago is not within 24 hours`() {
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alertStartingAt(nowSec - 25 * hourSec), now))
    }

    @Test
    fun `start exactly 24 hours ago is still within the window`() {
        val alert = alertStartingAt(nowSec - 24 * hourSec)
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
        // One millisecond past the boundary is out.
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now + 1.milliseconds))
    }

    @Test
    fun `future-dated start is not within 24 hours`() {
        // Regression guard: a negative elapsed time must not slip past the upper bound.
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alertStartingAt(nowSec + hourSec), now))
    }

    @Test
    fun `alert with no active period does not crash and is not surfaced`() {
        // active_period is optional in GTFS-RT; getActivePeriod(0) would throw. Must return false, not crash.
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(GtfsRealtime.Alert.newBuilder().build(), now))
    }

    @Test
    // Exercises the deprecated active_period fallback; see https://github.com/OneBusAway/onebusaway-android/issues/2160.
    @Suppress("DEPRECATION")
    fun `range with no start is not treated as an epoch-zero start`() {
        // `start` is optional within a TimeRange too: an open-ended range began at "the beginning of
        // time", so it is never a recent start — and must not read as 0, which would also be false
        // but for the wrong reason. Pinned alongside a genuinely recent sibling range.
        val openEnded = GtfsRealtime.TimeRange.newBuilder().setEnd(nowSec + hourSec).build()
        assertFalse(
            GtfsAlertsHelper.isStartDateWithin24Hours(
                GtfsRealtime.Alert.newBuilder().addActivePeriod(openEnded).build(),
                now
            )
        )
        assertTrue(
            GtfsAlertsHelper.isStartDateWithin24Hours(
                GtfsRealtime.Alert.newBuilder()
                    .addActivePeriod(openEnded)
                    .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - hourSec).build())
                    .build(),
                now
            )
        )
    }

    @Test
    fun `communication period only alert within 24 hours is surfaced`() {
        // #2160: a feed that only populates the new field must still be readable, not silenced.
        val alert = GtfsRealtime.Alert.newBuilder()
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - hourSec).build())
            .build()
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
    }

    @Test
    fun `communication period only alert outside 24 hours is not surfaced`() {
        val alert = GtfsRealtime.Alert.newBuilder()
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 25 * hourSec).build())
            .build()
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
    }

    @Test
    // Exercises the deprecated active_period fallback; see https://github.com/OneBusAway/onebusaway-android/issues/2160.
    @Suppress("DEPRECATION")
    fun `alert with both fields prefers communication period over active period`() {
        // #2160: communication_period wins when both are populated, even if active_period alone
        // would put the alert outside the 24h window.
        val alert = GtfsRealtime.Alert.newBuilder()
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - hourSec).build())
            .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 25 * hourSec).build())
            .build()
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
    }

    @Test
    // Exercises the deprecated active_period fallback; see https://github.com/OneBusAway/onebusaway-android/issues/2160.
    @Suppress("DEPRECATION")
    fun `a later active period range within the window is surfaced despite a stale first range`() {
        // #2175: active_period is `repeated`, and the spec makes the alert applicable during any of
        // its ranges — a recurring closure whose first range is weeks old must not be silenced by
        // reading only index 0.
        val alert = GtfsRealtime.Alert.newBuilder()
            .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 30 * 24 * hourSec).build())
            .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 2 * hourSec).build())
            .build()
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
    }

    @Test
    fun `a later communication period range within the window is surfaced despite a stale first range`() {
        // #2175: the same repeated-field rule for the preferred communication_period list.
        val alert = GtfsRealtime.Alert.newBuilder()
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 30 * 24 * hourSec).build())
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 2 * hourSec).build())
            .build()
        assertTrue(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
    }

    @Test
    fun `every range outside the window is still not surfaced`() {
        // The any-match rule widens what's surfaced; it must not surface an alert with no recent
        // range at all — one stale, one future-dated.
        val alert = GtfsRealtime.Alert.newBuilder()
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 25 * hourSec).build())
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec + hourSec).build())
            .build()
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
    }

    @Test
    // Exercises the deprecated active_period fallback; see https://github.com/OneBusAway/onebusaway-android/issues/2160.
    @Suppress("DEPRECATION")
    fun `a recent active period range does not rescue a populated communication period list`() {
        // #2160 precedence holds at the list level: once communication_period is populated it is the
        // authoritative list, and active_period is not consulted for any of its ranges.
        val alert = GtfsRealtime.Alert.newBuilder()
            .addCommunicationPeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - 25 * hourSec).build())
            .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(nowSec - hourSec).build())
            .build()
        assertFalse(GtfsAlertsHelper.isStartDateWithin24Hours(alert, now))
    }

    // Mirrors the deprecated-but-still-universal `active_period` fallback read in the helper
    // under test; see the rationale on [GtfsAlertsHelper.isStartDateWithin24Hours].
    @Suppress("DEPRECATION")
    private fun alertStartingAt(startSec: Long): GtfsRealtime.Alert = GtfsRealtime.Alert.newBuilder()
        .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(startSec).build())
        .build()
}
