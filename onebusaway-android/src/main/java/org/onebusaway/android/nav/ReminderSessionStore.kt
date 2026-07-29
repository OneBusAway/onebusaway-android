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

import android.util.Log
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
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

/**
 * How long a stored session stays resumable. A reminder session covers a single transit trip, so
 * anything older than this is a row the app failed to clean up — a force-stop, a crash, or a
 * legacy `nav_stops` row carried across the pre-Room import — and resuming it would put the device
 * back into a foreground GPS session for a trip that ended long ago. Deliberately generous: the
 * bound exists to kill obvious zombies, not to trim a legitimately long journey.
 *
 * Measured against the device clock in both cases ([NavigationSessionRecord.startedAtMs] and the
 * legacy row's `start_time` are locally stamped), which keeps the comparison inside [WallTime].
 */
private val MAX_RESUMABLE_SESSION_AGE = 12.hours

private const val TAG = "ReminderSessionStore"

internal interface ReminderSessionStore {
    val hasActiveSession: Flow<Boolean>
    suspend fun start(plan: ReminderPlan, now: WallTime)
    suspend fun restore(now: WallTime): ActiveReminderSession?
    suspend fun persist(plan: ReminderPlan, state: ReminderEngineState, now: WallTime)
    suspend fun clear()
}

/** What a stored session row is worth: see [resumeOutcome]. */
internal sealed interface StoredSessionOutcome {
    /**
     * [progressionReset] is set when the plan survived but its progression did not, so the ride is
     * monitored from the start again rather than the session being thrown away.
     */
    data class Resume(
        val plan: ReminderPlan,
        val state: ReminderEngineState,
        val progressionReset: Boolean
    ) : StoredSessionOutcome

    data class Discard(val reason: String) : StoredSessionOutcome
}

/**
 * Decides what to do with a stored session row. Pure — no IO, no Android — so the rules are
 * verified directly rather than through the database.
 *
 * Note there is no "fall back to the legacy row" outcome. A stored session that cannot be resumed
 * is discarded: the legacy `nav_stops` row describes a different, single-ride trip, and quietly
 * resuming that under a rider who was part-way through a multi-leg session would monitor the wrong
 * journey.
 */
internal fun NavigationSessionRecord.resumeOutcome(now: WallTime): StoredSessionOutcome = when {
    formatVersion != ReminderPlan.CURRENT_VERSION -> StoredSessionOutcome.Discard(
        "written by format version $formatVersion, this build reads ${ReminderPlan.CURRENT_VERSION}"
    )
    now - WallTime(startedAtMs) > MAX_RESUMABLE_SESSION_AGE ->
        StoredSessionOutcome.Discard("older than $MAX_RESUMABLE_SESSION_AGE")
    else -> {
        val plan = ReminderPlanJson.decode(planJson)
        if (plan == null) {
            StoredSessionOutcome.Discard("plan JSON did not decode")
        } else {
            val state = ReminderPlanJson.decodeState(stateJson)
            StoredSessionOutcome.Resume(plan, state ?: ReminderEngineState(), progressionReset = state == null)
        }
    }
}

/**
 * Whether a legacy `nav_stops` row is recent enough to resume. The legacy schema leaves the last
 * trip's row `is_active = 1` forever and the pre-Room importer carries it across verbatim, so an
 * upgrade can surface a years-old trip that would otherwise start a foreground GPS session the
 * rider can never finish.
 */
internal fun legacySessionResumable(startTime: WallTime, now: WallTime): Boolean = now - startTime <= MAX_RESUMABLE_SESSION_AGE

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
        val row = sessions.active() ?: return restoreLegacy(now)
        return when (val outcome = row.resumeOutcome(now)) {
            is StoredSessionOutcome.Discard -> {
                Log.w(TAG, "Discarding stored reminder session ${row.sessionId}: ${outcome.reason}")
                clear()
                null
            }
            is StoredSessionOutcome.Resume -> {
                if (outcome.progressionReset) {
                    Log.w(TAG, "Reminder session ${row.sessionId} progression did not decode; monitoring from the start")
                }
                ActiveReminderSession(outcome.plan, outcome.state)
            }
        }
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
        if (!legacySessionResumable(WallTime(legacy.startTime), now)) {
            legacySessions.clearAll()
            return null
        }
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
                // The legacy schema stores only the destination and the stop before it.
                board = null,
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
