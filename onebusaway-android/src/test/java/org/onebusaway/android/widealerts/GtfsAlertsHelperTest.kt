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
 * Unit tests for the pure, [ServerTime]-driven [GtfsAlertsHelper.hasRecentlyStartedPeriod]. "Now" is
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
        assertTrue(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert(activeStartsSec = listOf(nowSec - hourSec)), now))
    }

    @Test
    fun `start 25 hours ago is not within 24 hours`() {
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert(activeStartsSec = listOf(nowSec - 25 * hourSec)), now))
    }

    @Test
    fun `start exactly 24 hours ago is still within the window`() {
        val alert = alert(activeStartsSec = listOf(nowSec - 24 * hourSec))
        assertTrue(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
        // One millisecond past the boundary is out.
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now + 1.milliseconds))
    }

    @Test
    fun `future-dated start is not within 24 hours`() {
        // Regression guard: a negative elapsed time must not slip past the upper bound.
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert(activeStartsSec = listOf(nowSec + hourSec)), now))
    }

    @Test
    fun `alert with no periods at all does not crash and is not surfaced`() {
        // Both period fields are optional in GTFS-RT, so an alert may carry neither. Must return
        // false, not crash on an empty list.
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert(), now))
    }

    @Test
    fun `range with no start is not treated as an epoch-zero start`() {
        // `start` is optional within a TimeRange too: an open-ended range began at "the beginning of
        // time", so it is never a recent start — and must not read as 0, which would also be false
        // but for the wrong reason. Pinned alongside a genuinely recent sibling range.
        val openEnded = GtfsRealtime.TimeRange.newBuilder().setEnd(nowSec + hourSec).build()
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert(activeRanges = listOf(openEnded)), now))
        assertTrue(
            GtfsAlertsHelper.hasRecentlyStartedPeriod(
                alert(activeRanges = listOf(openEnded, rangeStartingAt(nowSec - hourSec))),
                now
            )
        )
    }

    @Test
    fun `communication period only alert within 24 hours is surfaced`() {
        // #2160: a feed that only populates the new field must still be readable, not silenced.
        val alert = alert(communicationStartsSec = listOf(nowSec - hourSec))
        assertTrue(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
    }

    @Test
    fun `communication period only alert outside 24 hours is not surfaced`() {
        val alert = alert(communicationStartsSec = listOf(nowSec - 25 * hourSec))
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
    }

    @Test
    fun `alert with both fields prefers communication period over active period`() {
        // #2160: communication_period wins when both are populated, even if active_period alone
        // would put the alert outside the 24h window.
        val alert = alert(
            communicationStartsSec = listOf(nowSec - hourSec),
            activeStartsSec = listOf(nowSec - 25 * hourSec)
        )
        assertTrue(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
    }

    @Test
    fun `a later active period range within the window is surfaced despite a stale first range`() {
        // #2175: active_period is `repeated`, and the spec makes the alert applicable during any of
        // its ranges — a recurring closure whose first range is weeks old must not be silenced by
        // reading only index 0.
        val alert = alert(activeStartsSec = listOf(nowSec - 30 * 24 * hourSec, nowSec - 2 * hourSec))
        assertTrue(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
    }

    @Test
    fun `a later communication period range within the window is surfaced despite a stale first range`() {
        // #2175: the same repeated-field rule for the preferred communication_period list.
        val alert = alert(communicationStartsSec = listOf(nowSec - 30 * 24 * hourSec, nowSec - 2 * hourSec))
        assertTrue(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
    }

    @Test
    fun `every range outside the window is still not surfaced`() {
        // The any-match rule widens what's surfaced; it must not surface an alert with no recent
        // range at all — one stale, one future-dated.
        val alert = alert(communicationStartsSec = listOf(nowSec - 25 * hourSec, nowSec + hourSec))
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
    }

    @Test
    fun `a recent active period range does not rescue a populated communication period list`() {
        // #2160 precedence holds at the list level: once communication_period is populated it is the
        // authoritative list, and active_period is not consulted for any of its ranges.
        val alert = alert(
            communicationStartsSec = listOf(nowSec - 25 * hourSec),
            activeStartsSec = listOf(nowSec - hourSec)
        )
        assertFalse(GtfsAlertsHelper.hasRecentlyStartedPeriod(alert, now))
    }

    private fun rangeStartingAt(startSec: Long): GtfsRealtime.TimeRange = GtfsRealtime.TimeRange.newBuilder().setStart(startSec).build()

    /**
     * Every alert under test is built here so the `active_period` deprecation suppression has one
     * home rather than one per test. That field is deprecated in the proto but still the only one
     * many feeds populate, which is exactly why the helper reads it; see
     * [GtfsAlertsHelper.hasRecentlyStartedPeriod] and
     * https://github.com/OneBusAway/onebusaway-android/issues/2160.
     */
    @Suppress("DEPRECATION")
    private fun alert(
        communicationStartsSec: List<Long> = emptyList(),
        activeStartsSec: List<Long> = emptyList(),
        activeRanges: List<GtfsRealtime.TimeRange> = emptyList()
    ): GtfsRealtime.Alert = GtfsRealtime.Alert.newBuilder().apply {
        communicationStartsSec.forEach { addCommunicationPeriod(rangeStartingAt(it)) }
        activeStartsSec.forEach { addActivePeriod(rangeStartingAt(it)) }
        activeRanges.forEach { addActivePeriod(it) }
    }.build()
}
