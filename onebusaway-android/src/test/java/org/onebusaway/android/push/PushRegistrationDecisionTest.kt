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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [decidePushRegistration] — the pure register/refresh/unregister reconciliation for OBACloud push
 * registration (#1957). Covers each transition between the desired registration ([target]) and the one
 * last sent to the server ([last]).
 */
class PushRegistrationDecisionTest {

    private val base = PushRegistration(
        regionId = 1L,
        sidecarBaseUrl = "https://sidecar.example.org",
        token = "token-a",
        locale = "en-US",
        testDevice = false,
    )

    @Test
    fun `nothing desired and nothing on record is a no-op`() {
        assertEquals(PushRegistrationAction.NoOp, decidePushRegistration(target = null, last = null))
    }

    @Test
    fun `first registration when nothing is on record`() {
        assertEquals(PushRegistrationAction.Register(base), decidePushRegistration(target = base, last = null))
    }

    @Test
    fun `opting out unregisters the recorded registration`() {
        assertEquals(PushRegistrationAction.Unregister(base), decidePushRegistration(target = null, last = base))
    }

    @Test
    fun `unchanged registration is a no-op`() {
        assertEquals(PushRegistrationAction.NoOp, decidePushRegistration(target = base, last = base))
    }

    @Test
    fun `a locale change re-posts on the same token and region`() {
        val target = base.copy(locale = "es-MX")
        assertEquals(PushRegistrationAction.Register(target), decidePushRegistration(target = target, last = base))
    }

    @Test
    fun `a test-device flag change re-posts on the same token and region`() {
        val target = base.copy(testDevice = true)
        assertEquals(PushRegistrationAction.Register(target), decidePushRegistration(target = target, last = base))
    }

    @Test
    fun `a token rotation unregisters the old token then registers the new one`() {
        val target = base.copy(token = "token-b")
        assertEquals(
            PushRegistrationAction.Reregister(previous = base, target = target),
            decidePushRegistration(target = target, last = base),
        )
    }

    @Test
    fun `a region change unregisters the old region then registers the new one`() {
        val target = base.copy(regionId = 2L, sidecarBaseUrl = "https://other.example.org")
        assertEquals(
            PushRegistrationAction.Reregister(previous = base, target = target),
            decidePushRegistration(target = target, last = base),
        )
    }
}
