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
package org.onebusaway.android.ui.tripresults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.TripAlert
import org.onebusaway.android.directions.model.TripAlertSeverity
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.ui.compose.components.AlertSeverity

/** Covers [alertItems], the pure projection of an itinerary's leg alerts onto the banner rows (#2143). */
class TripAlertsTest {

    @Test
    fun collapsesTheSameAlertAcrossLegsAndNamesEveryRideItApplies() {
        val suspension = alert(id = "a1", header = "1 Line service is suspended")
        val plan = itinerary(
            walkLeg(),
            transitLeg(shortName = "1 Line", alerts = listOf(suspension)),
            walkLeg(),
            // The same disruption, republished under a fresh id — the #1593 shape. Identity is the
            // content, so this must not become a second row.
            transitLeg(shortName = "2 Line", alerts = listOf(suspension.copy(id = "a2")))
        )

        val items = plan.alertItems()

        assertEquals(1, items.size)
        assertEquals("1 Line service is suspended", items[0].summary)
        assertEquals(listOf("1 Line", "2 Line"), items[0].routeLabels)
    }

    @Test
    fun ordersLoudestFirstAndKeepsTravelOrderWithinATone() {
        val plan = itinerary(
            transitLeg(
                shortName = "5",
                alerts = listOf(
                    alert(id = "info", header = "Stop moved", severity = TripAlertSeverity.INFO),
                    alert(id = "warn1", header = "Crowding expected", severity = TripAlertSeverity.WARNING),
                    alert(id = "severe", header = "No service", severity = TripAlertSeverity.SEVERE),
                    alert(id = "warn2", header = "Elevator out", severity = TripAlertSeverity.WARNING)
                )
            )
        )

        assertEquals(
            listOf("No service", "Crowding expected", "Elevator out", "Stop moved"),
            plan.alertItems().map { it.summary }
        )
        assertEquals(
            listOf(AlertSeverity.ERROR, AlertSeverity.WARNING, AlertSeverity.WARNING, AlertSeverity.INFO),
            plan.alertItems().map { it.severity }
        )
    }

    /** An unstated severity is still an alert — it must not be demoted below a stated warning. */
    @Test
    fun unstatedSeverityIsDrawnAsAWarning() {
        val plan = itinerary(
            transitLeg(
                shortName = "5",
                alerts = listOf(alert(id = "a", header = "Something happened", severity = TripAlertSeverity.UNKNOWN_SEVERITY))
            )
        )

        assertEquals(AlertSeverity.WARNING, plan.alertItems().single().severity)
    }

    @Test
    fun headerlessAlertLeadsWithItsDescriptionAndDoesNotRepeatIt() {
        val plan = itinerary(
            transitLeg(
                shortName = "5",
                alerts = listOf(alert(id = "a", header = null, description = "Reroute in effect"))
            )
        )

        val item = plan.alertItems().single()
        assertEquals("Reroute in effect", item.summary)
        assertNull(item.description)
        // The url is still detail worth expanding for, so the row keeps its tap target.
        assertTrue(item.hasDetail)
    }

    @Test
    fun alertWithNoRiderVisibleTextIsDropped() {
        val plan = itinerary(
            transitLeg(shortName = "5", alerts = listOf(alert(id = "a", header = null, description = null)))
        )

        assertTrue(plan.alertItems().isEmpty())
    }

    /** A headline with nothing behind it takes no tap target — the row must say so. */
    @Test
    fun headlineOnlyAlertHasNoDetail() {
        val plan = itinerary(
            transitLeg(
                shortName = "5",
                alerts = listOf(alert(id = "a", header = "Crowding expected", description = null, url = null))
            )
        )

        assertFalse(plan.alertItems().single().hasDetail)
    }

    /** A leg whose route publishes no name at all contributes no label rather than a blank one. */
    @Test
    fun unnamedRouteContributesNoLabel() {
        val plan = itinerary(
            TripLeg(
                mode = TripMode.FERRY,
                alerts = listOf(alert(id = "a", header = "Dock closed"))
            )
        )

        assertEquals(emptyList<String>(), plan.alertItems().single().routeLabels)
    }

    @Test
    fun anItineraryWithNoAlertsProducesNoRows() {
        assertTrue(itinerary(walkLeg(), transitLeg(shortName = "5")).alertItems().isEmpty())
    }

    private fun itinerary(vararg legs: TripLeg) = TripItinerary(legs = legs.toList())

    private fun walkLeg() = TripLeg(mode = TripMode.WALK)

    private fun transitLeg(shortName: String, alerts: List<TripAlert> = emptyList()) = TripLeg(
        mode = TripMode.BUS,
        routeShortName = shortName,
        alerts = alerts
    )

    private fun alert(
        id: String,
        header: String? = "Header",
        description: String? = "Description",
        url: String? = "https://example.org",
        severity: TripAlertSeverity = TripAlertSeverity.WARNING
    ) = TripAlert(id = id, header = header, description = description, url = url, severity = severity)
}
