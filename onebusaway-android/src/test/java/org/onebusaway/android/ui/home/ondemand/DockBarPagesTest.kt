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
package org.onebusaway.android.ui.home.ondemand

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.OnDemandDockState
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.bookingRule
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.util.GeoPoint

class DockBarPagesTest {

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.Rider)
    private val colors = mapOf("CC_CC1" to 0xFF78AA36.toInt(), "CC_CC2" to 0xFF3B82F6.toInt())

    private val inside = matchFor(service(areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
    private val insideNoContact = matchFor(service(id = "CC_CC2", bookingRules = mapOf("CC_b1" to bookingRule(phone = null)), areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
    private val outside = matchFor(service(id = "CC_CC2", areas = listOf(area(distance = 700.0, nearest = GeoPoint(45.056, -85.1))), matchReason = OnDemandMatchReason.AREA_NEARBY), now)

    @Test
    fun `an inside page takes the service colour, the inside title and the phone`() {
        val page = dockBarPages(OnDemandDockState.Bar(listOf(inside), probe), emptyMap(), colors, now, Locale.US, metric = false).single()
        assertEquals(0xFF78AA36.toInt(), page.color)
        assertEquals(0xFF78AA36.toInt(), page.thumbnailColor)
        // White is only 2.76:1 on #78AA36; black reaches 7.6:1 (spec §5, WCAG 4.5:1).
        assertEquals(0xFF000000.toInt(), page.textColor)
        assertEquals(R.string.ondemand_bar_inside, page.title?.res)
        assertEquals(TrailingAction.PHONE, page.trailing)
        assertEquals("231-582-6900", page.contact.phone)
    }

    @Test
    fun `a dark service colour reads white`() {
        val dark = mapOf("CC_CC1" to 0xFF1F3A5F.toInt())
        val page = dockBarPages(OnDemandDockState.Bar(listOf(inside), probe), emptyMap(), dark, now, Locale.US, metric = false).single()
        assertEquals(0xFFFFFFFF.toInt(), page.textColor)
    }

    @Test
    fun `an outside page is gray with the edge chevron, or nothing without a point`() {
        val page = dockBarPages(OnDemandDockState.Bar(listOf(outside), probe), emptyMap(), colors, now, Locale.US, metric = false).single()
        assertEquals(ONDEMAND_OUTSIDE_GRAY, page.color)
        // The bar's background goes gray, but the thumbnail's rings keep the service's own colour (D5).
        assertEquals(0xFF3B82F6.toInt(), page.thumbnailColor)
        assertEquals(R.string.ondemand_bar_outside_south, page.title?.res)
        assertEquals(TrailingAction.CHEVRON_EDGE, page.trailing)
        assertEquals(GeoPoint(45.056, -85.1), page.edgePoint)

        val noPoint = outside.copy(nearestPointOnBoundary = null)
        assertEquals(TrailingAction.NONE, trailingAction(noPoint, contactOf(noPoint.service), edgePoint = null))
        assertEquals(TrailingAction.CHEVRON_EDGE, trailingAction(noPoint, contactOf(noPoint.service), edgePoint = GeoPoint(45.0, -85.0)))
    }

    @Test
    fun `a near edge title arrives with the client geometry`() {
        val edges = mapOf("CC_CC1" to ZoneEdge(40.0, GeoPoint(45.0504, -85.1), 0.0))
        val page = dockBarPages(OnDemandDockState.Bar(listOf(inside), probe), edges, colors, now, Locale.US, metric = false).single()
        assertEquals(R.string.ondemand_bar_inside_near_edge_north, page.title?.res)
    }

    @Test
    fun `no contact means the detail chevron and no card primary`() {
        assertEquals(TrailingAction.CHEVRON_DETAIL, trailingAction(insideNoContact, contactOf(insideNoContact.service), edgePoint = null))
        assertNull(cardPrimary(insideNoContact.service))
        assertEquals(CardPrimary.Call("231-582-6900"), cardPrimary(inside.service))
        val onlineOnly = service(bookingRules = mapOf("CC_b1" to bookingRule(phone = null, bookingUrl = "https://book.example.org")))
        assertEquals(CardPrimary.Online("https://book.example.org"), cardPrimary(onlineOnly))
    }

    @Test
    fun `pages keep the stack order and the badge shows only for more than one`() {
        val pages = dockBarPages(OnDemandDockState.Bar(listOf(insideNoContact, inside), probe), emptyMap(), colors, now, Locale.US, metric = false)
        assertEquals(listOf("CC_CC2", "CC_CC1"), pages.map { it.match.service.id })
        assertTrue(showsBadge(2))
        assertFalse(showsBadge(1))
    }
}
