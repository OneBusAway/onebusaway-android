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

import org.onebusaway.android.api.data.toRouteStopGroups

import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.StopGroup
import org.onebusaway.android.api.contract.StopGroupName
import org.onebusaway.android.api.contract.StopGrouping
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.contract.StopsForRoute

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic coverage for [toRouteStopGroups]: group-name extraction from the nested name object,
 * stop resolution from the references pool (adapted to ObaStop), and skipping ids with no stop.
 */
class RouteStopGroupsMapperTest {

    @Test
    fun resolvesStopsPerDirectionAndSkipsUnknownIds() {
        val data = EntryWithReferences(
            entry = StopsForRoute(
                stopGroupings = listOf(
                    StopGrouping(
                        stopGroups = listOf(
                            StopGroup(
                                name = StopGroupName(names = listOf("Mount Baker Transit Center")),
                                stopIds = listOf("1_a", "1_missing", "1_b")
                            )
                        )
                    )
                )
            ),
            references = References(
                stops = listOf(
                    StopReference(id = "1_a", name = "Spring St & 3rd Ave", direction = "NE",
                        lat = 47.6, lon = -122.33),
                    StopReference(id = "1_b", name = "Pine St", direction = null,
                        lat = 47.61, lon = -122.34),
                )
            )
        )

        val groups = data.toRouteStopGroups()

        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals("Mount Baker Transit Center", group.name)
        // 1_missing is dropped (no stop in references); order is preserved.
        assertEquals(listOf("1_a", "1_b"), group.stops.map { it.id })
        assertEquals("Spring St & 3rd Ave", group.stops[0].name)
        assertEquals("NE", group.stops[0].direction)
        assertEquals(47.6, group.stops[0].latitude, 0.0)
        // null wire direction stays null on the model stop (the UI normalizes it).
        assertNull(group.stops[1].direction)
    }

    @Test
    fun flattensMultipleGroupingsAndGroups() {
        val group = StopGroup(name = StopGroupName(names = listOf("Dir")), stopIds = emptyList())
        val data = EntryWithReferences(
            entry = StopsForRoute(
                stopGroupings = listOf(
                    StopGrouping(stopGroups = listOf(group, group)),
                    StopGrouping(stopGroups = listOf(group)),
                )
            ),
            references = References()
        )

        assertEquals(3, data.toRouteStopGroups().size)
    }

    @Test
    fun decodesRealWireAndReadsGroupNameFromNamesArray() {
        // The group name comes from the `names` array (like legacy names[0]), NOT the non-standard
        // scalar `name` some servers also emit. The fixture sets them differently to prove it.
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val body = """
            {
              "version": 2, "code": 200, "currentTime": 1700000000000, "text": "OK",
              "data": {
                "entry": {
                  "routeId": "1_100275",
                  "stopIds": ["1_a"],
                  "polylines": [],
                  "stopGroupings": [
                    { "type": "direction", "ordered": true, "stopGroups": [
                      { "id": "0",
                        "name": { "name": "WRONG-scalar", "names": ["To Downtown"], "type": "destination" },
                        "stopIds": ["1_a"], "polylines": [], "subGroups": [] }
                    ] }
                  ]
                },
                "references": {
                  "stops": [
                    { "id": "1_a", "name": "Spring St & 3rd Ave", "direction": "NE",
                      "lat": 47.6, "lon": -122.33, "code": "101", "locationType": 0 }
                  ]
                }
              }
            }
        """.trimIndent()

        val envelope: ObaEnvelope<EntryWithReferences<StopsForRoute>> = json.decodeFromString(body)
        val groups = envelope.data!!.toRouteStopGroups()

        assertEquals(1, groups.size)
        assertEquals("To Downtown", groups[0].name)
        assertEquals(listOf("1_a"), groups[0].stops.map { it.id })
    }
}
