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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.ui.compose.components.RouteBadge

/** The undo snackbar's route badge goes where a translation put the route (#2366). */
class RouteMentionTest {

    private val route = RouteBadge("44", 0xFF0000FF.toInt())

    @Test
    fun splitsAroundThePlaceholder() {
        val mention = RouteMention.of("Arrivals for %1\$s hidden", route)!!
        assertEquals("Arrivals for ", mention.before)
        assertEquals(" hidden", mention.after)
        assertEquals(route, mention.route)
    }

    @Test
    fun placeholderAtEitherEndLeavesThatSideEmpty() {
        assertEquals("", RouteMention.of("%1\$s hidden", route)!!.before)
        assertEquals("", RouteMention.of("Hidden: %1\$s", route)!!.after)
    }

    @Test
    fun templateWithoutThePlaceholderHasNoMention() {
        assertNull(RouteMention.of("Route hidden", route))
    }
}
