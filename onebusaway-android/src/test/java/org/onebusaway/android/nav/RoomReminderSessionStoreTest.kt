/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.nav

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.database.oba.NavigationSessionDao
import org.onebusaway.android.database.oba.NavigationSessionRecord
import org.onebusaway.android.database.oba.StopListRow
import org.onebusaway.android.database.oba.StopLocationRow
import org.onebusaway.android.database.oba.StopRecentRow
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
    fun discardsASessionThatHasBeenQuietLongerThanTheResumableWindow() {
        // A force-stop or a crash leaves the row behind and nothing writes to it again; resuming it
        // days later would put the device back into a foreground GPS session for a trip long over.
        val outcome = row(plan(), ReminderEngineState(), startedAt = now - 48.hours).resumeOutcome(now)

        assertTrue(outcome is StoredSessionOutcome.Discard)
    }

    @Test
    fun aLongJourneyIsJudgedByItsLastProgressNotItsStartDate() {
        // Started two days ago but still making progress: a real journey that outlasts the window is
        // not a zombie, and retiring it would drop a session the service is actively monitoring.
        val outcome = row(
            plan(),
            ReminderEngineState(),
            startedAt = now - 48.hours,
            updatedAt = now - 5.minutes
        ).resumeOutcome(now)

        assertTrue(outcome is StoredSessionOutcome.Resume)
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
        // Discard is the only non-resume outcome there is: there is deliberately nothing to fall
        // back to, so an undecodable plan can never hand the rider a different journey.
        val outcome = row(plan(), ReminderEngineState(), startedAt = now)
            .copy(planJson = "{not json")
            .resumeOutcome(now)

        assertTrue(outcome is StoredSessionOutcome.Discard)
    }

    @Test
    fun anUnreadableStoredPlanIsDiscardedByTheStore() = runTest {
        // Through the real store, not just the pure rule: an undecodable row must leave the rider
        // with no session at all, and must not be offered again on the next launch.
        val sessions = FakeSessionDao(
            row(plan(), ReminderEngineState(), startedAt = now).copy(planJson = "{not json")
        )
        val log = RecordingLog()

        assertNull(store(sessions, log = log).restore(now))
        assertTrue("the unreadable row must not be offered again", sessions.rows.isEmpty())
        assertTrue(log.warnings.single().contains("plan JSON did not decode"))
    }

    @Test
    fun aSessionFromAnotherFormatVersionIsDiscardedByTheStore() = runTest {
        val sessions = FakeSessionDao(
            row(plan(), ReminderEngineState(), startedAt = now)
                .copy(formatVersion = ReminderPlan.CURRENT_VERSION + 1)
        )
        val log = RecordingLog()

        assertNull(store(sessions, log = log).restore(now))
        assertTrue("the row this build cannot read must not be offered again", sessions.rows.isEmpty())
        assertTrue(log.warnings.single().contains("format version"))
    }

    @Test
    fun theActiveSessionSignalOnlyReportsRowsRestoreWouldResume() = runTest {
        // The screen picks its Start/Stop action off this, so anything it reports has to be
        // something restore() would actually resume — otherwise the rider is told to stop a
        // session nothing is monitoring, and cannot start one until they do.
        val fresh = row(plan(), ReminderEngineState(), startedAt = WallTime.now() - 1.hours)

        assertTrue(store(FakeSessionDao(fresh)).hasActiveSession.first())
        assertFalse(
            "a force-stopped row quiet since yesterday is not an active session",
            store(FakeSessionDao(fresh.copy(updatedAtMs = (WallTime.now() - 48.hours).epochMs)))
                .hasActiveSession.first()
        )
        assertTrue(
            "but a long journey still making progress is",
            store(
                FakeSessionDao(
                    fresh.copy(startedAtMs = (WallTime.now() - 48.hours).epochMs)
                )
            ).hasActiveSession.first()
        )
        assertFalse(
            "nor is one written by a format version this build cannot read",
            store(FakeSessionDao(fresh.copy(formatVersion = ReminderPlan.CURRENT_VERSION + 1)))
                .hasActiveSession.first()
        )
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
    fun clearRemovesTheStoredSession() = runTest {
        val sessions = FakeSessionDao(row(plan(), ReminderEngineState(), startedAt = now))

        store(sessions).clear()

        assertTrue(sessions.rows.isEmpty())
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
        log: ReminderSessionLog = RecordingLog()
    ) = RoomReminderSessionStore(sessions, log)

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

    private fun row(
        plan: ReminderPlan,
        state: ReminderEngineState,
        startedAt: WallTime,
        updatedAt: WallTime = startedAt
    ) = NavigationSessionRecord(
        sessionId = plan.sessionId,
        formatVersion = plan.version,
        planJson = ReminderPlanJson.encode(plan),
        stateJson = ReminderPlanJson.encodeState(state),
        startedAtMs = startedAt.epochMs,
        updatedAtMs = updatedAt.epochMs
    )

    /**
     * The store reports discards through [ReminderSessionLog] rather than `android.util.Log`, which
     * throws here — that indirection is what lets these tests run `restore()` itself instead of only
     * the pure [resumeOutcome] underneath it.
     */
    private class RecordingLog : ReminderSessionLog() {
        val warnings = mutableListOf<String>()

        override fun warn(message: String) {
            warnings += message
        }
    }

    private class FakeSessionDao(initial: NavigationSessionRecord? = null) : NavigationSessionDao {
        val rows = mutableListOf<NavigationSessionRecord>().apply { initial?.let(::add) }

        override fun observeHasActiveSession(formatVersion: Int, updatedAtOrAfterMs: Long): Flow<Boolean> = flowOf(
            rows.any { it.formatVersion == formatVersion && it.updatedAtMs >= updatedAtOrAfterMs }
        )

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
}
