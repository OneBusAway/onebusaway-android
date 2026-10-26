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
package org.onebusaway.android.database.oba

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a stop rename stores as its custom name (#2414). */
class NormalizedUserNameTest {

    @Test
    fun keepsANewName_trimmed() {
        assertEquals("Home", normalizedUserName("  Home ", officialName = "Pine St & 3rd Ave"))
    }

    @Test
    fun blankRemovesTheCustomName() {
        assertNull(normalizedUserName("", officialName = "Pine St & 3rd Ave"))
        assertNull(normalizedUserName("   ", officialName = "Pine St & 3rd Ave"))
    }

    @Test
    fun theOfficialNameRemovesTheCustomName() {
        // Saving the prefilled official name unchanged must not freeze it as a custom name.
        assertNull(normalizedUserName("Pine St & 3rd Ave ", officialName = "Pine St & 3rd Ave"))
    }

    @Test
    fun aNameDifferingOnlyInCaseIsKept() {
        assertEquals("pine st & 3rd ave", normalizedUserName("pine st & 3rd ave", officialName = "Pine St & 3rd Ave"))
    }

    @Test
    fun noOfficialNameKeepsAnyNonBlankName() {
        assertEquals("Home", normalizedUserName("Home", officialName = null))
    }
}
