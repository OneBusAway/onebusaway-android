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
package org.onebusaway.android.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.region.Region

/** JVM tests for [WidgetDeployment] identity and [isServedBy], which pauses a widget after a region switch. */
class WidgetDeploymentTest {

    private val pugetSound = Region(id = 1L, name = "Puget Sound", obaBaseUrl = "https://api.pugetsound.onebusaway.org/")
    private val tampa = Region(id = 0L, name = "Tampa Bay", obaBaseUrl = "https://api.tampa.onebusaway.org/")

    private fun configOn(deployment: WidgetDeployment) = WidgetConfig("1_75403", "Pine St", "Pine St", deployment, mapOf("1_100" to "A"))

    @Test
    fun `a region is the same server whatever its name or base URL`() {
        val saved = WidgetDeployment.forRegion(pugetSound)
        val migrated = WidgetDeployment.forRegion(pugetSound.copy(name = "Seattle", obaBaseUrl = "https://new.example.org/"))

        assertTrue(saved.isSameServerAs(migrated))
        assertFalse(saved.isSameServerAs(WidgetDeployment.forRegion(tampa)))
    }

    @Test
    fun `a custom API URL is its own server, distinct from any region`() {
        val custom = WidgetDeployment.forCustomUrl("https://oba.example.org/")

        assertTrue(custom.isSameServerAs(WidgetDeployment.forCustomUrl("https://oba.example.org/")))
        assertFalse(custom.isSameServerAs(WidgetDeployment.forCustomUrl("https://other.example.org/")))
        assertFalse(custom.isSameServerAs(WidgetDeployment.forRegion(pugetSound)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a deployment must name exactly one server`() {
        WidgetDeployment(customApiUrl = "https://oba.example.org/", regionId = 1L, displayName = "both")
    }

    @Test
    fun `a widget is served only once the app is on its own server`() {
        val config = configOn(WidgetDeployment.forRegion(pugetSound))

        assertTrue(config.isServedBy(CurrentServer.On(WidgetDeployment.forRegion(pugetSound))))
        assertFalse(config.isServedBy(CurrentServer.On(WidgetDeployment.forRegion(tampa))))
        assertFalse(config.isServedBy(CurrentServer.Unavailable))
        // Loading isn't "served"; callers decide whether to wait or keep the cache.
        assertFalse(config.isServedBy(CurrentServer.Loading))
    }

    @Test
    fun `the pin callback carries the whole config, deployment included`() {
        val config = configOn(WidgetDeployment.forRegion(pugetSound))

        assertEquals(config, WidgetPrefs.decodeConfig(WidgetPrefs.encodeConfig(config)))
    }
}
