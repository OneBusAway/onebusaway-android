/*
 * Copyright 2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.nightlight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLightFlashPolicyTest {
    @Test
    fun `first launch never flashes before consent`() {
        assertFalse(shouldFlashNightLight(consentAccepted = false, userWantsFlashing = true))
    }

    @Test
    fun `accepted warning permits flashing when the user has not paused`() {
        assertTrue(shouldFlashNightLight(consentAccepted = true, userWantsFlashing = true))
    }

    @Test
    fun `user pause keeps flashing off after consent`() {
        assertFalse(shouldFlashNightLight(consentAccepted = true, userWantsFlashing = false))
    }
}
