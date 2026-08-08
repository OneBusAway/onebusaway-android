/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.PINNED_TRIP_GHOST_WIDTH_PROFILE
import org.onebusaway.android.map.render.ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.util.GeoPoint

/**
 * How the parked trip is drawn under an exploring map (#2053). The ghost has one job — say *which* trip
 * is waiting without competing with whatever the rider actually tapped — so what these pin is that it
 * keeps its colours and gives up everything that would make it loud.
 */
class PinnedTripGhostTest {

    private val points = listOf(GeoPoint(47.60, -122.33), GeoPoint(47.61, -122.34))

    @Test
    fun `the ghost keeps each leg's own colour`() {
        // The whole reason it is drawn at all: a grey trace would say a trip is pinned, not which one.
        val legs = listOf(line(0xFF0A5B3E.toInt()), line(0xFFB00020.toInt()))

        assertEquals(
            listOf(0xFF0A5B3E.toInt(), 0xFFB00020.toInt()),
            legs.asPinnedTripGhost().map { it.color }
        )
    }

    @Test
    fun `the ghost gives up every mark that would make it compete`() {
        val cased = RoutePolyline(
            0xFF0A5B3E.toInt(),
            points,
            directional = true,
            case = RouteLineCase.OUTLINE,
            startMark = RouteLineMark.BULB,
            endMark = RouteLineMark.BULB
        )

        val ghost = cased.let { listOf(it) }.asPinnedTripGhost().single()

        assertEquals(RouteLineCase.NONE, ghost.case)
        assertEquals(RouteLineMark.NONE, ghost.startMark)
        assertEquals(RouteLineMark.NONE, ghost.endMark)
        assertEquals(false, ghost.directional)
    }

    @Test
    fun `the ghost is thinner than an ordinary route line`() {
        assertTrue(
            "a ghost at or above a route line's weight would be the thing being read, not the reminder",
            PINNED_TRIP_GHOST_WIDTH_PROFILE.thicknessDp < ROUTE_LINE_WIDTH_PROFILE.thicknessDp
        )
        assertEquals(
            PINNED_TRIP_GHOST_WIDTH_PROFILE,
            listOf(line(0xFF0A5B3E.toInt())).asPinnedTripGhost().single().widthProfile
        )
    }

    private fun line(color: Int) = RoutePolyline(color, points)
}
