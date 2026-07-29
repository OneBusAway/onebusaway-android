/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.ui.tripresults

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.nav.ActiveReminderSession
import org.onebusaway.android.nav.ReminderEngineState
import org.onebusaway.android.nav.ReminderMode
import org.onebusaway.android.nav.ReminderPlan
import org.onebusaway.android.nav.ReminderPoint
import org.onebusaway.android.nav.ReminderRide
import org.onebusaway.android.nav.ReminderSessionStore
import org.onebusaway.android.nav.ReminderStop
import org.onebusaway.android.time.WallTime

/**
 * The reminder service is started by session id, so the session has to be in the store before the
 * intent goes out. These pin that handshake from the caller's side: what the screen must know
 * before it starts anything.
 */
class ReminderControlViewModelTest {

    @Test
    fun storingASessionMakesItTheActiveOne() = runTest {
        val store = FakeStore()
        val plan = plan()

        assertTrue(ReminderControlViewModel(store).storeSession(plan).isSuccess)

        assertEquals(plan, store.started?.first)
    }

    @Test
    fun aFailedStoreIsReportedSoTheServiceIsNotStarted() = runTest {
        // Starting the service after a failed write would leave it hunting for a session that was
        // never written, so it would promote to the foreground only to stop again.
        val store = FakeStore(failOnStart = true)

        assertTrue(ReminderControlViewModel(store).storeSession(plan()).isFailure)
    }

    private fun plan() = ReminderPlan(
        sessionId = "session",
        rides = listOf(
            ReminderRide(
                mode = ReminderMode.BUS,
                routeLabel = "10",
                tripId = "trip",
                board = null,
                penultimate = ReminderStop("p", "P", ReminderPoint(47.60, -122.3)),
                alight = ReminderStop("a", "A", ReminderPoint(47.61, -122.3)),
                scheduledStart = null,
                scheduledEnd = null
            )
        )
    )

    private class FakeStore(private val failOnStart: Boolean = false) : ReminderSessionStore {
        var started: Pair<ReminderPlan, WallTime>? = null

        override val hasActiveSession: Flow<Boolean> = flowOf(false)

        override suspend fun start(plan: ReminderPlan, now: WallTime) {
            if (failOnStart) throw IllegalStateException("disk full")
            started = plan to now
        }

        override suspend fun restore(now: WallTime): ActiveReminderSession? = started?.let {
            ActiveReminderSession(it.first, ReminderEngineState())
        }

        override suspend fun persist(plan: ReminderPlan, state: ReminderEngineState, now: WallTime) = Unit

        override suspend fun clear() {
            started = null
        }
    }
}
