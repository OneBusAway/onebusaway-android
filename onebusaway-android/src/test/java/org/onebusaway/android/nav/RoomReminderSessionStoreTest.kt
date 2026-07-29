/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.nav

import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.NavStopDetailsRow
import org.onebusaway.android.database.oba.NavStopRecord
import org.onebusaway.android.database.oba.NavigationSessionDao
import org.onebusaway.android.database.oba.NavigationSessionRecord
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopListRow
import org.onebusaway.android.database.oba.StopLocationRow
import org.onebusaway.android.database.oba.StopRecentRow
import org.onebusaway.android.database.oba.StopRecord
import org.onebusaway.android.database.oba.StopUserInfoMapRow
import org.onebusaway.android.database.oba.StopUserInfoRow
import org.onebusaway.android.time.WallTime

/**
 * Covers what a stored session is allowed to resume into. The risk here is not the SQL — the
 * migration test pins that — but the decisions this store makes when the row it finds is stale,
 * unreadable, or written by a different build.
 */
class RoomReminderSessionStoreTest {
    private val now = WallTime(1_000_000_000)

    @Test
    fun restoresAStoredSessionWithItsProgression() = runTest {
        val plan = plan()
        val state = ReminderEngineState(activeRideIndex = 0, getReadyEmitted = true, speechMuted = true)
        val sessions = FakeSessionDao(row(plan, state, startedAt = now - 1.hours))
        val store = store(sessions)

        val restored = store.restore(now)

        assertNotNull(restored)
        assertEquals(plan, restored!!.plan)
        assertTrue("a restored rider keeps their silence choice", restored.state.speechMuted)
        assertTrue(restored.state.getReadyEmitted)
    }

    @Test
    fun discardsASessionOlderThanTheResumableWindow() {
        // A force-stop or a crash leaves the row behind; resuming it days later would put the
        // device back into a foreground GPS session for a trip that is long over.
        val outcome = row(plan(), ReminderEngineState(), startedAt = now - 48.hours).resumeOutcome(now)

        assertTrue(outcome is StoredSessionOutcome.Discard)
    }

    @Test
    fun discardsASessionWrittenByAnotherFormatVersion() {
        val outcome = row(plan(), ReminderEngineState(), startedAt = now)
            .copy(formatVersion = ReminderPlan.CURRENT_VERSION + 1)
            .resumeOutcome(now)

        assertTrue(outcome is StoredSessionOutcome.Discard)
    }

    @Test
    fun anUnreadablePlanIsDiscardedRatherThanResumed() {
        // The caller must not fall back to the legacy nav_stops row, which describes a different,
        // single-ride trip: resuming that under a rider part-way through a multi-leg session would
        // monitor the wrong journey. Discard is the only non-resume outcome there is.
        val outcome = row(plan(), ReminderEngineState(), startedAt = now)
            .copy(planJson = "{not json")
            .resumeOutcome(now)

        assertTrue(outcome is StoredSessionOutcome.Discard)
    }

    @Test
    fun unreadableProgressionKeepsThePlanAndMonitorsFromTheStart() {
        val plan = plan()
        val outcome = row(plan, ReminderEngineState(), startedAt = now)
            .copy(stateJson = "{not json")
            .resumeOutcome(now)

        val resume = outcome as StoredSessionOutcome.Resume
        assertEquals(plan, resume.plan)
        assertEquals(ReminderEngineState(), resume.state)
        assertTrue(resume.progressionReset)
    }

    @Test
    fun aFreshRowResumesItsStoredProgression() {
        val state = ReminderEngineState(activeRideIndex = 0, alightNowEmitted = true)
        val outcome = row(plan(), state, startedAt = now - 1.hours).resumeOutcome(now)

        val resume = outcome as StoredSessionOutcome.Resume
        assertEquals(state, resume.state)
        assertFalse(resume.progressionReset)
    }

    @Test
    fun legacyRowIsResumableOnlyInsideTheWindow() {
        assertTrue(legacySessionResumable(now - 1.hours, now))
        assertFalse(legacySessionResumable(now - 100.hours, now))
    }

    @Test
    fun convertsARecentLegacyNavStopsRowIntoASingleRidePlan() = runTest {
        val legacy = FakeNavStopDao(legacyRow(startTime = (now - 1.hours).epochMs))
        val store = store(FakeSessionDao(), legacy)

        val restored = store.restore(now)

        val ride = restored?.plan?.rides?.single()
        assertEquals("trip", ride?.tripId)
        assertEquals("before", ride?.penultimate?.id)
        assertEquals("destination", ride?.alight?.id)
        assertNull("the legacy schema carries no boarding stop", ride?.board)
    }

    @Test
    fun ignoresAStaleLegacyNavStopsRow() = runTest {
        // The legacy schema leaves the last trip's row is_active=1 forever and the pre-Room
        // importer carries it across verbatim, so an upgrade can surface a years-old trip.
        val legacy = FakeNavStopDao(legacyRow(startTime = (now - 100.hours).epochMs))
        val store = store(FakeSessionDao(), legacy)

        assertNull(store.restore(now))
        assertNull("the zombie row must not be found again", legacy.row)
    }

    @Test
    fun missingLegacyStopsYieldNoSession() = runTest {
        val legacy = FakeNavStopDao(legacyRow(startTime = now.epochMs))
        val store = store(FakeSessionDao(), legacy, stops = FakeStopDao(emptyMap()))

        assertNull(store.restore(now))
    }

    @Test
    fun clearRemovesBothTheStoredAndTheLegacySession() = runTest {
        val sessions = FakeSessionDao(row(plan(), ReminderEngineState(), startedAt = now))
        val legacy = FakeNavStopDao(legacyRow(startTime = now.epochMs))

        store(sessions, legacy).clear()

        assertTrue(sessions.rows.isEmpty())
        assertNull(legacy.row)
    }

    @Test
    fun startSeedsFreshProgressionForANewSession() = runTest {
        val sessions = FakeSessionDao()
        val plan = plan()

        store(sessions).start(plan, now)

        val stored = sessions.rows.single()
        assertEquals(plan.sessionId, stored.sessionId)
        assertEquals(ReminderPlan.CURRENT_VERSION, stored.formatVersion)
        assertEquals(ReminderEngineState(), ReminderPlanJson.decodeState(stored.stateJson))
        assertFalse(stored.planJson.isEmpty())
    }

    private fun store(
        sessions: NavigationSessionDao,
        legacy: NavStopDao = FakeNavStopDao(),
        stops: StopDao = FakeStopDao(
            mapOf(
                "before" to stopRecord("before", 47.60),
                "destination" to stopRecord("destination", 47.61)
            )
        )
    ) = RoomReminderSessionStore(sessions, legacy, stops)

    private fun plan() = ReminderPlan(
        sessionId = "session",
        rides = listOf(
            ReminderRide(
                mode = ReminderMode.BUS,
                routeLabel = "10",
                tripId = "trip",
                board = ReminderStop("board", "Board", ReminderPoint(47.59, -122.3)),
                penultimate = ReminderStop("before", "Before", ReminderPoint(47.60, -122.3)),
                alight = ReminderStop("destination", "Destination", ReminderPoint(47.61, -122.3)),
                scheduledStart = null,
                scheduledEnd = null
            )
        )
    )

    private fun row(plan: ReminderPlan, state: ReminderEngineState, startedAt: WallTime) = NavigationSessionRecord(
        sessionId = plan.sessionId,
        formatVersion = plan.version,
        planJson = ReminderPlanJson.encode(plan),
        stateJson = ReminderPlanJson.encodeState(state),
        startedAtMs = startedAt.epochMs,
        updatedAtMs = startedAt.epochMs
    )

    private fun legacyRow(startTime: Long) = NavStopRecord(
        navId = "1",
        startTime = startTime,
        tripId = "trip",
        destinationId = "destination",
        beforeId = "before",
        sequence = 1,
        active = 1
    )

    private fun stopRecord(id: String, latitude: Double) = StopRecord(
        id = id,
        code = id,
        name = id,
        direction = "N",
        useCount = 0,
        latitude = latitude,
        longitude = -122.3
    )

    private class FakeSessionDao(initial: NavigationSessionRecord? = null) : NavigationSessionDao {
        val rows = mutableListOf<NavigationSessionRecord>().apply { initial?.let(::add) }

        override fun observeHasActiveSession(): Flow<Boolean> = flowOf(rows.isNotEmpty())

        override suspend fun active(): NavigationSessionRecord? = rows.maxByOrNull { it.updatedAtMs }

        override suspend fun upsert(record: NavigationSessionRecord) {
            rows.removeAll { it.sessionId == record.sessionId }
            rows += record
        }

        override suspend fun clear() = rows.clear()

        override suspend fun updateState(sessionId: String, stateJson: String, updatedAtMs: Long) {
            rows.replaceAll {
                if (it.sessionId == sessionId) it.copy(stateJson = stateJson, updatedAtMs = updatedAtMs) else it
            }
        }
    }

    private class FakeNavStopDao(var row: NavStopRecord? = null) : NavStopDao {
        override suspend fun active(): NavStopRecord? = row?.takeIf { it.active == 1 }

        override suspend fun clearAll() {
            row = null
        }

        override suspend fun insert(row: NavStopRecord) {
            this.row = row
        }

        override suspend fun getDetails(navId: String): NavStopDetailsRow? = row?.takeIf { it.navId == navId }?.let { NavStopDetailsRow(it.tripId, it.destinationId, it.beforeId) }
    }

    private class FakeStopDao(private val stops: Map<String, StopRecord>) : FakeStopDaoBase() {
        override suspend fun getStop(stopId: String): StopRecord? = stops[stopId]
    }
}

/** The unused half of the [StopDao] surface; the store only ever looks a stop up by id. */
private abstract class FakeStopDaoBase : StopDao {
    override suspend fun userInfo(stopId: String): StopUserInfoRow? = null

    override suspend fun setFavorite(stopId: String, favorite: Int) = Unit

    override suspend fun userInfoMap(): List<StopUserInfoMapRow> = emptyList()

    override suspend fun location(stopId: String): StopLocationRow? = null

    override suspend fun nameForStop(stopId: String): String? = null

    override suspend fun getStop(stopId: String): StopRecord? = null

    override suspend fun upsert(stop: StopRecord) = Unit

    override suspend fun markStopUsed(
        id: String,
        code: String,
        name: String,
        direction: String,
        latitude: Double,
        longitude: Double,
        regionId: Long?,
        now: Long
    ) = Unit

    override fun recents(cutoff: Long, regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())

    override fun recentsForSearch(cutoff: Long, regionId: Long?): Flow<List<StopRecentRow>> = flowOf(emptyList())

    override fun starredByName(regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())

    override fun starredByFrequency(regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())

    override fun favoriteStopIds(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun markUnused(stopId: String) = Unit

    override suspend fun markAllUnused() = Unit

    override suspend fun clearAllFavorites() = Unit
}
