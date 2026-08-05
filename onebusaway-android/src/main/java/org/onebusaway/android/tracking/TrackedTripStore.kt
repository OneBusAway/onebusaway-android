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

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.onebusaway.android.preferences.PreferencesRepository

private const val TAG = "TrackedTripStore"

/** Encodes and decodes the persisted tracked-trip list. Pure, so the JVM tests drive it directly. */
internal object TrackedTripsJson {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(trips: List<TrackedTrip>): String = json.encodeToString(trips)

    /**
     * The stored list, or null when the payload cannot be read — a payload written by a newer format,
     * or truncated by a crash mid-write. A tracked trip is a live, minutes-long intention, so there is
     * nothing there worth repairing; the store drops it and says so. Reporting it is the store's job,
     * not this object's, so this stays free of `android.util.Log` and testable on the JVM (the shape
     * `ReminderPlanJson` uses for the same reason).
     *
     * Deliberately narrow: [IllegalArgumentException] covers malformed JSON (`SerializationException`
     * extends it) and JSON that decodes but violates [TrackedTrip]'s own `require`s. Anything else is
     * not a bad payload and must not be silenced.
     */
    fun decode(value: String): List<TrackedTrip>? = try {
        json.decodeFromString<List<TrackedTrip>>(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * The set of trips the rider is currently tracking — the single source of truth for the feature.
 *
 * [TripTrackingService] renders it and never owns it, so which trips are tracked survives the
 * service being killed and restarted, and a "stop tracking" tap is a write here rather than a
 * message to a service that may not be running. Order is meaning, not history: first is the
 * most-recently-tracked session and therefore the one promoted to the prominent slot (see
 * [withTracked]).
 *
 * Persisted through [PreferencesRepository] as one JSON slot rather than as a Room table: the list
 * is at most [MAX_TRACKED_TRIPS] rows long, is rewritten whole on every change, and has no queries —
 * a schema migration would buy nothing. Reads are synchronous (the repository keeps a cache in
 * front of DataStore), so the initial load costs no suspension.
 */
@Singleton
class TrackedTripStore @Inject constructor(
    private val preferences: PreferencesRepository
) {

    private val _trips = MutableStateFlow(load())

    /** The tracked trips, most-recently-tracked first. */
    val trips: StateFlow<List<TrackedTrip>> = _trips.asStateFlow()

    /**
     * The exact trip instances being tracked ([TrackedTrip.instanceId]). The arrivals UI overlays
     * this to decide which ETA pill offers "stop tracking" — the same reactive-overlay shape the
     * starred-route set uses, so a Track from any surface re-flags every open list with no re-fetch.
     */
    val trackedInstances: Flow<Set<String>> = trips.map { list -> list.mapTo(mutableSetOf()) { it.instanceId } }

    /** True when [instanceId] is the exact instance currently tracked for its session. */
    fun isTracking(instanceId: String): Boolean = _trips.value.any { it.instanceId == instanceId }

    /**
     * Starts tracking [trip], or — when a session for the same [TrackedTripKey] is already live —
     * repoints it at this instance and promotes it to the prominent slot. Never a silent no-op: a
     * rider who taps Track on a bus they are already tracking is asking for it to be the one they
     * see (onebusaway-ios #1243).
     */
    fun track(trip: TrackedTrip) {
        update { it.withTracked(trip) }
    }

    /** Stops tracking the session identified by [key]. */
    fun untrack(key: TrackedTripKey) {
        update { it.withoutKey(key) }
    }

    /** Stops tracking whichever session is following [instanceId] (the notification's own action). */
    fun untrackInstance(instanceId: String) {
        _trips.value.firstOrNull { it.instanceId == instanceId }?.let { untrack(it.key) }
    }

    /** Stops tracking everything. */
    fun clear() {
        update { emptyList() }
    }

    private fun load(): List<TrackedTrip> {
        val stored = preferences.getString(KEY, null) ?: return emptyList()
        return TrackedTripsJson.decode(stored) ?: run {
            Log.w(TAG, "Discarding unreadable tracked-trip state")
            emptyList()
        }
    }

    private fun update(op: (List<TrackedTrip>) -> List<TrackedTrip>) {
        val next = op(_trips.value)
        if (next == _trips.value) return
        _trips.value = next
        preferences.setString(KEY, if (next.isEmpty()) null else TrackedTripsJson.encode(next))
    }

    private companion object {
        const val KEY = "tracked_trips"
    }
}
