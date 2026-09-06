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
package org.onebusaway.android.api.data

import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.ObaWebService
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.SituationReference
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.contract.TripReference

class ColocatedStopArrivalsTest {
    // Coordinates, names, directions and IDs from the Puget Sound API for #2286.
    private val metro = StopReference(
        id = "1_590",
        name = "3rd Ave & Pine St",
        direction = "NW",
        lat = 47.611137,
        lon = -122.338951,
        routeIds = listOf("1_100229")
    )
    private val express = metro.copy(id = "3_2479", routeIds = listOf("40_597"))
    private val opposite = metro.copy(id = "1_430", lat = 47.610897, lon = -122.338966, direction = "SE")

    @Test
    fun `opening either stop loads both feeds once with the same window and retains original IDs`() = runTest {
        for (selected in listOf(metro, express)) {
            val calls = mutableListOf<Pair<String, Int>>()
            val service = service { id, minutes ->
                calls += id to minutes
                response(if (id == metro.id) metro else express)
            }
            val result = service.arrivalsAtBoardingPoint(selected.id, 65)
            assertEquals(selected.id, result.stopId)
            assertEquals(selected.id, result.stop?.id)
            assertEquals(setOf(metro.id to 65, express.id to 65), calls.toSet())
            assertEquals(2, calls.size)
            assertEquals(setOf(metro.id, express.id), result.arrivals.map { it.stopId }.toSet())
            assertEquals(setOf("1_100229", "40_597"), result.arrivals.map { it.routeId }.toSet())
            assertEquals(1, result.arrivals.first { it.stopId == express.id }.directionId)
            assertEquals("Sound Transit", result.agencyName("40"))
            assertEquals("40_597", result.route("40_597")?.id)
            assertEquals("shape-3_2479", result.trip("trip-3_2479")?.shapeId)
            assertEquals(setOf("alert-1_590", "alert-3_2479"), result.situations().map { it.id }.toSet())
            assertEquals(1000L, result.currentTime)
            assertEquals(65, result.minutesAfter)
        }
    }

    @Test
    fun `nearby references do not include opposite stops or unrelated trip status stops`() {
        val data = response(metro).data!!
        val unlisted = metro.copy(id = "unlisted-trip-status-stop")
        val snapshot = StopArrivals(data.copy(references = data.references.copy(stops = data.references.stops + unlisted)), 1000, 65)
        assertEquals(listOf(express.id), snapshot.colocatedStopIds)
    }

    @Test
    fun `ordinary stop needs only its original request`() = runTest {
        var requests = 0
        val service = service { _, _ ->
            requests++
            val envelope = response(metro)
            envelope.copy(data = envelope.data!!.copy(entry = envelope.data.entry.copy(nearbyStopIds = listOf(opposite.id))))
        }
        assertEquals(listOf(metro.id), service.arrivalsAtBoardingPoint(metro.id, 65).arrivals.map { it.stopId })
        assertEquals(1, requests)
    }

    @Test
    fun `an empty primary still shows sibling arrivals`() = runTest {
        val service = service { id, _ ->
            if (id == metro.id) {
                val envelope = response(metro)
                envelope.copy(data = envelope.data!!.copy(entry = envelope.data.entry.copy(arrivalsAndDepartures = emptyList())))
            } else {
                response(express)
            }
        }
        val result = service.arrivalsAtBoardingPoint(metro.id, 65)
        assertTrue(result.hasArrivals)
        assertEquals(express.id, result.arrivals.single().stopId)
    }

    @Test
    fun `a sibling error fails the whole refresh instead of silently dropping its routes`() = runTest {
        val failure = IllegalArgumentException("sibling unavailable")
        val service = service { id, _ -> if (id == metro.id) response(metro) else throw failure }
        try {
            service.arrivalsAtBoardingPoint(metro.id, 65)
            fail("Expected sibling failure")
        } catch (e: IllegalArgumentException) {
            assertEquals(failure.message, e.message)
        }
    }

    @Test
    fun `cancellation during a sibling request propagates`() = runTest {
        val service = service { id, _ -> if (id == metro.id) response(metro) else throw CancellationException("cancelled") }
        try {
            service.arrivalsAtBoardingPoint(metro.id, 65)
            fail("Expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    private fun response(stop: StopReference): ObaEnvelope<EntryWithReferences<ArrivalsForStop>> = ObaEnvelope(
        code = 200,
        currentTime = 1000,
        data = EntryWithReferences(
            entry = ArrivalsForStop(
                stopId = stop.id,
                nearbyStopIds = listOf(metro.id, express.id, opposite.id, express.id, "missing"),
                arrivalsAndDepartures = listOf(ArrivalDeparture(stopId = stop.id, tripId = "trip-${stop.id}", routeId = stop.routeIds.single())),
                situationIds = listOf("alert-${stop.id}")
            ),
            references = References(
                stops = listOf(metro, express, opposite),
                routes = listOf(RouteReference(id = stop.routeIds.single(), agencyId = if (stop == express) "40" else "1")),
                agencies = listOf(AgencyReference(id = "40", name = "Sound Transit")),
                trips = listOf(TripReference(id = "trip-${stop.id}", routeId = stop.routeIds.single(), shapeId = "shape-${stop.id}", directionId = "1")),
                situations = listOf(SituationReference(id = "alert-${stop.id}"))
            )
        )
    )

    // Exercise the production suspend service boundary without a network or Android runtime.
    private fun service(answer: (String, Int) -> ObaEnvelope<EntryWithReferences<ArrivalsForStop>>): ObaWebService = Proxy.newProxyInstance(
        ObaWebService::class.java.classLoader,
        arrayOf(ObaWebService::class.java)
    ) { _, method, args ->
        check(method.name == "arrivalsAndDeparturesForStop")
        answer(args[0] as String, args[1] as Int)
    } as ObaWebService
}
