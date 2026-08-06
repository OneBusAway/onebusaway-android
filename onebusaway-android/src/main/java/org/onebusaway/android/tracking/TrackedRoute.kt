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
package org.onebusaway.android.tracking

import kotlinx.serialization.Serializable
import org.onebusaway.android.time.WallTime

/**
 * What the rider tracks: a **route row at a stop** — the (stop, route, headsign) triple the arrivals
 * drawer already draws as one row with its strip of upcoming ETAs.
 *
 * This is the row's own identity, not a handle we minted for it, which is what makes tracking the
 * same row from the arrivals drawer and from a starred stop land on one session (the lesson of
 * onebusaway-ios #1215). Note what is *not* in it: route colour, which is null until arrivals load,
 * so including it would let a fast second tap mint a distinct key and slip past the dedupe. Every
 * field here is known the moment the rider long-presses the row.
 */
@Serializable
data class TrackedRouteKey(
    val stopId: String,
    val routeId: String,
    val headsign: String
)

/**
 * One route row the rider asked to be kept informed about, rendered as a live countdown by
 * [TripTrackingService].
 *
 * Deliberately holds no trip instance. An earlier design pinned one vehicle, which meant the card
 * retired the moment that bus pulled away — exactly when a rider who has just missed it most wants
 * to know when the next one comes. Tracking the row lets the countdown roll onto the following
 * departure, and makes the notification a live copy of the row rather than of one pill in it.
 *
 * @param routeName the route's display name, resolved when tracking starts (the notification title)
 * @param stopName the stop's display name, resolved when tracking starts
 * @param stopLat the stop's latitude, and [stopLon] its longitude — carried so tapping the card can
 *        focus the map on the stop, which needs a location and not just an id. Resolved when tracking
 *        starts because that is when the arrivals response holding it is already in hand.
 * @param startedAtMs when the rider started this session, on the device wall clock. Stamped by
 *        [TrackedRouteStore.track] rather than by whoever built the route, because a My Lists badge is
 *        rebuilt on every arrivals poll and would otherwise re-date a session it merely re-rendered.
 *        Persisted, and wall-clock rather than monotonic, because its whole job is to survive the
 *        process dying: it is what stops a session the rider forgot about a week ago from coming back
 *        to life the next time the app opens. A row stored before this field existed decodes to 0 and
 *        so reads as long expired, which is the right way for those to go.
 */
@Serializable
data class TrackedRoute(
    val key: TrackedRouteKey,
    val routeName: String,
    val stopName: String,
    val stopLat: Double,
    val stopLon: Double,
    val startedAtMs: Long = 0
) {
    init {
        require(key.stopId.isNotBlank()) { "A tracked route must name a stop" }
        require(key.routeId.isNotBlank()) { "A tracked route must name a route" }
    }

    /** [startedAtMs] in its own domain; minted here rather than at each comparison. */
    val startedAt: WallTime get() = WallTime(startedAtMs)

    /**
     * A stable scalar identity for the row, for the places that cannot carry the triple — notably
     * the notification id, which has to survive re-renders, service restarts, and reordering. The
     * separator cannot occur in an OBA id, so the parts can never run together.
     */
    val id: String get() = "${key.stopId}|${key.routeId}|${key.headsign}"
}

/**
 * How many rows may be tracked at once. A bound, not a capacity estimate: every tracked row is a
 * permanent card in the shade, and past a handful they stop being glanceable and start being a wall.
 * The oldest session drops off when a new one pushes past this.
 */
const val MAX_TRACKED_ROUTES = 3

/**
 * [route] added to this list as the **most recently tracked** — first — replacing any existing
 * session with the same [TrackedRouteKey] rather than appending a second one.
 *
 * The ordering is the whole mechanism behind "most-recently-tracked wins the prominent slot"
 * (onebusaway-ios #1243): first place is the promoted slot, and re-tracking a live session moves it
 * back to the front rather than no-oping. Position *is* the recency record, so there is no timestamp
 * to keep — and therefore no clock reading resting in the persisted model.
 */
fun List<TrackedRoute>.withTracked(route: TrackedRoute): List<TrackedRoute> = (listOf(route) + filterNot { it.key == route.key }).take(MAX_TRACKED_ROUTES)

/**
 * True when this list is one [withTracked] could have produced: no key tracked twice, and no more
 * rows than [MAX_TRACKED_ROUTES].
 *
 * Lives beside the rule it mirrors, because the only caller is the decoder: a stored list is the one
 * way a list reaches the store without having been built by [withTracked], and one that breaks these
 * invariants would draw a duplicate card or blow past the cap.
 */
fun List<TrackedRoute>.holdsTrackedInvariants(): Boolean = size <= MAX_TRACKED_ROUTES && distinctBy { it.key }.size == size

/** This list without the session identified by [key] (the "stop tracking" action). */
fun List<TrackedRoute>.withoutKey(key: TrackedRouteKey): List<TrackedRoute> = filterNot { it.key == key }
