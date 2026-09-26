package org.onebusaway.android.api.adapters

import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
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
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.requireData
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceDayTime

class OnDemandAdaptersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun entry(name: String) = json.decodeFromString<ObaEnvelope<EntryWithReferences<OnDemandServiceDto>>>(File("src/androidTest/res/raw/$name").readText()).requireData()

    private fun list(name: String) = json.decodeFromString<ObaEnvelope<ListWithReferences<OnDemandServiceDto>>>(File("src/androidTest/res/raw/$name").readText()).requireData()

    @Test
    fun `entry adapts and resolves every reference`() {
        val service = entry("ondemand_service_alexandria.json").toOnDemandService()
        assertEquals("5088_77652", service.id)
        assertEquals(OnDemandServiceKind.ZONE, service.kind)
        assertNull(service.matchReason)
        assertEquals("America/Los_Angeles", service.agencyTimezone)
        assertEquals(2, service.rules.size)
        val rule = service.rules[0]
        assertEquals(ServiceDayTime(5 * 3600), rule.startPickupTime)
        assertEquals(ServiceDayTime(24 * 3600 + 50 * 60), rule.endPickupTime)
        assertEquals(ServiceDayTime(25 * 3600), rule.endDropOffTime)
        val booking = requireNotNull(service.pickupBookingRule(rule))
        assertEquals(BookingType.PRIOR_DAYS, booking.bookingType)
        assertEquals(1, booking.priorNoticeLastDay)
        assertEquals(ServiceDayTime(17 * 3600), booking.priorNoticeLastTime)
        assertEquals(14, booking.priorNoticeStartDay)
        assertEquals("703-746-5222", booking.phoneNumber)
        val calendar = requireNotNull(service.calendars["5088_c_71675_b_85952_d_63"])
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), calendar.days)
        assertEquals(LocalDate.of(2025, 12, 1), calendar.startDate)
        assertTrue(calendar.isActiveOn(LocalDate.of(2026, 3, 11)))
        assertTrue(!calendar.isActiveOn(LocalDate.of(2026, 3, 15)))
        val area = service.areas.single()
        assertEquals("5088_area_1449", area.id)
        assertEquals(-77.5372039, area.southWest.longitude, 0.0)
        assertEquals(39.057831, area.northEast.latitude, 0.0)
        assertTrue(area.polygons.isNotEmpty())
    }

    @Test
    fun `list adapts match reasons and viewport geometry`() {
        val services = list("ondemand_services_for_location_viewport.json").toOnDemandServices()
        assertEquals(1, services.size)
        assertTrue(services[0].matchReason in setOf(OnDemandMatchReason.AREA_INTERSECTS_VIEWPORT, OnDemandMatchReason.STOP_WITHIN_VIEWPORT))
        assertNull(services[0].areas.single().distanceToAreaMeters)
        val group = list("ondemand_services_for_agency_charlevoix.json").toOnDemandServices().first { it.id == "CC_CC3" }
        assertEquals(OnDemandServiceKind.STOP_GROUP, group.kind)
        assertEquals(2, group.locationGroups.single().stopIds.size)
        assertTrue(group.areas.isEmpty())
    }

    @Test
    fun `a list drops a service that fails to adapt and keeps the rest`() {
        val response = list("ondemand_services_for_agency_charlevoix.json")
        val broken = response.list[0].let { it.copy(rules = listOf(it.rules[0].copy(startPickupTime = "7:20"))) }
        val twoServices = response.copy(list = listOf(broken, response.list[2]))

        assertEquals(listOf("CC_CC3"), twoServices.toOnDemandServices().map { it.id })
    }

    @Test
    fun `a list drops a service whose area has a short position and keeps the rest`() {
        val viewport = list("ondemand_services_for_location_viewport.json")
        val shortPositions = viewport.references.serviceAreas.map { it.copy(nearestPointOnBoundary = listOf(-77.05)) }
        val group = list("ondemand_services_for_agency_charlevoix.json").list.first { it.id == "CC_CC3" }
        val response = viewport.copy(list = viewport.list + group, references = viewport.references.copy(serviceAreas = shortPositions))

        assertEquals(listOf("CC_CC3"), response.toOnDemandServices().map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a single service stays strict`() {
        val response = entry("ondemand_service_alexandria.json")
        val broken = response.entry.let { it.copy(rules = listOf(it.rules[0].copy(startPickupTime = "7:20"))) }
        response.copy(entry = broken).toOnDemandService()
    }

    @Test
    fun `unknown wire enums fall back rather than throw`() {
        assertEquals(OnDemandServiceKind.UNKNOWN, OnDemandServiceKind.fromWire("teleport"))
        assertEquals(OnDemandServiceKind.UNKNOWN, OnDemandServiceKind.fromWire(null))
        assertEquals(OnDemandMatchReason.UNKNOWN, OnDemandMatchReason.fromWire("somethingNew"))
        assertNull(BookingType.fromWire(7))
    }

    @Test
    fun `stop and route adapters expose the pointers`() {
        val stop = json.decodeFromString<ObaEnvelope<EntryWithReferences<StopReference>>>(File("src/androidTest/res/raw/stop_with_ondemand_pointer.json").readText()).requireData()
        assertEquals(listOf("CC_CC3"), DtoStop(stop.entry).onDemandServiceIds)
        val route = requireNotNull(entry("ondemand_service_alexandria.json").references.route("5088_77652"))
        assertEquals(listOf("5088_77652"), DtoRoute(route).onDemandServiceIds)
        assertNotNull(ObaStopElement().onDemandServiceIds)
    }
}
