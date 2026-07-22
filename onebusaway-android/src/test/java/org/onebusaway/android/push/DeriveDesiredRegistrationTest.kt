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
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.region

/**
 * Unit tests for [deriveDesiredRegistration] — the pure classification of the raw inputs into
 * wanted / opted-out / no-sidecar / not-yet-resolved. The load-bearing distinctions:
 * [DesiredRegistration.NoSidecar] vs [DesiredRegistration.Unresolved] (`NoSidecar` reconciles a stale
 * registration away, `Unresolved` freezes reconciliation until the input settles — collapsing the two
 * under a nullable target is exactly the bug that let a region-A registration outlive a switch to a
 * sidecar-less region B), and [DesiredRegistration.OptedOut] vs `NoSidecar` (an OS-level opt-out must
 * NOT unregister — see [DesiredRegistration.OptedOut] for the rationale).
 */
class DeriveDesiredRegistrationTest {

    private val sidecarRegion: Region = region(1).copy(sidecarBaseUrl = "https://sidecar.example.org")

    private fun derive(
        notificationsEnabled: Boolean = true,
        region: Region? = sidecarRegion,
        token: String = "token-a",
        locale: String = "en-US",
        testDeviceEnabled: Boolean = false,
        testDeviceName: String? = null
    ) = deriveDesiredRegistration(
        notificationsEnabled = notificationsEnabled,
        region = region,
        token = token,
        locale = locale,
        testDeviceEnabled = testDeviceEnabled,
        testDeviceName = testDeviceName
    )

    @Test
    fun `all inputs resolved yields the wanted registration`() {
        assertEquals(
            DesiredRegistration.Wanted(
                PushRegistration(
                    regionId = 1L,
                    sidecarBaseUrl = "https://sidecar.example.org",
                    token = "token-a",
                    locale = "en-US",
                    description = null
                )
            ),
            derive()
        )
    }

    @Test
    fun `notifications off is a definitive opt-out regardless of the other inputs`() {
        assertEquals(DesiredRegistration.OptedOut, derive(notificationsEnabled = false))
        // Definitive even while the async inputs are still settling: opting out never waits.
        assertEquals(DesiredRegistration.OptedOut, derive(notificationsEnabled = false, region = null, token = ""))
    }

    @Test
    fun `a missing region is unresolved, not no-sidecar`() {
        // The region flow is briefly null at cold start (RegionRepository seeds it asynchronously);
        // reading that as NoSidecar would DELETE the registration and re-POST it seconds later.
        assertEquals(DesiredRegistration.Unresolved, derive(region = null))
    }

    @Test
    fun `a region without a sidecar host is definitively no-sidecar, not unresolved`() {
        // The regression this type exists to prevent: a resolved region whose sidecarBaseUrl is blank
        // (the field defaults to "") is a fact — there is nowhere to be registered — so a registration
        // left over from a sidecar region must be reconciled away, not frozen in place.
        assertEquals(DesiredRegistration.NoSidecar, derive(region = region(2).copy(sidecarBaseUrl = "")))
        assertEquals(DesiredRegistration.NoSidecar, derive(region = region(2).copy(sidecarBaseUrl = null)))
    }

    @Test
    fun `a missing FCM token is unresolved`() {
        assertEquals(DesiredRegistration.Unresolved, derive(token = ""))
    }

    @Test
    fun `a named test device carries its description`() {
        val desired = derive(testDeviceEnabled = true, testDeviceName = "Sam's Pixel")
        assertEquals("Sam's Pixel", (desired as DesiredRegistration.Wanted).target.description)
        assertEquals(true, desired.target.testDevice)
    }

    @Test
    fun `an unnamed test device downgrades to an ordinary registration`() {
        // The server 422s test_device=true with a blank description, so an unnamed device must
        // register as ordinary rather than POST a request guaranteed to fail (matching iOS).
        for (name in listOf(null, "", "   ")) {
            val desired = derive(testDeviceEnabled = true, testDeviceName = name)
            assertEquals(null, (desired as DesiredRegistration.Wanted).target.description)
        }
    }

    @Test
    fun `a device name without the test-device flag is not sent`() {
        val desired = derive(testDeviceEnabled = false, testDeviceName = "Sam's Pixel")
        assertEquals(null, (desired as DesiredRegistration.Wanted).target.description)
    }

    @Test
    fun `an over-long device name is truncated to the server's limit`() {
        val desired = derive(testDeviceEnabled = true, testDeviceName = "N".repeat(300))
        assertEquals(
            "N".repeat(PUSH_DESCRIPTION_MAX_LENGTH),
            (desired as DesiredRegistration.Wanted).target.description
        )
    }

    @Test
    fun `truncation never splits a surrogate pair`() {
        // 254 single-unit chars + a two-unit emoji = 256 UTF-16 units: a bare take(255) would cut the
        // pair in half, sending a lone surrogate (mangled to '?'/U+FFFD) as the final character.
        val desired = derive(testDeviceEnabled = true, testDeviceName = "N".repeat(254) + "😀")
        assertEquals("N".repeat(254), (desired as DesiredRegistration.Wanted).target.description)
    }

    @Test
    fun `an emoji that fits exactly within the limit is kept whole`() {
        val name = "N".repeat(PUSH_DESCRIPTION_MAX_LENGTH - 2) + "😀"
        val desired = derive(testDeviceEnabled = true, testDeviceName = name)
        assertEquals(name, (desired as DesiredRegistration.Wanted).target.description)
    }
}
