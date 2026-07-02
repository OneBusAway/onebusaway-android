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
package org.onebusaway.android.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Unit tests for [ReminderEditorArgs]'s required-id invariant. */
class ReminderEditorArgsTest {

    @Test
    fun `accepts non-blank required ids`() {
        val args = ReminderEditorArgs(tripId = "trip_1", stopId = "stop_1")
        assertEquals("trip_1", args.tripId)
        assertEquals("stop_1", args.stopId)
    }

    @Test
    fun `rejects a blank trip id`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReminderEditorArgs(tripId = "", stopId = "stop_1")
        }
    }

    @Test
    fun `rejects a blank stop id`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReminderEditorArgs(tripId = "trip_1", stopId = "   ")
        }
    }
}
