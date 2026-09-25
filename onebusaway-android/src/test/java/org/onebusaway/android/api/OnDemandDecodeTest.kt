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
package org.onebusaway.android.api

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.OnDemandServiceDto
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.ServiceAreaDto
import org.onebusaway.android.api.contract.StopReference

/**
 * Decodes the responses captured from maglev against the Alexandria and Charlevoix flex feeds (see
 * the wiki's §3.4 worked example) and pins the wire contract the on-demand feature reads.
 */
class OnDemandDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun fixture(name: String) = File("src/androidTest/res/raw/$name").readText()

    private val pointModeReasons = setOf("areaContainsPoint", "stopWithinRadius", "areaNearby")
    private val viewportModeReasons = setOf("areaIntersectsViewport", "stopWithinViewport")

    @Test
    fun `service entry decodes the worked example`() {
        val data = json.decodeFromString<ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>>(fixture("ondemand_service_alexandria.json")).requireData()
        val entry = data.entry
        assertEquals("5088_77652", entry.id)
        assertEquals("5088", entry.agencyId)
        assertEquals("5088_77652", entry.routeId)
        assertEquals("zone", entry.serviceKind)
        assertNull(entry.matchReason)
        assertEquals(2, entry.rules.size)
        val first = entry.rules[0]
        assertEquals(listOf("5088_area_1449"), first.fromIds)
        assertEquals("05:00:00", first.startPickupTime)
        assertEquals("24:50:00", first.endPickupTime)
        assertEquals("25:00:00", first.endDropOffTime)
        assertEquals(listOf("5088_c_71675_b_85952_d_63"), first.calendarIds)
        assertEquals(2, first.pickupType)
        assertEquals("5088_booking_route_77652", first.pickupBookingRuleId)
        assertEquals(1.0, first.safeDurationFactor)

        val refs = data.references
        val booking = requireNotNull(refs.bookingRule("5088_booking_route_77652"))
        assertEquals(2, booking.bookingType)
        assertNull(booking.priorNoticeDurationMin)
        assertEquals(1, booking.priorNoticeLastDay)
        assertEquals("17:00:00", booking.priorNoticeLastTime)
        assertEquals(14, booking.priorNoticeStartDay)
        assertEquals("00:00:00", booking.priorNoticeStartTime)
        assertEquals("703-746-5222", booking.phoneNumber)
        assertNotNull(booking.infoUrl)

        val calendar = requireNotNull(refs.calendar("5088_c_71675_b_85952_d_63"))
        assertEquals(listOf("mon", "tue", "wed", "thu", "fri", "sat"), calendar.days)
        assertEquals(listOf("sun"), refs.calendar("5088_c_71675_b_85952_d_64")?.days)
        assertTrue(calendar.exceptedDates.isEmpty())

        assertEquals("America/Los_Angeles", refs.agency("5088")?.timezone)
        assertEquals(listOf("5088_77652"), refs.route("5088_77652")?.onDemandServiceIds)
        assertTrue(refs.locationGroups.isEmpty())
    }

    @Test
    fun `service area decodes bbox and full polygon geometry`() {
        val refs = json.decodeFromString<ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>>(fixture("ondemand_service_alexandria.json")).requireData().references
        val area = requireNotNull(refs.serviceArea("5088_area_1449"))
        assertEquals(listOf(-77.5372039, 38.617508, -76.9092198, 39.057831), area.bbox)
        assertNull(area.distanceToArea)
        val polygons = area.polygons()
        assertEquals(1, polygons.size)
        val ring = polygons[0][0]
        assertTrue(ring.size >= 4)
        for (point in ring) {
            assertTrue(point.longitude in area.bbox[0]..area.bbox[2])
            assertTrue(point.latitude in area.bbox[1]..area.bbox[3])
        }
    }

    @Test
    fun `services-for-location point mode carries match reason and distance`() {
        val data = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_location_point.json")).requireData()
        assertEquals(1, data.list.size)
        assertEquals("5088_77652", data.list[0].id)
        assertTrue(data.list[0].matchReason in pointModeReasons)
        val area = requireNotNull(data.references.serviceArea("5088_area_1449"))
        assertNotNull(area.distanceToArea)
        assertTrue(area.polygons().isNotEmpty())
    }

    @Test
    fun `services-for-location viewport mode uses simplified geometry and null distance`() {
        val data = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_location_viewport.json")).requireData()
        assertEquals(1, data.list.size)
        assertTrue(data.list[0].matchReason in viewportModeReasons)
        val area = requireNotNull(data.references.serviceArea("5088_area_1449"))
        assertNull(area.distanceToArea)
        assertNull(area.nearestPointOnBoundary)
        val ring = area.polygons()[0][0]
        assertTrue(ring.size in 4..257)
    }

    @Test
    fun `services-for-agency lists every service sorted by id`() {
        val data = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_agency_charlevoix.json")).requireData()
        assertEquals(listOf("CC_CC1", "CC_CC2_med", "CC_CC3", "CC_CC4"), data.list.map { it.id })
        val group = data.list.first { it.id == "CC_CC3" }
        assertEquals("stopGroup", group.serviceKind)
        assertNull(group.matchReason)
        val groupId = group.rules.single().fromIds.single()
        assertEquals(2, data.references.locationGroup(groupId)?.stopIds?.size)
        assertEquals(1, json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(fixture("ondemand_services_for_agency_alexandria.json")).requireData().list.size)
    }

    @Test
    fun `stop and route entries carry pointer fields`() {
        val stop = json.decodeFromString<ObaEnvelope<EntryWithReferences<StopReference>>>(fixture("stop_with_ondemand_pointer.json")).requireData().entry
        assertEquals("CC_CC_Ironton_Ferry_West", stop.id)
        assertEquals(listOf("CC_CC3"), stop.onDemandServiceIds)
        val route = json.decodeFromString<ObaEnvelope<EntryWithReferences<RouteReference>>>(fixture("route_with_ondemand_pointer.json")).requireData().entry
        assertEquals("CC_CC3", route.id)
        assertEquals(listOf("CC_CC3"), route.onDemandServiceIds)
    }

    @Test
    fun `geometry omitted or multipolygon decodes without throwing`() {
        val none = json.decodeFromString<ServiceAreaDto>("""{"id":"a","name":null,"description":null,"bbox":[0.0,0.0,1.0,1.0]}""")
        assertTrue(none.polygons().isEmpty())
        assertEquals(4, none.bbox.size)
        val multi = json.decodeFromString<ServiceAreaDto>(
            """{"id":"m","bbox":[0,0,3,3],"geometry":{"type":"MultiPolygon","coordinates":[
                 [[[0,0],[1,0],[1,1],[0,1],[0,0]],[[0.2,0.2],[0.4,0.2],[0.4,0.4],[0.2,0.4],[0.2,0.2]]],
                 [[[2,2],[3,2],[3,3],[2,3],[2,2]]]]}}"""
        )
        val polygons = multi.polygons()
        assertEquals(2, polygons.size)
        assertEquals(2, polygons[0].size)
        assertEquals(0.2, polygons[0][1][0].longitude, 0.0)
        assertEquals(2.0, polygons[1][0][0].latitude, 0.0)
        val point = json.decodeFromString<ServiceAreaDto>("""{"id":"p","bbox":[0,0,0,0],"geometry":{"type":"Point","coordinates":[1,2]}}""")
        assertTrue(point.polygons().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a position with one coordinate is malformed input, not an index error`() {
        json.decodeFromString<ServiceAreaDto>("""{"id":"s","bbox":[0,0,1,1],"geometry":{"type":"Polygon","coordinates":[[[0,0],[1],[1,1],[0,0]]]}}""").polygons()
    }
}
