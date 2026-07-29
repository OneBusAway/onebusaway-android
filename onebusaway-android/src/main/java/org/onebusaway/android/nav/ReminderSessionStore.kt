/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.nav

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.NavigationSessionDao
import org.onebusaway.android.database.oba.NavigationSessionRecord
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.time.WallTime

internal data class ActiveReminderSession(
    val plan: ReminderPlan,
    val state: ReminderEngineState
)

internal interface ReminderSessionStore {
    val hasActiveSession: Flow<Boolean>
    suspend fun start(plan: ReminderPlan, now: WallTime)
    suspend fun restore(now: WallTime): ActiveReminderSession?
    suspend fun persist(plan: ReminderPlan, state: ReminderEngineState, now: WallTime)
    suspend fun clear()
}

/** Room-backed single-active-session store, including a read-through adapter for legacy nav_stops. */
internal class RoomReminderSessionStore @Inject constructor(
    private val sessions: NavigationSessionDao,
    private val legacySessions: NavStopDao,
    private val stops: StopDao
) : ReminderSessionStore {
    override val hasActiveSession: Flow<Boolean> = sessions.observeHasActiveSession()

    override suspend fun start(plan: ReminderPlan, now: WallTime) {
        sessions.replace(
            NavigationSessionRecord(
                sessionId = plan.sessionId,
                formatVersion = plan.version,
                planJson = ReminderPlanJson.encode(plan),
                stateJson = ReminderPlanJson.encodeState(ReminderEngineState()),
                startedAtMs = now.epochMs,
                updatedAtMs = now.epochMs
            )
        )
    }

    override suspend fun restore(now: WallTime): ActiveReminderSession? {
        sessions.active()?.let { row ->
            val plan = ReminderPlanJson.decode(row.planJson)
            if (plan == null) {
                sessions.clear()
                return@let
            }
            val state = ReminderPlanJson.decodeState(row.stateJson) ?: ReminderEngineState()
            return ActiveReminderSession(plan, state)
        }
        return restoreLegacy(now)
    }

    override suspend fun persist(
        plan: ReminderPlan,
        state: ReminderEngineState,
        now: WallTime
    ) = withContext(Dispatchers.IO) {
        sessions.updateState(plan.sessionId, ReminderPlanJson.encodeState(state), now.epochMs)
    }

    override suspend fun clear() {
        sessions.clear()
        legacySessions.clearAll()
    }

    private suspend fun restoreLegacy(now: WallTime): ActiveReminderSession? {
        val legacy = legacySessions.active() ?: return null
        val before = stops.getStop(legacy.beforeId) ?: return null
        val destination = stops.getStop(legacy.destinationId) ?: return null
        val beforeStop = ReminderStop(
            before.id,
            before.name,
            ReminderPoint(before.latitude, before.longitude)
        )
        val destinationStop = ReminderStop(
            destination.id,
            destination.name,
            ReminderPoint(destination.latitude, destination.longitude)
        )
        val plan = (
            ReminderPlanBuilder.buildSingleRide(
                sessionId = legacy.navId,
                tripId = legacy.tripId,
                board = beforeStop,
                penultimate = beforeStop,
                alight = destinationStop,
                scheduledStart = null,
                scheduledEnd = null
            ) as? ReminderPlanResult.Success
            )?.plan ?: return null
        start(plan, now)
        return ActiveReminderSession(plan, ReminderEngineState())
    }
}
