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
package org.onebusaway.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.region.Region

/** JVM unit tests for the pure [BikeshareAvailability.isEnabled] predicate (region + custom OTP URL). */
class BikeshareAvailabilityTest {

    private fun region(supportsBikeshare: Boolean) = Region(supportsOtpBikeshare = supportsBikeshare)

    @Test
    fun `enabled when the region supports OTP bikeshare`() {
        assertTrue(BikeshareAvailability.isEnabled(region(supportsBikeshare = true), null))
    }

    @Test
    fun `disabled when the region does not support bikeshare and no custom OTP URL`() {
        assertFalse(BikeshareAvailability.isEnabled(region(supportsBikeshare = false), null))
        assertFalse(BikeshareAvailability.isEnabled(region(supportsBikeshare = false), ""))
    }

    @Test
    fun `enabled when a custom OTP URL is set, even if the region does not support bikeshare`() {
        assertTrue(BikeshareAvailability.isEnabled(region(supportsBikeshare = false), "https://otp.example"))
    }

    @Test
    fun `custom OTP URL enables bikeshare even with no region`() {
        assertTrue(BikeshareAvailability.isEnabled(null, "https://otp.example"))
    }

    @Test
    fun `disabled with no region and no custom OTP URL`() {
        assertFalse(BikeshareAvailability.isEnabled(null, null))
        assertFalse(BikeshareAvailability.isEnabled(null, ""))
    }
}
