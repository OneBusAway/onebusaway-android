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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.push.DesiredRegistration.NoSidecar
import org.onebusaway.android.push.DesiredRegistration.OptedOut
import org.onebusaway.android.push.DesiredRegistration.Wanted

/**
 * Unit tests for [decidePushRegistration] — the pure register/refresh/unregister reconciliation for OBACloud push
 * registration (#1957). Covers each transition between the resolved desired state ([DesiredRegistration.Resolved])
 * and the registration last sent to the server, plus the [PUSH_REFRESH_INTERVAL] keep-alive.
 */
class PushRegistrationDecisionTest {

    private val base = PushRegistration(
        regionId = 1L,
        sidecarBaseUrl = "https://sidecar.example.org",
        token = "token-a",
        locale = "en-US",
        description = null
    )

    /** Comfortably inside [PUSH_REFRESH_INTERVAL], so the keep-alive never confounds these cases. */
    private val fresh: Duration = 1.hours

    private fun decide(
        desired: DesiredRegistration.Resolved,
        last: PushRegistration?,
        sinceLastSent: Duration? = fresh
    ) = decidePushRegistration(desired, last, sinceLastSent)

    @Test
    fun `nothing desired and nothing on record is a no-op`() {
        assertEquals(PushRegistrationAction.NoOp, decide(OptedOut, last = null))
        assertEquals(PushRegistrationAction.NoOp, decide(NoSidecar, last = null))
    }

    @Test
    fun `first registration when nothing is on record`() {
        assertEquals(PushRegistrationAction.Register(base), decide(Wanted(base), last = null))
    }

    @Test
    fun `opting out leaves the recorded registration and its server row alone`() {
        // An OS-level disable never DELETEs — see [DesiredRegistration.OptedOut] for the rationale.
        assertEquals(PushRegistrationAction.NoOp, decide(OptedOut, last = base))
        // Stale record while opted out changes nothing: no keep-alive POST either.
        assertEquals(PushRegistrationAction.NoOp, decide(OptedOut, last = base, sinceLastSent = 30.days))
    }

    @Test
    fun `losing the sidecar unregisters the recorded registration`() {
        // Unlike an opt-out, a rider who moved to a sidecar-less region must stop receiving the old
        // region's alerts — the sanctioned old-row DELETE on a region change.
        assertEquals(PushRegistrationAction.Unregister(base), decide(NoSidecar, last = base))
    }

    @Test
    fun `unchanged registration is a no-op`() {
        assertEquals(PushRegistrationAction.NoOp, decide(Wanted(base), last = base))
    }

    @Test
    fun `a locale change re-posts on the same token and region`() {
        val target = base.copy(locale = "es-MX")
        assertEquals(PushRegistrationAction.Register(target), decide(Wanted(target), last = base))
    }

    @Test
    fun `a test-device flag change re-posts on the same token and region`() {
        // Naming the device is what promotes it: testDevice derives from the description.
        val target = base.copy(description = "Sam's Pixel")
        assertTrue("naming the device must promote it to a test device", target.testDevice)
        assertEquals(PushRegistrationAction.Register(target), decide(Wanted(target), last = base))
    }

    @Test
    fun `an unchanged registration is re-posted once the refresh interval elapses`() {
        // The keep-alive: without this the server's last_seen_at is never refreshed and the 180-day
        // prune silently drops a device whose token, region, locale and flags simply never change.
        assertEquals(
            PushRegistrationAction.Register(base),
            decide(Wanted(base), last = base, sinceLastSent = PUSH_REFRESH_INTERVAL)
        )
        assertEquals(
            PushRegistrationAction.Register(base),
            decide(Wanted(base), last = base, sinceLastSent = 30.days)
        )
    }

    @Test
    fun `an unchanged registration is left alone just inside the refresh interval`() {
        assertEquals(
            PushRegistrationAction.NoOp,
            decide(Wanted(base), last = base, sinceLastSent = PUSH_REFRESH_INTERVAL - 1.hours)
        )
    }

    @Test
    fun `an unknown last-sent time counts as stale`() {
        // A record written before the timestamp slot existed. A redundant upsert is harmless; losing
        // the device to the prune is not, so unknown must re-post rather than no-op.
        assertEquals(
            PushRegistrationAction.Register(base),
            decide(Wanted(base), last = base, sinceLastSent = null)
        )
    }

    @Test
    fun `a token rotation unregisters the old token then registers the new one`() {
        val target = base.copy(token = "token-b")
        assertEquals(
            PushRegistrationAction.Reregister(previous = base, target = target),
            decide(Wanted(target), last = base)
        )
    }

    @Test
    fun `a region change unregisters the old region then registers the new one`() {
        val target = base.copy(regionId = 2L, sidecarBaseUrl = "https://other.example.org")
        assertEquals(
            PushRegistrationAction.Reregister(previous = base, target = target),
            decide(Wanted(target), last = base)
        )
    }
}
