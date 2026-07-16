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

import org.junit.Assert.assertEquals
import org.junit.Test

class MapRenderStatePaddingTest {

    @Test
    fun `route focus extends rather than stacks on the top chrome`() {
        val state = MapRenderState()

        state.setTopChromeInset(100)
        state.setRouteFocusTopEdge(180)

        assertEquals(MapPadding(topPx = 180), state.padding.value)
    }

    @Test
    fun `clearing route focus restores the top chrome baseline`() {
        val state = MapRenderState()
        state.setTopChromeInset(100)
        state.setRouteFocusTopEdge(180)

        state.setRouteFocusTopEdge(0)

        assertEquals(MapPadding(topPx = 100), state.padding.value)
    }
}
