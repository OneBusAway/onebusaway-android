/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBadgeLayoutTest {

    private val viewport = ScreenRect(0f, 0f, 200f, 120f)

    @Test
    fun `a crossing route is placed at the first safe viewport edge`() {
        val placement = layoutRouteBadges(
            viewport,
            listOf(input("route", 40f, 20f, point(-50f, 60f), point(250f, 60f))),
            edgeMarginPx = 0f,
        ).single()

        assertEquals(20f, placement.center.x, 0f)
        assertEquals(60f, placement.center.y, 0f)
        assertFalse(placement.overlaps)
    }

    @Test
    fun `a route contained in the viewport uses its endpoint nearest the edge`() {
        val placement = layoutRouteBadges(
            viewport,
            listOf(input("route", 20f, 20f, point(35f, 60f), point(150f, 60f))),
            edgeMarginPx = 0f,
        ).single()

        assertEquals(35f, placement.center.x, 0f)
    }

    @Test
    fun `later routes take the next edge candidate instead of overlapping`() {
        val path = arrayOf(point(-50f, 60f), point(250f, 60f))
        val placements = layoutRouteBadges(
            viewport,
            listOf(
                input("first", 40f, 20f, *path),
                input("second", 40f, 20f, *path),
            ),
            edgeMarginPx = 0f,
        )

        assertEquals(20f, placements[0].center.x, 0f)
        assertTrue(placements[1].center.x > 60f)
        assertFalse(placements[1].overlaps)
    }

    @Test
    fun `least-overlap candidate is used when collision is unavoidable`() {
        val tinyViewport = ScreenRect(0f, 0f, 40f, 20f)
        val path = arrayOf(point(-10f, 10f), point(50f, 10f))
        val placements = layoutRouteBadges(
            tinyViewport,
            listOf(
                input("first", 40f, 20f, *path),
                input("second", 40f, 20f, *path),
            ),
            edgeMarginPx = 0f,
            badgeGapPx = 0f,
        )

        assertFalse(placements[0].overlaps)
        assertTrue(placements[1].overlaps)
        assertEquals(placements[0].center, placements[1].center)
    }

    @Test
    fun `routes outside the usable viewport receive no placement`() {
        val placements = layoutRouteBadges(
            viewport,
            listOf(input("route", 20f, 20f, point(-50f, -50f), point(-10f, -10f))),
        )

        assertTrue(placements.isEmpty())
    }

    @Test
    fun `a route hugging the edge is clamped inward so its badge remains visible`() {
        val placement = layoutRouteBadges(
            viewport,
            listOf(input("route", 40f, 20f, point(-50f, 2f), point(250f, 2f))),
            edgeMarginPx = 4f,
        ).single()

        assertEquals(24f, placement.center.x, 0f)
        assertEquals(14f, placement.center.y, 0f)
    }

    @Test
    fun `input order controls greedy priority`() {
        val path = arrayOf(point(-50f, 60f), point(250f, 60f))
        val placements = layoutRouteBadges(
            viewport,
            listOf(
                input("priority", 100f, 20f, *path),
                input("later", 40f, 20f, *path),
            ),
            edgeMarginPx = 0f,
        )

        assertEquals(listOf("priority", "later"), placements.map { it.routeId })
        assertEquals(50f, placements.first().center.x, 0f)
    }

    private fun input(
        id: String,
        width: Float,
        height: Float,
        vararg points: ScreenPoint,
    ) = RouteBadgeLayoutInput(id, ScreenSize(width, height), listOf(points.toList()))

    private fun point(x: Float, y: Float) = ScreenPoint(x, y)
}
