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
import org.onebusaway.android.time.WallTime

private const val TAG = "TrackedRouteStore"

/** Encodes and decodes the persisted tracked-trip list. Pure, so the JVM tests drive it directly. */
internal object TrackedRoutesJson {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(routes: List<TrackedRoute>): String = json.encodeToString(routes)

    /**
     * The stored list, or null when the payload cannot be read — a payload written by a newer format,
     * or truncated by a crash mid-write. A tracked row is a live, minutes-long intention, so there is
     * nothing there worth repairing; the store drops it and says so. Reporting it is the store's job,
     * not this object's, so this stays free of `android.util.Log` and testable on the JVM (the shape
     * `ReminderPlanJson` uses for the same reason).
     *
     * Deliberately narrow: [IllegalArgumentException] covers malformed JSON (`SerializationException`
     * extends it) and JSON that decodes but violates [TrackedRoute]'s own `require`s. Anything else is
     * not a bad payload and must not be silenced.
     */
    fun decode(value: String): List<TrackedRoute>? = try {
        json.decodeFromString<List<TrackedRoute>>(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * The route rows the rider is currently tracking — the single source of truth for the feature.
 *
 * [TripTrackingService] renders it and never owns it, so which rows are tracked survives the service
 * being killed and restarted, and a "stop tracking" tap is a write here rather than a message to a
 * service that may not be running. Order is meaning, not history: first is the most-recently-tracked
 * session and therefore the one promoted to the prominent slot (see [withTracked]).
 *
 * Persisted through [PreferencesRepository] as one JSON slot rather than as a Room table: the list
 * is at most [MAX_TRACKED_ROUTES] rows long, is rewritten whole on every change, and has no queries —
 * a schema migration would buy nothing. Reads are synchronous (the repository keeps a cache in
 * front of DataStore), so the initial load costs no suspension.
 */
@Singleton
class TrackedRouteStore @Inject constructor(
    private val preferences: PreferencesRepository
) {

    private val _routes = MutableStateFlow(load())

    /** The tracked rows, most-recently-tracked first. */
    val routes: StateFlow<List<TrackedRoute>> = _routes.asStateFlow()

    /**
     * The tracked row keys, live. Every surface that draws a route row overlays this to decide
     * whether the row's menu offers "track" or "stop tracking" — the same reactive-overlay shape the
     * starred-route set uses, so a Track from any surface re-flags every open list with no re-fetch.
     */
    val trackedKeys: Flow<Set<TrackedRouteKey>> = routes.map { list -> list.mapTo(mutableSetOf()) { it.key } }

    /** True when [key]'s row is currently tracked. */
    fun isTracking(key: TrackedRouteKey): Boolean = _routes.value.any { it.key == key }

    /**
     * Starts tracking [route] as of [now], or — when its row is already tracked — promotes that
     * session to the prominent slot and re-dates it. Never a silent no-op: a rider who taps Track on a
     * row they are already tracking is asking for it to be the one they see (onebusaway-ios #1243),
     * and asking again is asking for the full session again, not the tail of an old one.
     *
     * The stamp is applied here rather than trusted from [route] because callers build a
     * [TrackedRoute] to *describe* a row — a My Lists badge rebuilds one on every arrivals poll — and
     * only this call means "the rider started watching it".
     */
    fun track(route: TrackedRoute, now: WallTime) {
        update { it.withTracked(route.copy(startedAtMs = now.epochMs)) }
    }

    /** Stops tracking the session identified by [key]. */
    fun untrack(key: TrackedRouteKey) {
        update { it.withoutKey(key) }
    }

    /** Stops tracking whichever session the notification with this row [id] belongs to. */
    fun untrackById(id: String) {
        _routes.value.firstOrNull { it.id == id }?.let { untrack(it.key) }
    }

    /** Stops tracking everything. */
    fun clear() {
        update { emptyList() }
    }

    private fun load(): List<TrackedRoute> {
        val stored = preferences.getString(KEY, null) ?: return emptyList()
        return TrackedRoutesJson.decode(stored) ?: run {
            Log.w(TAG, "Discarding unreadable tracked-route state")
            emptyList()
        }
    }

    private fun update(op: (List<TrackedRoute>) -> List<TrackedRoute>) {
        val next = op(_routes.value)
        if (next == _routes.value) return
        _routes.value = next
        preferences.setString(KEY, if (next.isEmpty()) null else TrackedRoutesJson.encode(next))
    }

    private companion object {
        const val KEY = "tracked_routes"
    }
}
