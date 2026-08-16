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
package org.onebusaway.android.ui.tripplan.pinned

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.onebusaway.android.database.oba.PinnedTripDao
import org.onebusaway.android.database.oba.PinnedTripRecord
import org.onebusaway.android.database.oba.SINGLE_PIN_ID
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.toJson
import org.onebusaway.android.directions.model.toTripItineraries
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripplan.TripPlanParams

private const val TAG = "PinnedTripStore"

/**
 * The version of the stored pin's payload this build writes and reads.
 *
 * It protects the joint shape of both blobs — [PinnedTripQuery] and the serialized [TripItinerary]
 * list — plus the meaning of the selected index. Bump it when a field is renamed, retyped, or comes to
 * mean something else; do **not** bump it for a purely additive field, which both decoders already
 * tolerate (`ignoreUnknownKeys`). The direction that needs catching is exactly the one that doesn't
 * announce itself: a rename decodes to a default rather than failing, and a pin silently restoring the
 * wrong trip is worse than one that declines to restore at all.
 */
internal const val PINNED_TRIP_FORMAT_VERSION = 1

/**
 * The trip the rider parked, decoded: the request they made and the plan it produced (#2053).
 *
 * @param departNow whether the trip was asked on the moving "now" anchor rather than a stated instant.
 *        Kept out of [params] because that is what the planner was *sent*; this is what the rider
 *        *meant*, and only the second one survives being resumed a day later.
 * @param itineraries every option the plan returned — see [PinnedTripRecord.itinerariesJson].
 * @param selectedIndex which of them the rider pinned; always a valid index into [itineraries], since
 *        a row where it isn't is discarded rather than coerced.
 * @param pinnedAt when the pin was taken. Carried for reporting only: a pin does not expire.
 */
data class PinnedTrip(
    val params: TripPlanParams,
    val departNow: Boolean,
    val itineraries: List<TripItinerary>,
    val selectedIndex: Int,
    val pinnedAt: WallTime
) {
    /** The option the rider actually pinned — the one the resume FAB describes. */
    val selectedItinerary: TripItinerary get() = itineraries[selectedIndex]
}

/** What a stored pin row is worth: see [pinnedOutcome]. */
internal sealed interface PinnedTripOutcome {
    data class Resume(val pin: PinnedTrip) : PinnedTripOutcome
    data class Discard(val reason: String) : PinnedTripOutcome
}

/**
 * Where the store reports a pin it threw away. A collaborator rather than a direct `android.util.Log`
 * call for the same reason [org.onebusaway.android.nav.ReminderSessionLog] is one: the store is
 * otherwise plain Kotlin over a DAO and is unit-tested as such, and `android.util.Log` throws outside
 * an Android runtime — which would leave the discard path, the decision this store exists to get
 * right, impossible to cover. Concrete and dependency-free, so Hilt builds it with no binding; tests
 * override [warn].
 */
internal open class PinnedTripLog @Inject constructor() {
    open fun warn(message: String) {
        Log.w(TAG, message)
    }
}

/**
 * The one parked trip plan. Exactly one is stored at a time — pinning replaces — which is also the
 * answer to "how do pins stop cluttering": there is nothing to accumulate.
 */
interface PinnedTripStore {
    /** The pinned trip as it changes, or null when nothing is pinned or the stored row is unreadable. */
    val pinned: Flow<PinnedTrip?>

    suspend fun pin(
        params: TripPlanParams,
        departNow: Boolean,
        itineraries: List<TripItinerary>,
        selectedIndex: Int,
        now: WallTime
    )

    suspend fun unpin()
}

/**
 * Decides what a stored pin row is worth. Pure — no IO, no Android — so the rules are verified
 * directly rather than through the database.
 *
 * Note the signature: unlike [org.onebusaway.android.nav.resumeOutcome] it takes **no clock**, because
 * there is no time-based rule to apply. A pin is a standing intention with no process behind it to be
 * orphaned, so it lives until the rider unpins it or pins something else; taking a `now` here would be
 * an invitation to invent an age bound nothing has justified.
 */
internal fun PinnedTripRecord.pinnedOutcome(): PinnedTripOutcome = when {
    formatVersion != PINNED_TRIP_FORMAT_VERSION -> PinnedTripOutcome.Discard(
        "written by format version $formatVersion, this build reads $PINNED_TRIP_FORMAT_VERSION"
    )
    else -> {
        val query = PinnedTripJson.decode(queryJson)
        val params = query?.toParamsOrNull()
        // `toTripItineraries` reports a corrupt payload and an empty one the same way — as an empty
        // list. That is enough here because a pin is never written empty: the results sheet only ever
        // exists over a non-empty PlanResult.Success. So empty *is* the corruption signal, and there
        // is no reason for a second, stricter decoder.
        val itineraries = itinerariesJson.toTripItineraries()
        when {
            params == null -> PinnedTripOutcome.Discard("query JSON did not decode")
            itineraries.isEmpty() -> PinnedTripOutcome.Discard("itinerary JSON did not decode")
            // Discarded rather than coerced into range. The rider pinned one option out of several,
            // and quietly resuming a different one would be a wrong answer wearing the right clothes.
            selectedIndex !in itineraries.indices ->
                PinnedTripOutcome.Discard("selected option $selectedIndex is not in the stored plan")
            else -> PinnedTripOutcome.Resume(
                PinnedTrip(
                    params = params,
                    departNow = query.departNow,
                    itineraries = itineraries,
                    selectedIndex = selectedIndex,
                    pinnedAt = WallTime(pinnedAtMs)
                )
            )
        }
    }
}

/** Room-backed single-slot pin store. */
internal class RoomPinnedTripStore @Inject constructor(
    private val pins: PinnedTripDao,
    private val log: PinnedTripLog
) : PinnedTripStore {

    /**
     * An unreadable row reads as "nothing pinned" rather than as an error the UI has to render: from
     * the rider's side those are the same fact, and the card simply doesn't appear. The row is left in
     * place — this is a read — and the next [pin] replaces it.
     */
    override val pinned: Flow<PinnedTrip?> = pins.observePinned().map { row ->
        when (val outcome = row?.pinnedOutcome()) {
            null -> null
            is PinnedTripOutcome.Discard -> {
                log.warn("Ignoring stored pinned trip: ${outcome.reason}")
                null
            }
            is PinnedTripOutcome.Resume -> outcome.pin
        }
    }

    override suspend fun pin(
        params: TripPlanParams,
        departNow: Boolean,
        itineraries: List<TripItinerary>,
        selectedIndex: Int,
        now: WallTime
    ) {
        // Refusing here rather than storing a row the reader would discard: the callers all have a
        // live plan in hand, so this can only be a wiring mistake, and a pin that vanishes on the next
        // read is a much harder thing to notice than one that never appears.
        if (selectedIndex !in itineraries.indices) {
            log.warn("Not pinning: option $selectedIndex is not in a plan of ${itineraries.size}")
            return
        }
        pins.replace(
            PinnedTripRecord(
                pinId = SINGLE_PIN_ID,
                formatVersion = PINNED_TRIP_FORMAT_VERSION,
                queryJson = PinnedTripJson.encode(params.toPinnedQuery(departNow)),
                itinerariesJson = itineraries.toJson(),
                selectedIndex = selectedIndex,
                pinnedAtMs = now.epochMs
            )
        )
    }

    override suspend fun unpin() {
        pins.clear()
    }
}
