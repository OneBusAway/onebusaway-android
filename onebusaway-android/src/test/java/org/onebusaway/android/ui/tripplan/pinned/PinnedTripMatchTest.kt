/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.ui.tripplan.pinned

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripplan.StreetMode
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.VehicleMode

/**
 * Whether the plan on screen is the pinned one. Gets it wrong in one direction and the control offers
 * to pin a trip that already is; wrong in the other and a refresh silently adopts a different trip's
 * itineraries into the pin.
 */
class PinnedTripMatchTest {

    @Test
    fun `the same request is the same trip`() {
        assertTrue(pin().describesSameTripAs(params(), currentDepartNow = false))
    }

    @Test
    fun `a different destination is a different trip`() {
        assertFalse(pin().describesSameTripAs(params(to = "Ballard"), currentDepartNow = false))
    }

    @Test
    fun `a different mode is a different trip`() {
        val other = params().copy(modes = TripModeSelection(VehicleMode.RAIL, StreetMode.BICYCLE))

        assertFalse(pin().describesSameTripAs(other, currentDepartNow = false))
    }

    @Test
    fun `a different arrive-by choice is a different trip`() {
        assertFalse(pin().describesSameTripAs(params().copy(arriving = true), currentDepartNow = false))
    }

    @Test
    fun `two leave-now requests are the same trip however far apart they were submitted`() {
        // The stated exception, and the reason it exists: `dateTimeMillis` on a "leave now" request is
        // the moment it was *sent*, restamped on every re-plan. Comparing it would make refreshing a
        // pinned "leave now" trip read as a different trip and silently un-pin it.
        val later = params().copy(dateTimeMillis = params().dateTimeMillis + 5 * 60_000L)

        assertTrue(pin(departNow = true).describesSameTripAs(later, currentDepartNow = true))
    }

    @Test
    fun `two leave-now requests to different places are still different trips`() {
        // The companion to the exception above: the leave-now branch substitutes one field, not the whole
        // request. Without this, a match that returned true for any two leave-now plans would still pass.
        val elsewhere = params(to = "Ballard").copy(dateTimeMillis = params().dateTimeMillis + 5 * 60_000L)

        assertFalse(pin(departNow = true).describesSameTripAs(elsewhere, currentDepartNow = true))
    }

    @Test
    fun `a trip pinned to an instant is judged on that instant`() {
        val later = params().copy(dateTimeMillis = params().dateTimeMillis + 5 * 60_000L)

        assertFalse(pin(departNow = false).describesSameTripAs(later, currentDepartNow = false))
    }

    @Test
    fun `the same instant asked two different ways is two different trips`() {
        // One rider meant "leave now", the other meant "leave at 4:12". They coincide today and won't
        // tomorrow, so they are not the same standing intention.
        assertFalse(pin(departNow = true).describesSameTripAs(params(), currentDepartNow = false))
        assertFalse(pin(departNow = false).describesSameTripAs(params(), currentDepartNow = true))
    }

    private fun pin(departNow: Boolean = false) = PinnedTrip(
        params = params(),
        departNow = departNow,
        itineraries = listOf(TripItinerary()),
        selectedIndex = 0,
        pinnedAt = WallTime(0L)
    )

    private fun params(to: String = "Pike Place Market") = TripPlanParams(
        from = TripEndpoint.Geocoded("Home", 47.60, -122.33),
        to = TripEndpoint.Geocoded(to, 47.61, -122.34),
        dateTimeMillis = 1_700_000_000_000,
        arriving = false,
        modes = TripModeSelection(),
        wheelchair = false,
        optimizeTransfers = false,
        maxWalkMeters = null
    )
}
