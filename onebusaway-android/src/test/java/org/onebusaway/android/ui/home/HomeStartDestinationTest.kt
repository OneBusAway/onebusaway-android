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
package org.onebusaway.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.ui.nav.NavRoutes

class HomeStartDestinationTest {
    @Test
    fun `honors March selections and safely falls back for retired sections`() {
        val prefs = FakePreferencesRepository()
        assertEquals(NavRoutes.HOME, prefs.homeStartDestination())
        listOf(
            1 to NavRoutes.HOME_STARRED_STOPS,
            2 to NavRoutes.HOME_STARRED_ROUTES,
            3 to NavRoutes.MY_REMINDERS,
            8 to NavRoutes.HOME,
            -1 to NavRoutes.HOME
        ).forEach { (oldValue, route) ->
            prefs.setInt(HOME_SECTION_KEY, oldValue)
            assertEquals(route, prefs.homeStartDestination())
        }
    }

    @Test
    fun `detail and settings visits preserve the section but choosing Map resets it`() {
        val prefs = FakePreferencesRepository()
        prefs.rememberHomeSection(NavRoutes.HOME_STARRED_STOPS)
        prefs.rememberHomeSection(NavRoutes.SETTINGS)
        prefs.rememberHomeSection("arrivals/stop")
        assertEquals(NavRoutes.HOME_STARRED_STOPS, prefs.homeStartDestination())
        prefs.rememberHomeSection(NavRoutes.HOME)
        assertEquals(NavRoutes.HOME, prefs.homeStartDestination())
    }
}
