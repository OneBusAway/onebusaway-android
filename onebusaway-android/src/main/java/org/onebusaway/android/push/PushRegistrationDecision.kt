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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * A single push registration on record with OBACloud: the region it targets, the FCM token, and the
 * metadata sent alongside it. All fields participate in equality, so a change to *any* of them (a
 * rotated token, a moved region, a new device locale, a flipped test flag) is a change that must be
 * pushed to the server. Pure data — no Android dependencies — so [decidePushRegistration] is
 * JVM-unit-testable.
 */
data class PushRegistration(
    val regionId: Long,
    val sidecarBaseUrl: String,
    val token: String,
    val locale: String,
    val testDevice: Boolean,
    /**
     * The admin-facing label sent alongside a test-device registration (null for an ordinary one). It
     * participates in equality so that renaming the device re-POSTs — and because it can only change
     * when the rider edits the setting, it never churns on its own.
     */
    val description: String?
)

/**
 * Re-POST an otherwise-unchanged registration this often, so the server's `last_seen_at` is refreshed
 * and its 180-day prune never silently drops this device (issue #1957's "freshness" goal). Matches the
 * iOS client's `PushRegistrationManager.refreshInterval`, which keeps a healthy device at roughly one
 * POST per day — comfortably inside the endpoint's 30 req/min/IP budget.
 */
val PUSH_REFRESH_INTERVAL: Duration = 24.hours

/**
 * True when [this] and [other] address the same server row — the region, host, and token a DELETE (or an
 * upsert POST) targets. Locale and test-device aren't part of the row's identity, so they don't count.
 */
fun PushRegistration.sameEndpoint(other: PushRegistration): Boolean = regionId == other.regionId &&
    sidecarBaseUrl == other.sidecarBaseUrl &&
    token == other.token

/**
 * What the registrar should do to reconcile the [PushRegistration] currently on record with the one the
 * device now wants (issue #1957).
 */
sealed interface PushRegistrationAction {

    /** On record and desired already match (or neither exists) — no network call. */
    object NoOp : PushRegistrationAction

    /** POST [target]: either the first registration, or a metadata refresh on the same token+region. */
    data class Register(val target: PushRegistration) : PushRegistrationAction

    /** DELETE [previous]: the rider opted out (or the token/region became unavailable). */
    data class Unregister(val previous: PushRegistration) : PushRegistrationAction

    /**
     * The token or the region changed while still enabled: DELETE the stale [previous] registration
     * (so the old region/token stops receiving) and POST the new [target].
     */
    data class Reregister(
        val previous: PushRegistration,
        val target: PushRegistration
    ) : PushRegistrationAction
}

/**
 * The pure reconciliation decision. [target] is the registration the device wants right now, or null
 * when the rider has opted out. [last] is the registration last successfully sent to the server, or
 * null if none. [sinceLastSent] is how long ago [last] was sent, or null when that is unknown (no
 * record, or a record written by a build that predates the timestamp) — unknown counts as stale, since
 * a redundant upsert is harmless and losing the device to the 180-day prune is not.
 *
 * A null [target] means the rider opted out, never "the inputs haven't resolved yet" — see the guard
 * in `PushRegistrationManager.sync`.
 *
 * Keeping this a pure function (no clock, no I/O, no Android — the caller supplies the elapsed time)
 * is deliberate: it is the one place the register/refresh/unregister rules live, and it is
 * exhaustively unit-tested.
 */
fun decidePushRegistration(
    target: PushRegistration?,
    last: PushRegistration?,
    sinceLastSent: Duration?
): PushRegistrationAction = when {
    target == null -> if (last == null) PushRegistrationAction.NoOp else PushRegistrationAction.Unregister(last)
    last == null -> PushRegistrationAction.Register(target)
    // Unchanged, but the server's last_seen_at needs refreshing before the 180-day prune reaches it.
    target == last ->
        if (sinceLastSent == null || sinceLastSent >= PUSH_REFRESH_INTERVAL) {
            PushRegistrationAction.Register(target)
        } else {
            PushRegistrationAction.NoOp
        }
    // Same endpoint, only metadata (locale / test flag / description) changed → a plain re-POST upserts it.
    target.sameEndpoint(last) -> PushRegistrationAction.Register(target)
    // Token or region changed → clean up the stale registration, then register the new one.
    else -> PushRegistrationAction.Reregister(previous = last, target = target)
}
