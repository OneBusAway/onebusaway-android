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
package org.onebusaway.android.map.render

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.ACHROMATIC_ROUTE_CHROMA

@SuppressLint("RestrictedApi")
class ContinuationBadgeBitmapsTest {
    @Test
    fun `badge casing uses the theme tone`() {
        val routeColor = Hct.from(210.0, 75.0, 55.0).toInt()

        val lightOutline = ContinuationBadgeBitmaps.routeBadgeOutlineColor(routeColor, darkMode = false)
        val darkOutline = ContinuationBadgeBitmaps.routeBadgeOutlineColor(routeColor, darkMode = true)

        assertEquals(35.0, Hct.fromInt(lightOutline).tone, 1.0)
        assertEquals(85.0, Hct.fromInt(darkOutline).tone, 1.0)
    }

    @Test
    fun `a badge naming several routes cases itself in a neutral of the same tone`() {
        // A stacked badge (#2083) has no single hue to case with, so it keeps the theme tone and drops the
        // hue — the outline and the lines between its rows read against every band they enclose.
        val light = ContinuationBadgeBitmaps.neutralBadgeOutlineColor(darkMode = false)
        val dark = ContinuationBadgeBitmaps.neutralBadgeOutlineColor(darkMode = true)

        assertEquals(35.0, Hct.fromInt(light).tone, 1.0)
        assertEquals(85.0, Hct.fromInt(dark).tone, 1.0)
        // Grey to the same threshold the app uses to declare a route colour hueless, rather than exactly
        // zero: the tone is quantized into sRGB on the way out and lands a hair off achromatic.
        assertTrue(Hct.fromInt(light).chroma < ACHROMATIC_ROUTE_CHROMA)
        assertTrue(Hct.fromInt(dark).chroma < ACHROMATIC_ROUTE_CHROMA)
    }
}
