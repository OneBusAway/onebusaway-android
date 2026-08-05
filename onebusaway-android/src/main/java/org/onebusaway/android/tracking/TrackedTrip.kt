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

/**
 * The **content identity** of a tracked trip: the (stop, route, headsign) the rider pointed at.
 *
 * Deliberately not a handle we minted at Track time. onebusaway-ios shipped tracking keyed on a
 * freshly-created Live Activity id and had to fix it (their #1215): tracking the same bus from a
 * bookmark and from the stop page produced two independent activities, two Lock Screen cards, and
 * two registrations, because nothing tied the two taps together. Keying on what the rider named
 * makes the second tap land on the first tap's session by construction, from any surface.
 *
 * Note what is *not* in the key: the route colour. It is null until arrivals load, so including it
 * would let a fast second tap — before the colour resolved — mint a distinct key and slip through
 * the dedupe. Every field here is known at the moment the rider taps Track.
 */
@Serializable
data class TrackedTripKey(
    val stopId: String,
    val routeId: String,
    val headsign: String
)

/**
 * One bus the rider asked to be kept informed about, pinned to the notification shade as a live
 * countdown by [TripTrackingService].
 *
 * [key] is the session's identity ([TrackedTripKey]); [tripId] + [serviceDate] name the exact trip
 * *instance* the countdown follows. The two are different jobs: the key decides whether a Track tap
 * starts a new session or lands on an existing one, while the instance decides which row of the
 * arrivals response the countdown reads. Re-tracking the same key with a different instance (the
 * rider changed their mind and picked the 4:52 instead of the 4:38) replaces the instance and
 * promotes the session, rather than opening a second card for the same bus.
 *
 * @param routeName the route's display name, resolved at Track time (the notification title)
 * @param stopName the stop's display name, resolved at Track time
 * @param plannedWaitSeconds the rider's wait when tracking began — the span of the progress bar, so
 *        a delay that pushes the arrival out visibly walks the tracker backwards instead of
 *        silently rescaling the bar under it. A duration, never a clock reading.
 */
@Serializable
data class TrackedTrip(
    val key: TrackedTripKey,
    val tripId: String,
    val serviceDate: Long,
    val routeName: String,
    val stopName: String,
    val plannedWaitSeconds: Int
) {
    init {
        require(tripId.isNotBlank()) { "A tracked trip must name a trip instance" }
        require(key.stopId.isNotBlank()) { "A tracked trip must name a stop" }
    }

    /** This exact instance's id — what an arrivals row is matched against. See [trackedInstanceId]. */
    val instanceId: String get() = trackedInstanceId(key.stopId, tripId, serviceDate)
}

/**
 * The identity of one trip instance *at one stop*: the triple the arrivals list itself guarantees is
 * unique across a response (`StopArrivals.arrivals` collapses duplicate trip instances, and the ETA
 * strip keys its pills on the same triple). The stop is part of it because the same bus tracked at
 * two different stops is two different countdowns.
 *
 * Used two ways: the service resolves a tracked trip against a fresh response with it, and the
 * arrivals UI decides whether a given ETA pill is the one being tracked — so the menu offers "stop
 * tracking" on the pill the rider actually tracked, and plain "track" on its siblings (which replace
 * it, per [TrackedTripKey]).
 */
fun trackedInstanceId(stopId: String, tripId: String, serviceDate: Long): String = "$stopId|$tripId|$serviceDate"

/**
 * How many trips may be tracked at once. A bound, not a capacity estimate: every tracked trip is a
 * permanent card in the shade, and past a handful they stop being glanceable and start being a
 * wall. The oldest session drops off when a new one pushes past this.
 */
const val MAX_TRACKED_TRIPS = 3

/**
 * [trip] added to this list as the **most recently tracked** — first — replacing any existing
 * session with the same [TrackedTrip.key] rather than appending a second one.
 *
 * The ordering is the whole mechanism behind "most-recently-tracked wins the prominent slot"
 * (onebusaway-ios #1243): first place is the promoted slot, and re-tracking a live session moves it
 * back to the front rather than no-oping. Position *is* the recency record, so there is no
 * timestamp to keep — and therefore no clock reading resting in the persisted model.
 */
fun List<TrackedTrip>.withTracked(trip: TrackedTrip): List<TrackedTrip> = (listOf(trip) + filterNot { it.key == trip.key }).take(MAX_TRACKED_TRIPS)

/** This list without the session identified by [key] (the "stop tracking" action). */
fun List<TrackedTrip>.withoutKey(key: TrackedTripKey): List<TrackedTrip> = filterNot { it.key == key }
