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
package org.onebusaway.android.api

import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.contract.StopsForRoute

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ports the wire coverage of the retired instrumented StopsForLocationTest / StopsForRouteRequestTest
 * onto the modernized map fetch: decodes the same fixtures and asserts the stops list + references
 * resolution (route/agency) and the stops-for-route stopGroupings + encoded polylines. The polyline
 * *decoding* (PolylineDecoder.decodeLine → Location) and DtoStop.getLocation are Android-bound and
 * exercised on-device + by PolylineDecoderTest.
 */
class StopsMapDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun stopsForLocationDecodesWithReferences() {
        val body = File("src/androidTest/res/raw/stops_for_location_downtown_seattle.json").readText()
        val data = json.decodeFromString<ObaEnvelope<ListWithReferences<StopReference>>>(body).requireData()

        assertTrue(data.list.isNotEmpty())
        val first = data.list[0]
        assertEquals("1_10230", first.id)
        // The stop's route + that route's agency resolve from the references pool.
        val route = data.references.route(first.routeIds[0])
        assertEquals("1_70", route?.id)
        assertEquals("Metro Transit", data.references.agency(route!!.agencyId)?.name)
    }

    @Test
    fun stopsForRouteDecodesPolylinesAndStops() {
        val body = File("src/androidTest/res/raw/stops_for_route_1_44.json").readText()
        val data = json.decodeFromString<ObaEnvelope<EntryWithReferences<StopsForRoute>>>(body).requireData()

        assertTrue(data.entry.stopGroupings.isNotEmpty())
        assertTrue(data.entry.polylines.isNotEmpty())
        assertTrue(data.entry.polylines[0].points.isNotEmpty())
        assertTrue(data.references.stops.isNotEmpty())
        assertNotNull(data.references.route("1_44"))
    }
}
