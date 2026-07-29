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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.onebusaway.android.database.oba.NavigationSessionDao
import org.onebusaway.android.database.oba.NavigationSessionRecord
import org.onebusaway.android.time.WallTime

internal data class ActiveReminderSession(
    val plan: ReminderPlan,
    val state: ReminderEngineState
)

/**
 * How long a stored session stays resumable after it last made progress. A row quieter than this is
 * one the app failed to clean up — a force-stop or a crash — and resuming it would put the device
 * back into a foreground GPS session for a trip that ended long ago. Deliberately generous: the
 * bound exists to kill obvious zombies, not to trim a legitimately long journey.
 *
 * Measured from [NavigationSessionRecord.updatedAtMs], not from when the session began: a journey
 * that runs longer than this bound is a real journey, and judging it by its start date would retire
 * a session the service is still actively monitoring. A zombie, by contrast, stops being written to
 * the moment its process dies, so it ages out on schedule either way.
 *
 * Note what "made progress" means: [RoomReminderSessionStore.persist] runs on restoration-relevant
 * transitions, not on every location fix (that would be a database write per second), so a very long
 * ride with a quiet middle stretch can still age out between transitions. Closing that would take a
 * periodic heartbeat write; it is not worth one until a real journey hits it.
 *
 * Both sides are the device clock ([NavigationSessionRecord] timestamps are locally stamped), which
 * keeps the comparison inside [WallTime].
 */
private val MAX_RESUMABLE_SESSION_AGE = 12.hours

private const val TAG = "ReminderSessionStore"

/**
 * Where the store reports a session it threw away. A collaborator rather than a direct
 * `android.util.Log` call because [RoomReminderSessionStore] is otherwise plain Kotlin over DAOs and
 * is unit-tested as such: `android.util.Log` throws outside an Android runtime, which would
 * otherwise leave [RoomReminderSessionStore.restore]'s discard path — the decision this store exists
 * to get right — impossible to cover. Concrete and dependency-free, so Hilt constructs it with no
 * binding; tests override [warn].
 */
internal open class ReminderSessionLog @Inject constructor() {
    open fun warn(message: String) {
        Log.w(TAG, message)
    }
}

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
 * A stored session that cannot be resumed is discarded outright. There is deliberately nothing to
 * fall back to: the pre-Room `nav_stops` row is not a session in progress — its `is_active` flag was
 * written as 1 on every insert and never cleared by any shipped build, so it means "the last
 * reminder this rider ever set up", which may be years old. Reading it as a live session is how a
 * rider part-way through one journey ends up being monitored on a different, older one.
 */
internal fun NavigationSessionRecord.resumeOutcome(now: WallTime): StoredSessionOutcome = when {
    formatVersion != ReminderPlan.CURRENT_VERSION -> StoredSessionOutcome.Discard(
        "written by format version $formatVersion, this build reads ${ReminderPlan.CURRENT_VERSION}"
    )
    now - WallTime(updatedAtMs) > MAX_RESUMABLE_SESSION_AGE ->
        StoredSessionOutcome.Discard("no progress for over $MAX_RESUMABLE_SESSION_AGE")
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

/** Room-backed single-active-session store. */
internal class RoomReminderSessionStore @Inject constructor(
    private val sessions: NavigationSessionDao,
    private val log: ReminderSessionLog
) : ReminderSessionStore {
    /**
     * Applies the same two rules [resumeOutcome] does, so the screen can never offer to stop a
     * session [restore] would throw away — a row a force-stop left quiet overnight, or one written
     * by a format version this build cannot read.
     *
     * The cutoff is evaluated when the flow is collected, not when the store is built, so a screen
     * opened the next day is judged against that day's clock. It is then fixed for the life of the
     * subscription: a session that goes quiet past the bound while the screen stays open keeps
     * reading active until the next collection, which matches the service still monitoring it.
     */
    override val hasActiveSession: Flow<Boolean> = flow {
        emitAll(
            sessions.observeHasActiveSession(
                formatVersion = ReminderPlan.CURRENT_VERSION,
                updatedAtOrAfterMs = (WallTime.now() - MAX_RESUMABLE_SESSION_AGE).epochMs
            )
        )
    }

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
        val row = sessions.active() ?: return null
        return when (val outcome = row.resumeOutcome(now)) {
            is StoredSessionOutcome.Discard -> {
                log.warn("Discarding stored reminder session ${row.sessionId}: ${outcome.reason}")
                clear()
                null
            }
            is StoredSessionOutcome.Resume -> {
                if (outcome.progressionReset) {
                    log.warn("Reminder session ${row.sessionId} progression did not decode; monitoring from the start")
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
    }
}
