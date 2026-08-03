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
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.ui.compose.components.AlertSeverity

/** Covers [alertItems], the pure projection of a leg's own alerts onto the rows drawn under its header (#2143). */
class TripAlertsTest {

    /** The #1593 shape, scoped to one leg: a feed republishing the same disruption under a fresh id. */
    @Test
    fun aRepublishedDuplicateOnOneLegCollapsesToOneRow() {
        val suspension = alert(id = "a1", header = "1 Line service is suspended")
        val leg = transitLeg(
            shortName = "1 Line",
            alerts = listOf(suspension, suspension.copy(id = "a2"))
        )

        val items = leg.alertItems()

        assertEquals(1, items.size)
        assertEquals("1 Line service is suspended", items[0].summary)
    }

    /**
     * The point of placing alerts per leg: one disruption spanning two rides is a fact about *each* of
     * them, and each leg draws it under its own header. Collapsing the pair into a single row is what
     * the head-of-itinerary banner did, and it left the rider to work out which ride was affected.
     */
    @Test
    fun theSameAlertOnTwoLegsIsDrawnUnderEach() {
        val suspension = alert(id = "a1", header = "1 Line service is suspended")
        val first = transitLeg(shortName = "1 Line", alerts = listOf(suspension))
        // Republished under a fresh id on the second leg — still one row there, and still its own row.
        val second = transitLeg(shortName = "2 Line", alerts = listOf(suspension.copy(id = "a2")))

        assertEquals("1 Line service is suspended", first.alertItems().single().summary)
        assertEquals("1 Line service is suspended", second.alertItems().single().summary)
    }

    @Test
    fun ordersLoudestFirstAndKeepsFeedOrderWithinATone() {
        val leg = transitLeg(
            shortName = "5",
            alerts = listOf(
                alert(id = "info", header = "Stop moved", severity = TripAlertSeverity.INFO),
                alert(id = "warn1", header = "Crowding expected", severity = TripAlertSeverity.WARNING),
                alert(id = "severe", header = "No service", severity = TripAlertSeverity.SEVERE),
                alert(id = "warn2", header = "Elevator out", severity = TripAlertSeverity.WARNING)
            )
        )

        assertEquals(
            listOf("No service", "Crowding expected", "Elevator out", "Stop moved"),
            leg.alertItems().map { it.summary }
        )
        assertEquals(
            listOf(AlertSeverity.ERROR, AlertSeverity.WARNING, AlertSeverity.WARNING, AlertSeverity.INFO),
            leg.alertItems().map { it.severity }
        )
    }

    /** An unstated severity is still an alert — it must not be demoted below a stated warning. */
    @Test
    fun unstatedSeverityIsDrawnAsAWarning() {
        val leg = transitLeg(
            shortName = "5",
            alerts = listOf(alert(id = "a", header = "Something happened", severity = TripAlertSeverity.UNKNOWN_SEVERITY))
        )

        assertEquals(AlertSeverity.WARNING, leg.alertItems().single().severity)
    }

    @Test
    fun headerlessAlertLeadsWithItsDescriptionAndDoesNotRepeatIt() {
        val leg = transitLeg(
            shortName = "5",
            alerts = listOf(alert(id = "a", header = null, description = "Reroute in effect"))
        )

        val item = leg.alertItems().single()
        assertEquals("Reroute in effect", item.summary)
        assertNull(item.description)
        // The url is still detail worth expanding for, so the row keeps its tap target.
        assertTrue(item.hasDetail)
    }

    @Test
    fun alertWithNoRiderVisibleTextIsDropped() {
        val leg = transitLeg(shortName = "5", alerts = listOf(alert(id = "a", header = null, description = null)))

        assertTrue(leg.alertItems().isEmpty())
    }

    /** A headline with nothing behind it takes no tap target — the row must say so. */
    @Test
    fun headlineOnlyAlertHasNoDetail() {
        val leg = transitLeg(
            shortName = "5",
            alerts = listOf(alert(id = "a", header = "Crowding expected", description = null, url = null))
        )

        assertFalse(leg.alertItems().single().hasDetail)
    }

    /**
     * A walk carries alerts too, and has its own header to draw them under — so nothing an itinerary-wide
     * banner used to pool is lost by moving the rows onto the legs.
     */
    @Test
    fun aWalkLegProjectsItsOwnAlerts() {
        val leg = TripLeg(mode = TripMode.WALK, alerts = listOf(alert(id = "a", header = "Sidewalk closed")))

        assertEquals("Sidewalk closed", leg.alertItems().single().summary)
    }

    @Test
    fun aLegWithNoAlertsProducesNoRows() {
        assertTrue(transitLeg(shortName = "5").alertItems().isEmpty())
    }

    /**
     * What a folded interline chain draws: the continuation's alerts joined to the leader's under the
     * one header they share — an alert scoped to the whole chain drawn once, and the merged set still
     * loudest-first rather than in the order the legs happened to be folded.
     */
    @Test
    fun mergingLegsKeepsOneRowPerAlertLoudestFirst() {
        val chainWide = alert(id = "chain", header = "Bridge is up")
        val leader = transitLeg(
            shortName = "8",
            alerts = listOf(chainWide, alert(id = "a", header = "Stop moved", severity = TripAlertSeverity.INFO))
        ).alertItems()
        // The same chain-wide alert again (republished id), plus one louder than anything the leader had.
        val continuation = transitLeg(
            shortName = "12",
            alerts = listOf(
                chainWide.copy(id = "chain2"),
                alert(id = "b", header = "No service after 9 PM", severity = TripAlertSeverity.SEVERE)
            )
        ).alertItems()

        assertEquals(
            listOf("No service after 9 PM", "Bridge is up", "Stop moved"),
            leader.mergedWith(continuation).map { it.summary }
        )
    }

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
