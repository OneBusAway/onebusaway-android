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
package org.onebusaway.android.ui.arrivals.dialogs

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the service alert dialog shows for each combination of header and description (#2448). */
class SituationTextTest {

    private val longHeadline =
        "Route 40 is rerouted between Fremont and Ballard through Sunday night because of " +
            "road work on Leary Way NW; use the temporary stops on NW 46th St and Shilshole Ave NW"

    @Test
    fun longHeadlineIsKeptWholeAboveTheDescription() {
        assertEquals(
            SituationText(longHeadline, showDescription = true, showNoDescription = false),
            situationText(longHeadline, "Stops at 3rd Ave NW are closed.")
        )
    }

    @Test
    fun headlineIsTrimmed() {
        assertEquals("Snow routes", situationText("  Snow routes \n", "").headline)
    }

    @Test
    fun emptyDescriptionShowsTheHeadlineAloneWithNoPlaceholder() {
        assertEquals(
            SituationText("Snow routes", showDescription = false, showNoDescription = false),
            situationText("Snow routes", "")
        )
        assertEquals(
            SituationText("Snow routes", showDescription = false, showNoDescription = false),
            situationText("Snow routes", " \n ")
        )
    }

    @Test
    fun descriptionThatRepeatsTheHeadlineIsShownOnce() {
        assertEquals(
            SituationText("Snow routes in effect", showDescription = false, showNoDescription = false),
            situationText("Snow routes in effect", "Snow routes\nin effect\n")
        )
    }

    @Test
    fun descriptionAloneIsShownWithoutAHeadline() {
        assertEquals(
            SituationText(null, showDescription = true, showNoDescription = false),
            situationText(null, "Stops at 3rd Ave NW are closed.")
        )
        assertEquals(
            SituationText(null, showDescription = true, showNoDescription = false),
            situationText("  ", "Stops at 3rd Ave NW are closed.")
        )
    }

    @Test
    fun noTextAtAllShowsThePlaceholder() {
        assertEquals(
            SituationText(null, showDescription = false, showNoDescription = true),
            situationText(null, "")
        )
    }
}
