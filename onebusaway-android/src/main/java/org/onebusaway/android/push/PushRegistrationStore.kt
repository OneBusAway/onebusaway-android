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
package org.onebusaway.android.push

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.time.WallTime

/**
 * The durable memory of what this device has told OBACloud (issue #1957) — the only record of it,
 * since the push_registrations API offers no GET. Two records live here:
 *
 * - **The live registration** ([last]) plus when it was sent ([sinceLastSent]). Needed to address the
 *   DELETE on opt-out (by which point the current token/region may already have changed), to skip
 *   redundant POSTs against a 30 req/min/IP endpoint, and to drive the [PUSH_REFRESH_INTERVAL]
 *   keep-alive.
 * - **A pending delete** ([pendingDelete]) — a registration whose DELETE is still owed. See
 *   [queuePendingDelete].
 *
 * Each record spans several preference slots, so one of them — the token — acts as a **commit
 * sentinel**: writes store it last and clears null it, and a read returns nothing unless it is present.
 * A half-written record therefore reads back as "no record" rather than as a corrupt one.
 *
 * That ordering is exact for in-process reads (the prefs cache updates synchronously, in call order)
 * but each slot persists to disk as its own async DataStore edit, so it is NOT guaranteed across a
 * process death mid-persist. Acceptable by design: every torn state lands on a safe path — a missing
 * sentinel reads as "no record" and re-registration is an idempotent upsert, while a stale record's
 * DELETE is answered with a tolerated 404. No recovery path depends on the sentinel being durably last.
 */
@Singleton
class PushRegistrationStore internal constructor(
    private val prefs: PreferencesRepository,
    // Device clock, injectable so the refresh window is drivable from a JVM test.
    private val now: () -> WallTime
) {

    @Inject
    constructor(prefs: PreferencesRepository) : this(prefs, WallTime::now)

    /** The registration last successfully sent to the server, or null if none is on record. */
    fun last(): PushRegistration? {
        val base = prefs.getString(KEY_BASE, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return PushRegistration(
            regionId = prefs.getLong(KEY_REGION_ID, -1L),
            sidecarBaseUrl = base,
            token = token,
            locale = prefs.getString(KEY_LOCALE, null) ?: "",
            description = prefs.getString(KEY_DESCRIPTION, null)
        )
    }

    /**
     * How long ago [last] was sent, or null if unknown — no record, or one written before this slot
     * existed. Purely local elapsed time measured against our own write ([WallTime] on both ends), never
     * against a server timestamp, so no server-clock crossing is involved.
     */
    fun sinceLastSent(): Duration? {
        val sentAtMs = prefs.getLong(KEY_SENT_AT, 0L).takeIf { it > 0L } ?: return null
        return now() - WallTime(sentAtMs)
    }

    /** Commits [registration] as the live record and stamps the refresh clock. */
    fun record(registration: PushRegistration) {
        prefs.setLong(KEY_REGION_ID, registration.regionId)
        prefs.setString(KEY_BASE, registration.sidecarBaseUrl)
        prefs.setString(KEY_LOCALE, registration.locale)
        prefs.setString(KEY_DESCRIPTION, registration.description)
        prefs.setLong(KEY_SENT_AT, now().epochMs)
        // The commit sentinel, written last.
        prefs.setString(KEY_TOKEN, registration.token)
        // This endpoint is live again, so drop any DELETE still owed for it — otherwise returning to an
        // old endpoint (region/token switched away, then back) would delete the row we just POSTed.
        // This is what makes a single pending-delete slot safe.
        if (pendingDelete()?.sameEndpoint(registration) == true) clearPendingDelete()
    }

    /** Forgets the live record, so [last] reports nothing on record. */
    fun clear() {
        prefs.setString(KEY_TOKEN, null)
    }

    /**
     * Remembers that [registration] still needs deleting server-side. Written when a re-registration's
     * POST lands but its DELETE doesn't: the new registration must be recorded, yet the old row is still
     * on the server pointing at a token this device still holds, so without this it would keep pushing
     * until the 180-day prune.
     *
     * Only the DELETE-addressing fields (region + host + token) are kept; locale and the test-device
     * fields don't affect a DELETE. There is deliberately one slot — a second orphan before the first
     * drains overwrites it, which takes two region changes with both old hosts unreachable back to back,
     * and the loser still falls back to the server prune.
     */
    fun queuePendingDelete(registration: PushRegistration) {
        prefs.setLong(KEY_PENDING_DEL_REGION_ID, registration.regionId)
        prefs.setString(KEY_PENDING_DEL_BASE, registration.sidecarBaseUrl)
        // The commit sentinel, written last — mirroring [record].
        prefs.setString(KEY_PENDING_DEL_TOKEN, registration.token)
    }

    /** The registration awaiting a retried DELETE, or null if none is queued. */
    fun pendingDelete(): PushRegistration? {
        val base = prefs.getString(KEY_PENDING_DEL_BASE, null) ?: return null
        val token = prefs.getString(KEY_PENDING_DEL_TOKEN, null) ?: return null
        return PushRegistration(
            regionId = prefs.getLong(KEY_PENDING_DEL_REGION_ID, -1L),
            sidecarBaseUrl = base,
            token = token,
            // Unused when deleting — a DELETE is addressed by region + host + token only.
            locale = "",
            description = null
        )
    }

    fun clearPendingDelete() {
        prefs.setString(KEY_PENDING_DEL_TOKEN, null)
    }

    private companion object {
        // Internal bookkeeping slots (not user-facing) for the last successful registration.
        const val KEY_REGION_ID = "push_reg_region_id"
        const val KEY_BASE = "push_reg_base"
        const val KEY_TOKEN = "push_reg_token"
        const val KEY_LOCALE = "push_reg_locale"
        const val KEY_DESCRIPTION = "push_reg_description"
        const val KEY_SENT_AT = "push_reg_sent_at"

        // Slots for a registration whose DELETE is owed but not yet confirmed (see [queuePendingDelete]).
        const val KEY_PENDING_DEL_REGION_ID = "push_pending_del_region_id"
        const val KEY_PENDING_DEL_BASE = "push_pending_del_base"
        const val KEY_PENDING_DEL_TOKEN = "push_pending_del_token"
    }
}
