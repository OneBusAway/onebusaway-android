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
import org.junit.Test

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
}
