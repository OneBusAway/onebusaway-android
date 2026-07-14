/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.haversineMeters

class RouteBadgeLayoutTest {

    @Test
    fun `badge uses distance midpoint rather than middle vertex`() {
        val placement = layoutRouteBadges(
            listOf(input("route", path(0.0, 0.0, 1.0, 3.0))),
        ).single()

        assertEquals(0.0, placement.point.latitude, 0.000001)
        assertEquals(1.5, placement.point.longitude, 0.000001)
        assertFalse(placement.overlaps)
    }

    @Test
    fun `badge uses midpoint of longest shape`() {
        val placement = layoutRouteBadges(
            listOf(
                RouteBadgeLayoutInput(
                    "route",
                    listOf(path(0.0, 0.0, 0.01), path(1.0, 10.0, 14.0)),
                )
            )
        ).single()

        assertEquals(1.0, placement.point.latitude, 0.001)
        assertEquals(12.0, placement.point.longitude, 0.000001)
    }

    @Test
    fun `shared routes stagger along their line by geographic distance`() {
        val shared = path(0.0, 0.0, 0.1)
        val placements = layoutRouteBadges(
            listOf(input("first", shared), input("second", shared)),
            minimumSeparationMeters = 1_000.0,
        )

        assertEquals(0.05, placements[0].point.longitude, 0.000001)
        assertTrue(placements[1].point.longitude < placements[0].point.longitude)
        assertTrue(haversineMeters(placements[0].point, placements[1].point) >= 999.9)
        assertFalse(placements[1].overlaps)
    }

    @Test
    fun `input order controls midpoint priority`() {
        val shared = path(0.0, 0.0, 0.1)
        val placements = layoutRouteBadges(
            listOf(input("priority", shared), input("later", shared)),
            minimumSeparationMeters = 1_000.0,
        )

        assertEquals(listOf("priority", "later"), placements.map { it.routeId })
        assertEquals(0.05, placements.first().point.longitude, 0.000001)
    }

    @Test
    fun `overlap is accepted when a route is too short to stagger`() {
        val short = path(0.0, 0.0, 0.001)
        val placements = layoutRouteBadges(
            listOf(input("first", short), input("second", short)),
            minimumSeparationMeters = 300.0,
        )

        assertFalse(placements.first().overlaps)
        assertTrue(placements.last().overlaps)
        assertEquals(placements.first().point, placements.last().point)
    }

    @Test
    fun `degenerate paths receive no badge`() {
        val placements = layoutRouteBadges(
            listOf(
                RouteBadgeLayoutInput(
                    "route",
                    listOf(listOf(GeoPoint(1.0, 1.0), GeoPoint(1.0, 1.0))),
                )
            )
        )

        assertTrue(placements.isEmpty())
    }

    private fun input(id: String, points: List<GeoPoint>) =
        RouteBadgeLayoutInput(id, listOf(points))

    private fun path(latitude: Double, vararg longitudes: Double) =
        longitudes.map { longitude -> GeoPoint(latitude, longitude) }
}
