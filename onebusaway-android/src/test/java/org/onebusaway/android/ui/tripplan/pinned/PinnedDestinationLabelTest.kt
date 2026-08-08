/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.ui.tripplan.pinned

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.TripPlanParams

/** What the resume card calls the trip it offers to bring back. */
class PinnedDestinationLabelTest {

    @Test
    fun `the rider's own name for the place wins`() {
        val label = pinnedDestinationLabel(
            params(TripEndpoint.Geocoded("Pike Place Market", 47.60, -122.34)),
            itineraryEndingAt("1st Ave & Pike St")
        )

        assertEquals(PinnedLabel.Text("Pike Place Market"), label)
    }

    @Test
    fun `an end with no name of its own takes the one the plan stamped on it`() {
        // "My location" and a map point are only coordinates; the plan's terminal-place naming has
        // already reverse-geocoded them, and that address says far more than the fixed label would.
        val label = pinnedDestinationLabel(
            params(TripEndpoint.CurrentLocation(47.60, -122.34)),
            itineraryEndingAt("1st Ave & Pike St")
        )

        assertEquals(PinnedLabel.Text("1st Ave & Pike St"), label)
    }

    @Test
    fun `a map point the plan could not name falls back to the fixed label`() {
        val label = pinnedDestinationLabel(
            params(TripEndpoint.MapPoint(47.60, -122.34)),
            itineraryEndingAt(null)
        )

        assertEquals(PinnedLabel.Resource(R.string.trip_plan_map_location), label)
    }

    @Test
    fun `a current-location end the plan could not name falls back to the fixed label`() {
        val label = pinnedDestinationLabel(
            params(TripEndpoint.CurrentLocation(47.60, -122.34)),
            itineraryEndingAt("   ")
        )

        assertEquals(PinnedLabel.Resource(R.string.tripplanner_current_location), label)
    }

    private fun itineraryEndingAt(name: String?) = TripItinerary(
        legs = listOf(
            TripLeg(to = TripPlace(name = "Somewhere in the middle")),
            TripLeg(to = TripPlace(name = name))
        )
    )

    private fun params(to: TripEndpoint) = TripPlanParams(
        from = TripEndpoint.Geocoded("Home", 47.59, -122.33),
        to = to,
        dateTimeMillis = 0L,
        arriving = false,
        modes = TripModeSelection(),
        wheelchair = false,
        optimizeTransfers = false,
        maxWalkMeters = null
    )
}
