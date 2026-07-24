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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.mock.ArrivalsFixtures
import org.onebusaway.android.ui.arrivals.components.EtaStrip
import org.onebusaway.android.ui.arrivals.components.previewRowCallbacks
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The on-device half of the #2012 fix, end to end on the production path: a captured response whose
 * feed duplicates a trip instance is decoded, deduped, grouped, and actually **rendered** by the real
 * [EtaStrip].
 *
 * The strip keys its `LazyRow` pills by `(tripId, serviceDate, stopSequence)`, and a duplicate key is
 * a fatal `IllegalArgumentException` thrown from Compose's measure pass — so against the pre-fix dedup
 * this test doesn't fail an assertion, it dies inside `setContent` with the crash report's own
 * exception. [org.onebusaway.android.api.data.StopArrivalsDedupTest] pins the same guarantee at the
 * JVM/payload level; this one proves the whole chain holds on a device, where the throw happens.
 *
 * The fixture is a verbatim capture of `arrivals-and-departures-for-stop/97_256` (Everett Transit
 * route 8) taken 2026-07-24, the stop and agency from the crash report. Trip `97_108568` at
 * `stopSequence` 23 comes back twice on block `97_139` with two ordinary coach ids — `97_149` first
 * with no position report, then `97_317` with a real one — so neither entry is the block-id phantom of
 * #1710 and the old dedup left both standing. A third, genuinely distinct trip (`97_108569`, schedule
 * only) shares the route and direction, so all of it lands in one strip.
 */
class EtaStripDuplicateArrivalsRenderTest {

    // Unconfined composition — see createUnconfinedComposeRule (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    @Test
    fun rendersOnePillPerTripInstance() {
        val group = routeGroupFromFixture()

        // Render FIRST, before any assertion on the projected data: the regression is a throw out of
        // Compose's measure pass, so this line is the guard. Asserting the trip list up front would
        // instead fail on the duplicate before the strip ever composed, and the crash path — the thing
        // that actually reached riders — would go unexercised.
        composeRule.setContent {
            Box(Modifier.width(320.dp)) {
                EtaStrip(trips = group.trips, actionsFor = { null }, callbacks = previewRowCallbacks())
            }
        }

        // The LazyRow really built its items rather than composing an empty strip. Both the fixture's
        // `currentTime` and its predicted times are fixed, so this ETA is exactly 43 for the test's
        // duration — and it's the strip's only "43" (the other pill is over an hour out, and neither
        // clock subline contains it in any time zone).
        composeRule.onNodeWithText("43", substring = true).assertExists()

        // The duplicated trip instance collapsed to one entry; the distinct trip survived alongside it.
        assertEquals(listOf("97_108568", "97_108569"), group.trips.map { it.tripId })
        // The located coach won the duplicate even though the feed listed the unlocated one first —
        // the AVL preference decided it, not feed order (see collapseDuplicateTripInstances).
        assertEquals("97_317", group.trips.first().vehicleId)
    }

    /** The fixture's arrivals, projected and grouped exactly as the arrivals screen does. */
    private fun routeGroupFromFixture(): RouteRowGroup {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val env = ArrivalsFixtures.load(context, FIXTURE)
        val arrivals = ArrivalsFixtures.convert(context, env, false)
        // Agency name only orders rows; the fixture is a single (route, direction).
        return groupArrivalsByRouteDirection(arrivals) { null }.single()
    }

    private companion object {
        const val FIXTURE = "arrivals_and_departures_for_stop_97_256_duplicate_vehicles"
    }
}
