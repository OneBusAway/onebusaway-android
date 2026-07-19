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
)

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
        val target: PushRegistration,
    ) : PushRegistrationAction
}

/**
 * The pure reconciliation decision. [target] is the registration the device wants right now — non-null
 * only when notifications are enabled and a region, sidecar URL, and FCM token are all available;
 * null otherwise. [last] is the registration last successfully sent to the server, or null if none.
 *
 * Keeping this a pure function (no clock, no I/O, no Android) is deliberate: it is the one place the
 * register/refresh/unregister rules live, and it is exhaustively unit-tested.
 */
fun decidePushRegistration(
    target: PushRegistration?,
    last: PushRegistration?,
): PushRegistrationAction = when {
    target == null -> if (last == null) PushRegistrationAction.NoOp else PushRegistrationAction.Unregister(last)
    last == null -> PushRegistrationAction.Register(target)
    target == last -> PushRegistrationAction.NoOp
    // Same endpoint + token, only locale/test flag changed → a plain re-POST upserts it.
    target.token == last.token &&
        target.regionId == last.regionId &&
        target.sidecarBaseUrl == last.sidecarBaseUrl -> PushRegistrationAction.Register(target)
    // Token or region changed → clean up the stale registration, then register the new one.
    else -> PushRegistrationAction.Reregister(previous = last, target = target)
}
