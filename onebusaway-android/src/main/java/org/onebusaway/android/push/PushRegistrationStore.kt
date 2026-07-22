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
 * since the push_registrations API offers no GET: the registration last successfully sent ([last])
 * plus when it was sent ([sinceLastSent]). Needed to address the DELETE of a stale row when the
 * endpoint changes or the region loses its sidecar (by which point the current token/region may
 * already have changed), to skip redundant POSTs against a 30 req/min/IP endpoint, and to drive the
 * [PUSH_REFRESH_INTERVAL] keep-alive.
 *
 * The record spans several preference slots, with the token as the **presence marker**: a read
 * returns nothing unless it is present, and clearing the record nulls it. Every multi-slot write goes
 * through [PreferencesRepository.edit], which commits all of the record's slots atomically — in
 * process and on disk (#1978) — so the record persists whole or not at all across a process death,
 * and a record whose marker is present but whose addressing fields are missing cannot exist.
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
     * How long ago [last] was sent, or null if unknown — no record, one written before this slot
     * existed, or a device wall clock that has since been set backwards (the elapsed time would read
     * negative, which would silence the [PUSH_REFRESH_INTERVAL] keep-alive until the clock caught back
     * up — for a large jump, long enough for the server's 180-day prune to drop the device). Unknown
     * counts as stale, so the caller re-POSTs and [record] restamps at the new clock, healing the jump.
     *
     * Purely local elapsed time measured against our own write ([WallTime] on both ends), never against
     * a server timestamp, so no server-clock crossing is involved.
     */
    fun sinceLastSent(): Duration? {
        val sentAtMs = prefs.getLong(KEY_SENT_AT, 0L).takeIf { it > 0L } ?: return null
        return (now() - WallTime(sentAtMs)).takeIf { !it.isNegative() }
    }

    /** Commits [registration] as the live record and stamps the refresh clock. */
    fun record(registration: PushRegistration) {
        prefs.edit {
            setLong(KEY_REGION_ID, registration.regionId)
            setString(KEY_BASE, registration.sidecarBaseUrl)
            setString(KEY_LOCALE, registration.locale)
            setString(KEY_DESCRIPTION, registration.description)
            setLong(KEY_SENT_AT, now().epochMs)
            setString(KEY_TOKEN, registration.token)
        }
    }

    /** Forgets the live record, so [last] reports nothing on record. */
    fun clear() {
        prefs.edit {
            setString(KEY_TOKEN, null)
            // Dropped with the record it stamped: sinceLastSent() must not report an elapsed time for a
            // registration last() says doesn't exist.
            setLong(KEY_SENT_AT, 0L)
        }
    }

    private companion object {
        // Internal bookkeeping slots (not user-facing) for the last successful registration.
        const val KEY_REGION_ID = "push_reg_region_id"
        const val KEY_BASE = "push_reg_base"
        const val KEY_TOKEN = "push_reg_token"
        const val KEY_LOCALE = "push_reg_locale"
        const val KEY_DESCRIPTION = "push_reg_description"
        const val KEY_SENT_AT = "push_reg_sent_at"
    }
}
