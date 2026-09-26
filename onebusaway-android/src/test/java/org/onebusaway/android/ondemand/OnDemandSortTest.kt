package org.onebusaway.android.ondemand

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.BookingType
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.util.GeoPoint

class OnDemandSortTest {

    private val now = instant("2026-03-10T14:00:00-04:00")

    private fun match(id: String, name: String = id, distance: Double? = 0.0, tier: Int? = null, closed: Boolean = false, eligible: Boolean = false, advance: Boolean = false): OnDemandMatch {
        val base = service(
            id = id,
            name = name,
            areas = listOf(area(distance = distance, nearest = distance?.let { GeoPoint(45.0, -85.1) })),
            matchReason = if (distance == 0.0) OnDemandMatchReason.AREA_CONTAINS_POINT else OnDemandMatchReason.AREA_NEARBY,
            eligibility = if (eligible) OnDemandEligibility(EligibilityRequirement.CERTIFICATION_REQUIRED, null) else null,
            calendars = mapOf("CC_cal" to calendar(end = if (closed) java.time.LocalDate.of(2026, 2, 1) else java.time.LocalDate.of(2026, 12, 31))),
            bookingRules = mapOf("CC_b1" to if (advance) bookingRule(type = BookingType.PRIOR_DAYS, durationMin = null, lastDay = 1, lastTime = "15:10:00") else bookingRule())
        )
        val computed = matchFor(base, now)
        return if (tier == null) computed else computed.copy(availability = computed.availability.copy(usabilityTier = tier))
    }

    @Test
    fun `matchFor takes the nearest area's distance and boundary point`() {
        val far = area(id = "far", distance = 900.0, nearest = GeoPoint(45.2, -85.1))
        val near = area(id = "near", distance = 120.0, nearest = GeoPoint(45.05, -85.3))
        val match = matchFor(service(areas = listOf(far, near), matchReason = OnDemandMatchReason.AREA_NEARBY), now)
        assertEquals(120.0, match.distanceToAreaMeters)
        assertEquals(GeoPoint(45.05, -85.3), match.nearestPointOnBoundary)
        assertTrue(match.isNearby)
    }

    @Test
    fun `a stop group has no distance`() {
        val match = matchFor(service(areas = emptyList(), matchReason = OnDemandMatchReason.STOP_WITHIN_RADIUS), now)
        assertNull(match.distanceToAreaMeters)
        assertTrue(!match.isNearby)
    }

    @Test
    fun `tiers order open now, same-day, advance, eligibility, closed`() {
        val sorted = listOf(match("closed", closed = true), match("elig", eligible = true), match("advance", advance = true), match("open"), match("sameday", tier = TIER_SAME_DAY))
            .sortedSoonestUsable(Locale.US)
        assertEquals(listOf("open", "sameday", "advance", "elig", "closed"), sorted.map { it.service.id })
    }

    @Test
    fun `within a tier distance ascends with null last`() {
        val sorted = listOf(match("none", distance = null), match("far", distance = 800.0), match("inside", distance = 0.0), match("near", distance = 40.0))
            .map { it.copy(availability = it.availability.copy(usabilityTier = TIER_OPEN_NOW)) }
            .sortedSoonestUsable(Locale.US)
        assertEquals(listOf("inside", "near", "far", "none"), sorted.map { it.service.id })
    }

    @Test
    fun `names break ties naturally and case-insensitively`() {
        val sorted = listOf(match("b", name = "Route 10"), match("a", name = "route 9"), match("c", name = "Route 2"))
            .sortedSoonestUsable(Locale.US)
        assertEquals(listOf("Route 2", "route 9", "Route 10"), sorted.map { it.service.name })
    }
}
