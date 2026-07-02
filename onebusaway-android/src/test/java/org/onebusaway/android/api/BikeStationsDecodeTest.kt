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

import org.onebusaway.android.api.contract.BikeRentalStationsDto
import org.onebusaway.android.api.contract.toBikeRentalStations

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the OTP bike-rental decode + the mapping onto the OTP [BikeRentalStation] POJO the map
 * overlay consumes. The wire is plain JSON whose keys match the POJO field names; extra keys (e.g.
 * `networks`) must be ignored, not rejected.
 */
class BikeStationsDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesAndMapsStations() {
        val body = """
            {
              "stations": [
                {
                  "id": "bike_1", "name": "Pine & 5th",
                  "x": -122.334, "y": 47.611,
                  "bikesAvailable": 4, "spacesAvailable": 6,
                  "allowDropoff": true, "isFloatingBike": false, "realTimeData": true,
                  "networks": ["pronto"]
                },
                {
                  "id": "float_2", "name": "Floating bike",
                  "x": -122.30, "y": 47.62,
                  "bikesAvailable": 1, "spacesAvailable": 0,
                  "allowDropoff": false, "isFloatingBike": true, "realTimeData": false
                }
              ],
              "errorsByNetwork": {}
            }
        """.trimIndent()

        val dto = json.decodeFromString<BikeRentalStationsDto>(body)
        assertEquals(2, dto.stations.size)

        val stations = dto.toBikeRentalStations()
        assertEquals(2, stations.size)

        val first = stations[0]
        assertEquals("bike_1", first.id)
        assertEquals("Pine & 5th", first.name)
        assertEquals(-122.334, first.x, 1e-6)
        assertEquals(47.611, first.y, 1e-6)
        assertEquals(4, first.bikesAvailable)
        assertEquals(6, first.spacesAvailable)
        assertTrue(first.allowDropoff)
        assertEquals(false, first.isFloatingBike)
        assertTrue(first.realTimeData)

        assertTrue(stations[1].isFloatingBike)
    }

    /**
     * Decodes the real OTP Tampa bike-rental fixture and maps it onto [BikeRentalStation], porting
     * the assertions from the retired live-network `BikeStationRequestTest`. Note the OTP server
     * returns ids wrapped in literal quote characters (`"bike_3566"`), preserved verbatim.
     */
    @Test
    fun decodesTampaFixture() {
        val body = File("src/androidTest/res/raw/bike_rental_tampa_all.json").readText()
        val stations = json.decodeFromString<BikeRentalStationsDto>(body).toBikeRentalStations()

        assertEquals(133, stations.size)
        stations.forEach { assertNotNull(it.name) }

        val first = stations[0]
        val precision = 0.000001
        assertEquals("\"bike_3566\"", first.id)
        assertEquals("B-1165", first.name)
        assertEquals(-82.40730666666667, first.x, precision)
        assertEquals(28.066505, first.y, precision)
        assertEquals(1, first.bikesAvailable)
        assertEquals(0, first.spacesAvailable)
        assertEquals(false, first.allowDropoff)
        assertEquals(true, first.isFloatingBike)
        assertEquals(true, first.realTimeData)
    }
}
