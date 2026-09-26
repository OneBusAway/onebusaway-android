package org.onebusaway.android.ondemand

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.util.GeoPoint

class OnDemandCopyTest {

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = GeoPoint(45.05, -85.1)

    private fun inside(tier: Int = TIER_OPEN_NOW, status: OnDemandStatus? = null): OnDemandMatch {
        val match = matchFor(service(areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
        return match.copy(availability = match.availability.copy(usabilityTier = tier, status = status ?: match.availability.status))
    }

    @Test
    fun `imperial distances round feet to ten below a tenth of a mile then miles to one decimal`() {
        assertEquals(DistanceText("0.2", DistanceUnit.MILES), formatDistance(300.0, metric = false, locale = Locale.US))
        assertEquals(DistanceText("30", DistanceUnit.FEET), formatDistance(10.0, metric = false, locale = Locale.US))
        assertEquals(DistanceText("0.1", DistanceUnit.MILES), formatDistance(161.0, metric = false, locale = Locale.US))
        assertEquals(DistanceText("3.1", DistanceUnit.MILES), formatDistance(5000.0, metric = false, locale = Locale.US))
    }

    @Test
    fun `metric distances round metres to ten below a kilometre then kilometres to one decimal`() {
        assertEquals(DistanceText("100", DistanceUnit.METERS), formatDistance(96.0, metric = true, locale = Locale.UK))
        assertEquals(DistanceText("300", DistanceUnit.METERS), formatDistance(300.0, metric = true, locale = Locale.UK))
        assertEquals(DistanceText("1.5", DistanceUnit.KILOMETERS), formatDistance(1500.0, metric = true, locale = Locale.UK))
        assertEquals(DistanceText("1,5", DistanceUnit.KILOMETERS), formatDistance(1500.0, metric = true, locale = Locale.GERMANY))
    }

    @Test
    fun `status copy picks the key per status and renders nothing for unknown`() {
        val zone = java.time.ZoneId.of(CHARLEVOIX_TZ)
        val until = statusText(OnDemandStatus.OpenNow(instant("2026-03-10T16:40:00-04:00")), zone, now, Locale.US)
        assertEquals(R.string.ondemand_status_open_until, until?.res)
        assertTrue((until?.args?.single() as String).contains("4:40"))
        assertEquals(R.string.ondemand_status_open_now_until, statusText(OnDemandStatus.OpenNow(now), zone, now, Locale.US, OpenStyle.OPEN_NOW_UNTIL)?.res)
        assertEquals(TextSpec(R.string.ondemand_status_open), statusText(OnDemandStatus.OpenNow(null), zone, now, Locale.US))
        val opens = statusText(OnDemandStatus.OpensAt(instant("2026-03-11T07:20:00-04:00")), zone, now, Locale.US)
        assertEquals(R.string.ondemand_status_opens, opens?.res)
        assertEquals(R.string.ondemand_relative_tomorrow_at, (opens?.args?.single() as TextSpec).res)
        val bookBy = statusText(OnDemandStatus.BookBy(instant("2026-03-10T15:10:00-04:00"), LocalDate.of(2026, 3, 11)), zone, now, Locale.US)
        assertEquals(R.string.ondemand_status_book_by, bookBy?.res)
        assertEquals(R.string.ondemand_relative_today_at, (bookBy?.args?.single() as TextSpec).res)
        assertEquals(R.string.ondemand_booking_opens, statusText(OnDemandStatus.BookingOpens(now, LocalDate.of(2026, 3, 12)), zone, now, Locale.US)?.res)
        assertEquals(TextSpec(R.string.ondemand_status_closed), statusText(OnDemandStatus.Closed, zone, now, Locale.US))
        assertNull(statusText(OnDemandStatus.Unknown, zone, now, Locale.US))
    }

    @Test
    fun `relative days name the weekday within a week and the date beyond`() {
        val zone = java.time.ZoneId.of(CHARLEVOIX_TZ)
        val saturday = relativeDayTime(instant("2026-03-14T09:00:00-04:00"), zone, now, Locale.US)
        assertEquals(R.string.ondemand_relative_day_at, saturday.res)
        assertEquals("Saturday", saturday.args[0])
        val nextMonth = relativeDayTime(instant("2026-04-14T09:00:00-04:00"), zone, now, Locale.US)
        assertTrue((nextMonth.args[0] as String).contains("Apr"))
    }

    @Test
    fun `card meta joins the status and the booking tag`() {
        val meta = cardMeta(inside().availability, now, Locale.US)
        assertEquals(listOf(R.string.ondemand_status_open_until, R.string.ondemand_tag_same_day), meta.map { it.res })
        assertEquals(listOf(R.string.ondemand_tag_same_day), cardMeta(inside(status = OnDemandStatus.Unknown).availability, now, Locale.US).map { it.res })
    }

    @Test
    fun `bar titles follow the precedence`() {
        assertEquals(TextSpec(R.string.ondemand_bar_inside), barTitle(inside(), probe, edge = null, now, Locale.US, metric = false))
        val nearNorth = ZoneEdge(30.0, GeoPoint(45.0503, -85.1), 0.0)
        val nearEdge = barTitle(inside(), probe, nearNorth, now, Locale.US, metric = false)
        assertEquals(R.string.ondemand_bar_inside_near_edge_north, nearEdge?.res)
        assertEquals(DistanceText("100", DistanceUnit.FEET), nearEdge?.args?.single())
        assertEquals(TextSpec(R.string.ondemand_bar_inside), barTitle(inside(), probe, ZoneEdge(150.0, probe, 0.0), now, Locale.US, metric = false))
        assertEquals(TextSpec(R.string.ondemand_tag_eligibility), barTitle(inside(tier = TIER_ELIGIBILITY), probe, null, now, Locale.US, metric = false))
        assertEquals(R.string.ondemand_status_opens, barTitle(inside(tier = TIER_SAME_DAY, status = OnDemandStatus.OpensAt(now)), probe, null, now, Locale.US, metric = false)?.res)
        assertEquals(R.string.ondemand_status_book_by, barTitle(inside(tier = TIER_ADVANCE, status = OnDemandStatus.BookBy(now, LocalDate.of(2026, 3, 11))), probe, null, now, Locale.US, metric = false)?.res)
        assertEquals(TextSpec(R.string.ondemand_status_closed), barTitle(inside(tier = TIER_UNKNOWN_OR_CLOSED, status = OnDemandStatus.Closed), probe, null, now, Locale.US, metric = false))
        assertNull(barTitle(inside(tier = TIER_UNKNOWN_OR_CLOSED, status = OnDemandStatus.Unknown), probe, null, now, Locale.US, metric = false))
        assertEquals(
            R.string.ondemand_booking_opens,
            barTitle(inside(tier = TIER_UNKNOWN_OR_CLOSED, status = OnDemandStatus.BookingOpens(now, LocalDate.of(2026, 3, 12))), probe, null, now, Locale.US, metric = false)?.res
        )
    }

    @Test
    fun `a point due south of a zone reads south of the zone with the server distance`() {
        val outside = matchFor(
            service(areas = listOf(area(distance = 5000.0, nearest = GeoPoint(45.095, -85.1))), matchReason = OnDemandMatchReason.AREA_NEARBY),
            now
        )
        val title = barTitle(outside, probe, edge = null, now, Locale.US, metric = false)
        assertEquals(R.string.ondemand_bar_outside_south, title?.res)
        assertEquals(DistanceText("3.1", DistanceUnit.MILES), title?.args?.single())
        // Server point missing: the client edge stands in, and with neither there is no title.
        val noPoint = outside.copy(nearestPointOnBoundary = null)
        assertEquals(R.string.ondemand_bar_outside_south, barTitle(noPoint, probe, ZoneEdge(5000.0, GeoPoint(45.095, -85.1), 0.0), now, Locale.US, metric = false)?.res)
        assertNull(barTitle(noPoint, probe, null, now, Locale.US, metric = false))
    }

    @Test
    fun `badge, address line, picker subtitle and detail location copy`() {
        assertEquals(TextSpec(R.string.ondemand_bar_badge, listOf(1, 2)), badgeText(1, 2))
        assertEquals(TextSpec(R.string.ondemand_address_inside, listOf("Dial-a-Ride")), addressLine("Dial-a-Ride", isInside = true, othersInside = 0))
        assertEquals(PluralSpec(R.plurals.ondemand_address_inside_more, 2, listOf("Dial-a-Ride", 2)), addressLine("Dial-a-Ride", isInside = true, othersInside = 2))
        assertEquals(TextSpec(R.string.ondemand_address_outside, listOf("Dial-a-Ride")), addressLine("Dial-a-Ride", isInside = false, othersInside = 0))
        assertEquals(TextSpec(R.string.ondemand_picker_subtitle_location, listOf("Boyne City")), pickerSubtitle(ProbeSource.Rider, "Boyne City"))
        assertEquals(TextSpec(R.string.ondemand_picker_map_center), pickerSubtitle(ProbeSource.MapCenter, null))
        assertEquals(TextSpec(R.string.ondemand_picker_subtitle_point, listOf("Boyne City")), pickerSubtitle(ProbeSource.Point(null), "Boyne City"))
        assertEquals(TextSpec(R.string.ondemand_detail_location_inside), detailLocationText(ProbeSource.Rider, isInside = true))
        assertEquals(TextSpec(R.string.ondemand_detail_location_outside), detailLocationText(ProbeSource.Rider, isInside = false))
        assertEquals(TextSpec(R.string.ondemand_detail_center_inside), detailLocationText(ProbeSource.MapCenter, isInside = true))
        assertEquals(TextSpec(R.string.ondemand_detail_center_outside), detailLocationText(ProbeSource.MapCenter, isInside = false))
        assertEquals(TextSpec(R.string.ondemand_detail_point_inside), detailLocationText(ProbeSource.Point("x"), isInside = true))
        assertEquals(TextSpec(R.string.ondemand_detail_point_outside), detailLocationText(ProbeSource.Point("x"), isInside = false))
        assertEquals(PluralSpec(R.plurals.ondemand_detail_zone_count, 3, listOf(3)), zoneCount(3))
    }
}
