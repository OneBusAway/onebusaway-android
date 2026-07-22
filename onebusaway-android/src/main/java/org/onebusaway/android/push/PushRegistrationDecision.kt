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
 * "Free text ≤255 chars identifying the device to admins"). Enforced client-side — via
 * [truncatedToDescriptionCap] — so a long name is truncated rather than POSTed and rejected: a
 * `description` that violates this is a 422, and since a failed registration persists nothing, the
 * doomed request would otherwise repeat on every foreground.
 */
const val PUSH_DESCRIPTION_MAX_LENGTH = 255

/**
 * Truncates to at most [PUSH_DESCRIPTION_MAX_LENGTH] UTF-16 units without ever splitting a surrogate
 * pair — a bare `take()` counts code units and can cut between a pair's halves, sending a lone
 * surrogate (mangled to `?`/U+FFFD by the form encoding) as the final character. When the cut would
 * land mid-pair, the pair is dropped whole.
 *
 * The budget deliberately stays in UTF-16 units: it is the strictest of the plausible readings of the
 * server's documented "≤255 chars" (the OBACloud source is private, so its exact counting can't be
 * read) — a string of at most N UTF-16 units also has at most N code points, so the result is legal
 * under either interpretation. Grapheme clusters (ZWJ emoji sequences, combining marks) can still be
 * cut between code points; that renders imperfectly but is valid Unicode and within the server limit.
 */
fun String.truncatedToDescriptionCap(): String = if (length <= PUSH_DESCRIPTION_MAX_LENGTH) {
    this
} else {
    take(PUSH_DESCRIPTION_MAX_LENGTH).dropLastWhile { it.isHighSurrogate() }
}

/**
 * True when [this] and [other] address the same server row — the region, host, and token a DELETE (or an
 * upsert POST) targets. Locale and test-device aren't part of the row's identity, so they don't count.
 */
fun PushRegistration.sameEndpoint(other: PushRegistration): Boolean = regionId == other.regionId &&
    sidecarBaseUrl == other.sidecarBaseUrl &&
    token == other.token

/**
 * The registration this device should have, derived from the current inputs. Four-valued, not a
 * nullable [PushRegistration], because "no registration" genuinely means three different things here
 * and conflating them is exactly how a registration bug leaks: an *unresolved* answer must freeze
 * reconciliation (deciding on a half-read state fires spurious DELETEs), an [OptedOut] answer must
 * leave the server row alone (issue #1957: an OS-level disable needs no DELETE), and only a
 * [NoSidecar] answer actively unregisters what is on record. The splits are enforced at the type
 * level — [decidePushRegistration] accepts only [Resolved], so the only way to skip reconciling is an
 * explicit match on [Unresolved].
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
     * The rider has notifications off at the OS level — nothing should be POSTed, and the server row
     * (if any) is deliberately left in place. Issue #1957: an OS-level disable "doesn't need a DELETE —
     * FCM bounces feed back to the server and it cleans up unregistered tokens itself"; DELETE is
     * reserved for an *in-app* opt-out, which this app doesn't have. The iOS client behaves the same
     * way (its `PushRegistrationManager` never DELETEs). Deleting here would also risk dropping the
     * delivery target of an already-scheduled trip alarm the moment the rider toggles notifications
     * off, if the server couples alarm delivery to the registration row.
     */
    data object OptedOut : Resolved

    /**
     * The current region has no sidecar host to register with, so no registration should exist here —
     * and one on record from a *previous* region must be unregistered, or the rider who moved keeps
     * receiving the old region's alerts until the server's 180-day prune (the iOS docs sanction exactly
     * this old-row DELETE on a region change).
     */
    data object NoSidecar : Resolved

    /** Definitively, [target] should be registered. */
    data class Wanted(val target: PushRegistration) : Resolved
}

/**
 * The pure derivation of [DesiredRegistration] from the raw inputs (issue #1957). Every early exit
 * names which of the four answers it is — there is no `null` through which "this region has no
 * sidecar" (a resolved fact, [DesiredRegistration.NoSidecar]) can masquerade as "the region hasn't
 * loaded yet" (an unresolved one) — see [DesiredRegistration] for why that distinction is load-bearing.
 * Notifications off at the OS level is the **only opt-out signal** (there is no in-app toggle), it is
 * definitive regardless of every other input, and it deliberately does not unregister — see
 * [DesiredRegistration.OptedOut].
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
    if (!notificationsEnabled) return DesiredRegistration.OptedOut
    if (region == null) return DesiredRegistration.Unresolved
    val base = region.sidecarBaseUrl?.takeIf { it.isNotBlank() } ?: return DesiredRegistration.NoSidecar
    if (token.isEmpty()) return DesiredRegistration.Unresolved
    val description = testDeviceName
        ?.takeIf { testDeviceEnabled }
        ?.trim()
        ?.truncatedToDescriptionCap()
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

    /**
     * DELETE [previous]: its destination no longer applies (the rider's region has no sidecar). Never
     * produced for an OS-level opt-out — see [DesiredRegistration.OptedOut].
     */
    data class Unregister(val previous: PushRegistration) : PushRegistrationAction

    /**
     * The token or the region changed while still enabled: DELETE the stale [previous] registration
     * (so the old region/token stops receiving) and POST the new [target]. The DELETE is
     * **best-effort by design**: a miss is dropped, and the old row lingers until the server's
     * 180-day prune — the iOS client's baseline for *every* region change (it never DELETEs, and
     * issue #1957 marks the old-row DELETE as optional). A persisted retry queue was deliberately
     * rejected as disproportionate to that corner (PR #1958 review).
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
    // The server row is left alone on an OS-level opt-out (issue #1957; iOS parity) — the record is
    // kept too, so re-enabling reads as "unchanged" rather than a fresh registration.
    DesiredRegistration.OptedOut -> PushRegistrationAction.NoOp
    DesiredRegistration.NoSidecar ->
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
