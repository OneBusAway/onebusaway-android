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
package org.onebusaway.android.ui.search

import org.onebusaway.android.api.adapters.DtoStop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.provider.StopUserInfo

/** Pure-logic coverage for [toStopSearchResult]: custom-name / favorite decoration and defaults. */
class StopSearchMappingTest {

    // The ObaStop the search produces is a DtoStop over the wire reference; test the mapper on that.
    private val ref = StopReference(
        id = "1_101",
        name = "Spring St & 3rd Ave",
        direction = "NE",
        lat = 47.6,
        lon = -122.33
    )
    private val stop = DtoStop(ref)

    @Test
    fun usesServerNameAndDefaultsWhenNoUserInfo() {
        val result = stop.toStopSearchResult(userInfo = null)

        assertEquals("1_101", result.id)
        assertEquals("Spring St & 3rd Ave", result.name)
        assertEquals("Spring St & 3rd Ave", result.serverName)
        assertEquals("NE", result.direction)
        assertFalse(result.isFavorite)
        assertEquals(47.6, result.latitude, 0.0)
        assertEquals(-122.33, result.longitude, 0.0)
    }

    @Test
    fun prefersCustomNameAndReflectsFavorite() {
        val result = stop.toStopSearchResult(StopUserInfo(isFavorite = true, userName = "Home stop"))

        // Display name is the user's custom name; serverName keeps the raw value for intents.
        assertEquals("Home stop", result.name)
        assertEquals("Spring St & 3rd Ave", result.serverName)
        assertTrue(result.isFavorite)
    }

    @Test
    fun blankCustomNameFallsBackToServerName() {
        val result = stop.toStopSearchResult(StopUserInfo(isFavorite = false, userName = ""))

        assertEquals("Spring St & 3rd Ave", result.name)
    }

    @Test
    fun nullDirectionBecomesEmpty() {
        val result = DtoStop(ref.copy(direction = null)).toStopSearchResult(userInfo = null)

        assertEquals("", result.direction)
    }
}
