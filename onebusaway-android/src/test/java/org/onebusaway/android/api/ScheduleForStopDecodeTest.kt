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
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.StopSchedule

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports the legacy ScheduleForStopTest onto the modernized `schedule-for-stop` endpoint, walking the
 * route -> direction -> stop-time nesting and asserting the same anchors the instrumented test did
 * (stop id, route id, direction headsign, first trip id).
 */
class ScheduleForStopDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Mirrors the KCM stop 1_75403 schedule the retired instrumented test asserted against.
    private val body = """
        {
          "version": 2, "code": 200, "currentTime": 1343635200000, "text": "OK",
          "data": {
            "entry": {
              "stopId": "1_75403", "timeZone": "America/Los_Angeles", "date": 1343635200000,
              "stopRouteSchedules": [
                {
                  "routeId": "1_25",
                  "stopRouteDirectionSchedules": [
                    {
                      "tripHeadsign": "DOWNTOWN SEATTLE UNIVERSITY DISTRICT",
                      "scheduleStopTimes": [
                        { "tripId": "1_20969000", "arrivalTime": 1343663220000, "departureTime": 1343663220000 }
                      ]
                    }
                  ]
                }
              ]
            },
            "references": {}
          }
        }
    """.trimIndent()

    @Test
    fun decodesScheduleNesting() {
        val envelope: ObaEnvelope<EntryWithReferences<StopSchedule>> = json.decodeFromString(body)

        assertEquals(200, envelope.code)
        val schedule = envelope.data!!.entry
        assertEquals("1_75403", schedule.stopId)

        val routeSchedules = schedule.stopRouteSchedules
        assertTrue(routeSchedules.isNotEmpty())
        assertEquals("1_25", routeSchedules[0].routeId)

        val directions = routeSchedules[0].stopRouteDirectionSchedules
        assertTrue(directions.isNotEmpty())
        assertEquals("DOWNTOWN SEATTLE UNIVERSITY DISTRICT", directions[0].tripHeadsign)

        val stopTimes = directions[0].scheduleStopTimes
        assertTrue(stopTimes.isNotEmpty())
        assertEquals("1_20969000", stopTimes[0].tripId)
    }
}
