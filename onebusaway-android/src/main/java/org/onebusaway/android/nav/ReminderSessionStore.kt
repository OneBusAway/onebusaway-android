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
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.NavigationSessionDao
import org.onebusaway.android.database.oba.NavigationSessionRecord
import org.onebusaway.android.database.oba.StopDao

internal data class ActiveReminderSession(
    val plan: ReminderPlan,
    val state: ReminderEngineState,
    val logFilePath: String?
)

internal interface ReminderSessionStore {
    suspend fun start(plan: ReminderPlan, nowMs: Long, logFilePath: String? = null)
    suspend fun restore(): ActiveReminderSession?
    suspend fun persist(plan: ReminderPlan, state: ReminderEngineState, nowMs: Long, logFilePath: String?)
    suspend fun clear()
}

/** Room-backed single-active-session store, including a read-through adapter for legacy nav_stops. */
internal class RoomReminderSessionStore @Inject constructor(
    private val sessions: NavigationSessionDao,
    private val legacySessions: NavStopDao,
    private val stops: StopDao
) : ReminderSessionStore {
    override suspend fun start(plan: ReminderPlan, nowMs: Long, logFilePath: String?) {
        sessions.replace(
            NavigationSessionRecord(
                sessionId = plan.sessionId,
                formatVersion = plan.version,
                planJson = ReminderPlanJson.encode(plan),
                stateJson = ReminderPlanJson.encodeState(ReminderEngineState()),
                startedAtMs = nowMs,
                updatedAtMs = nowMs,
                logFilePath = logFilePath
            )
        )
    }

    override suspend fun restore(): ActiveReminderSession? {
        sessions.active()?.let { row ->
            val plan = ReminderPlanJson.decode(row.planJson) ?: return@let
            val state = ReminderPlanJson.decodeState(row.stateJson) ?: return@let
            return ActiveReminderSession(plan, state, row.logFilePath)
        }
        return restoreLegacy()
    }

    override suspend fun persist(
        plan: ReminderPlan,
        state: ReminderEngineState,
        nowMs: Long,
        logFilePath: String?
    ) {
        sessions.updateState(plan.sessionId, ReminderPlanJson.encodeState(state), nowMs, logFilePath)
    }

    override suspend fun clear() {
        sessions.clear()
        legacySessions.clearAll()
    }

    private suspend fun restoreLegacy(): ActiveReminderSession? {
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
                scheduledStart = legacy.startTime,
                scheduledEnd = legacy.startTime
            ) as? ReminderPlanResult.Success
            )?.plan ?: return null
        start(plan, legacy.startTime)
        return ActiveReminderSession(plan, ReminderEngineState(), null)
    }
}
