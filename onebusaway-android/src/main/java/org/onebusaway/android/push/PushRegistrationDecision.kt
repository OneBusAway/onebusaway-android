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
import org.onebusaway.android.region.Region

/**
 * A single push registration on record with OBACloud: the region it targets, the FCM token, and the
 * metadata sent alongside it. All properties participate in equality, so a change to *any* of them (a
 * rotated token, a moved region, a new device locale, a renamed or cleared test-device label) is a
 * change that must be pushed to the server. Pure data — no Android dependencies — so
 * [decidePushRegistration] is JVM-unit-testable.
 */
data class PushRegistration(
    val regionId: Long,
    val sidecarBaseUrl: String,
    val token: String,
    val locale: String,
    /**
     * The admin-facing label sent alongside a test-device registration (null for an ordinary one). It
     * participates in equality so that renaming the device re-POSTs — and because it can only change
     * when the rider edits the setting, it never churns on its own.
     */
    val description: String?
) {

    /**
     * Whether this registers as a test device. Derived rather than stored: the server requires a
     * `description` exactly when `test_device=true` (422 otherwise), so carrying the flag separately
     * would admit a pair — flag set, no name — that can only ever be rejected. Deriving it makes that
     * rule hold by construction, at the type level, instead of by agreement between call sites.
     */
    val testDevice: Boolean get() = description != null
}

/**
 * Re-POST an otherwise-unchanged registration this often, so the server's `last_seen_at` is refreshed
 * and its 180-day prune never silently drops this device (issue #1957's "freshness" goal). Matches the
 * iOS client's `PushRegistrationManager.refreshInterval`, which keeps a healthy device at roughly one
 * POST per day — comfortably inside the endpoint's 30 req/min/IP budget.
 */
val PUSH_REFRESH_INTERVAL: Duration = 24.hours

/**
 * The server's cap on a test device's `description` (OBACloud's push-notifications documentation:
 * "Free text ≤255 chars identifying the device to admins"). Enforced client-side so a long name is
 * truncated rather than POSTed and rejected — a `description` that violates this is a 422, and since a
 * failed registration persists nothing, the doomed request would otherwise repeat on every foreground.
 */
const val PUSH_DESCRIPTION_MAX_LENGTH = 255

/**
 * True when [this] and [other] address the same server row — the region, host, and token a DELETE (or an
 * upsert POST) targets. Locale and test-device aren't part of the row's identity, so they don't count.
 */
fun PushRegistration.sameEndpoint(other: PushRegistration): Boolean = regionId == other.regionId &&
    sidecarBaseUrl == other.sidecarBaseUrl &&
    token == other.token

/**
 * The registration this device should have, derived from the current inputs. Three-valued, not a
 * nullable [PushRegistration], because "no registration" genuinely means two different things here and
 * conflating them under one `null` is exactly how a stale registration leaks: an *unresolved* answer
 * must freeze reconciliation (deciding on a half-read state fires spurious DELETEs), while a *resolved*
 * "none" must drive it (a registration on record that shouldn't exist has to be unregistered). The
 * split is enforced at the type level — [decidePushRegistration] accepts only [Resolved], so the only
 * way to skip reconciling is an explicit match on [Unresolved].
 */
sealed interface DesiredRegistration {

    /**
     * An input that resolves asynchronously (the region flow, the FCM token) hasn't settled yet, so
     * nothing can be decided — a later trigger re-runs the reconcile once it has. Never an opt-out:
     * this state cannot reach [decidePushRegistration], so it can never DELETE anything.
     */
    data object Unresolved : DesiredRegistration

    /** Every input has settled; only these states may be reconciled against the record. */
    sealed interface Resolved : DesiredRegistration

    /**
     * Definitively, no registration should exist: the rider opted out, or the current region has no
     * sidecar host to register with. Reconciling this *unregisters* whatever is on record — a rider who
     * moves from a sidecar region to a sidecar-less one must stop receiving the old region's alerts.
     */
    data object None : Resolved

    /** Definitively, [target] should be registered. */
    data class Wanted(val target: PushRegistration) : Resolved
}

/**
 * The pure derivation of [DesiredRegistration] from the raw inputs (issue #1957). Every early exit
 * names which of the three answers it is — there is no `null` through which "this region has no
 * sidecar" (a resolved fact, [DesiredRegistration.None]) can masquerade as "the region hasn't loaded
 * yet" (an unresolved one) — see [DesiredRegistration] for why that distinction is load-bearing.
 * Notifications off at the OS level is the **only opt-out signal** (there is no in-app toggle), and it
 * is definitive regardless of every other input.
 *
 * The test-device [description][PushRegistration.description] is honoured only once the rider has
 * named the device: the server rejects a `test_device=true` registration with a blank `description`
 * (422), so an unnamed device registers as an ordinary one rather than POSTing a request guaranteed to
 * fail. The iOS client gates its "Test Device Name" the same way — and [PushRegistration.testDevice]
 * derives the flag from the name, so that rule holds by construction. The name is capped when it is
 * written (`AdvancedSettingsViewModel.onPushTestDeviceNameChanged`); the [PUSH_DESCRIPTION_MAX_LENGTH]
 * clamp here is a cheap guard on the wire boundary itself, so no path can put an over-long
 * `description` on a request the server would reject outright.
 *
 * @param region null while the region flow hasn't resolved yet (it is briefly null at cold start —
 *   see `RegionRepository`).
 * @param token the FCM token, or `""` while one hasn't been obtained yet (`userPushId()`'s contract).
 * @param locale the device's BCP-47 tag, sent as-is with no normalization.
 */
fun deriveDesiredRegistration(
    notificationsEnabled: Boolean,
    region: Region?,
    token: String,
    locale: String,
    testDeviceEnabled: Boolean,
    testDeviceName: String?
): DesiredRegistration {
    if (!notificationsEnabled) return DesiredRegistration.None
    if (region == null) return DesiredRegistration.Unresolved
    val base = region.sidecarBaseUrl?.takeIf { it.isNotBlank() } ?: return DesiredRegistration.None
    if (token.isEmpty()) return DesiredRegistration.Unresolved
    val description = testDeviceName
        ?.takeIf { testDeviceEnabled }
        ?.trim()
        ?.take(PUSH_DESCRIPTION_MAX_LENGTH)
        ?.takeIf { it.isNotEmpty() }
    return DesiredRegistration.Wanted(
        PushRegistration(
            regionId = region.id,
            sidecarBaseUrl = base,
            token = token,
            locale = locale,
            description = description
        )
    )
}

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
 * The pure reconciliation decision. [desired] is the registration the device should have — typed as
 * [DesiredRegistration.Resolved], so an unresolved input state cannot reach this function and be
 * mistaken for either an opt-out or a wanted registration. [last] is the registration last
 * successfully sent to the server, or null if none. [sinceLastSent] is how long ago [last] was sent,
 * or null when that is unknown (no record, or a record written by a build that predates the
 * timestamp) — unknown counts as stale, since a redundant upsert is harmless and losing the device to
 * the 180-day prune is not.
 *
 * Keeping this a pure function (no clock, no I/O, no Android — the caller supplies the elapsed time)
 * is deliberate: it is the one place the register/refresh/unregister rules live, and it is
 * exhaustively unit-tested.
 */
fun decidePushRegistration(
    desired: DesiredRegistration.Resolved,
    last: PushRegistration?,
    sinceLastSent: Duration?
): PushRegistrationAction = when (desired) {
    DesiredRegistration.None ->
        if (last == null) PushRegistrationAction.NoOp else PushRegistrationAction.Unregister(last)
    is DesiredRegistration.Wanted -> {
        val target = desired.target
        when {
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
    }
}
