/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.ui.tripplan.pinned

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.StreetMode
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.VehicleMode
import org.onebusaway.android.ui.tripplan.WalkPreference

/**
 * The stored request is a hand-written copy of [TripPlanParams], which is what makes it safe to rename
 * the form's own types — and also what makes it possible to drop a field on the way past. These cover
 * the round trip and the two ways a stored payload is allowed to be wrong.
 */
class PinnedTripQueryTest {

    @Test
    fun `every endpoint kind survives the round trip`() {
        val kinds = listOf(
            TripEndpoint.FreeText("half typed"),
            TripEndpoint.Geocoded("Pike Place Market", 47.60, -122.34, isTransit = false),
            TripEndpoint.Geocoded("Westlake Station", 47.61, -122.33, isTransit = true),
            TripEndpoint.AddressBook("Home", 47.62, -122.32),
            TripEndpoint.CurrentLocation(47.63, -122.31),
            TripEndpoint.MapPoint(47.64, -122.30)
        )

        kinds.forEach { endpoint ->
            val restored = roundTrip(params(from = endpoint))
            assertEquals("$endpoint did not survive the round trip", endpoint, restored?.from)
        }
    }

    @Test
    fun `an address-book endpoint with no coordinates survives as one`() {
        // A contacts pick may legitimately have no coordinates — the server geocodes it. Only MAP_POINT
        // structurally requires them, so this must not be mistaken for a corrupt payload.
        val endpoint = TripEndpoint.AddressBook("Somewhere", null, null)

        assertEquals(endpoint, roundTrip(params(from = endpoint))?.from)
    }

    @Test
    fun `every option of the request survives the round trip`() {
        val original = TripPlanParams(
            from = TripEndpoint.Geocoded("A", 1.0, 2.0),
            to = TripEndpoint.Geocoded("B", 3.0, 4.0),
            dateTimeMillis = 1_700_000_000_000,
            arriving = true,
            modes = TripModeSelection(VehicleMode.RAIL, StreetMode.BICYCLE),
            wheelchair = true,
            optimizeTransfers = true,
            maxWalkMeters = 1200.0,
            walkPreference = WalkPreference.MINIMUM,
            cyclingPreference = CyclingPreference.FLATTEST,
            bikePreference = BikePreference.MAXIMUM
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun `defaulted options are written down rather than left to be re-defaulted`() {
        // encodeDefaults is the setting that makes this true, and it lives in PinnedTripJson rather
        // than at any call site — so this is the test that notices if it goes away.
        val json = PinnedTripJson.encode(params().toPinnedQuery(departNow = false))

        assertTrue("walkPreference is a defaulted field", json.contains("walkPreference"))
        assertTrue("cyclingPreference is a defaulted field", json.contains("cyclingPreference"))
    }

    @Test
    fun `the depart-now anchor round trips, since the request itself cannot carry it`() {
        val encoded = PinnedTripJson.encode(params().toPinnedQuery(departNow = true))

        assertTrue(PinnedTripJson.decode(encoded)!!.departNow)
    }

    @Test
    fun `a map point with no coordinates is a corrupt payload, not a point at zero`() {
        val query = params().toPinnedQuery(departNow = false)
            .copy(from = PinnedEndpoint(PinnedEndpoint.Kind.MAP_POINT, lat = null, lon = null))

        assertNull(query.toParamsOrNull())
    }

    @Test
    fun `an option name this build does not know degrades to the neutral one`() {
        // A payload written by a newer build. Falling back is the only reading that cannot misroute;
        // failing the whole pin over one preference would be worse.
        val query = params().toPinnedQuery(departNow = false).copy(
            walkPreference = "SOMETHING_NEWER",
            vehicleMode = "HYPERLOOP"
        )

        val restored = query.toParamsOrNull()!!
        assertEquals(WalkPreference.MEDIUM, restored.walkPreference)
        assertEquals(VehicleMode.ALL_TRANSIT, restored.modes.vehicle)
    }

    @Test
    fun `malformed JSON decodes to null rather than throwing`() {
        assertNull(PinnedTripJson.decode("{not json"))
    }

    @Test
    fun `a field this build does not know is ignored rather than rejected`() {
        val encoded = PinnedTripJson.encode(params().toPinnedQuery(departNow = false))
            .replaceFirst("{", """{"somethingNewer":true,""")

        assertEquals(params(), PinnedTripJson.decode(encoded)?.toParamsOrNull())
    }

    private fun roundTrip(params: TripPlanParams): TripPlanParams? = PinnedTripJson.decode(PinnedTripJson.encode(params.toPinnedQuery(departNow = false)))?.toParamsOrNull()

    private fun params(
        from: TripEndpoint = TripEndpoint.Geocoded("A", 1.0, 2.0),
        to: TripEndpoint = TripEndpoint.Geocoded("B", 3.0, 4.0)
    ) = TripPlanParams(
        from = from,
        to = to,
        dateTimeMillis = 1_700_000_000_000,
        arriving = false,
        modes = TripModeSelection(),
        wheelchair = false,
        optimizeTransfers = false,
        maxWalkMeters = null
    )
}
