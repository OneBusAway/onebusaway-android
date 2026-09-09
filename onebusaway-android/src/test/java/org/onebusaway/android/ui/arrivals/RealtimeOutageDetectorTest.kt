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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime

class RealtimeOutageDetectorTest {

    private fun arrival(
        routeId: String = "route-1",
        predicted: Boolean = false,
        tripId: String = "trip-1"
    ): ArrivalInfo {
        val scheduled = ServerTime(1_000_000L)
        val predictedTime = if (predicted) ServerTime(1_000_000L) else null
        val data = FakeArrivalData(
            predicted = predicted,
            predictedArrivalTime = predictedTime,
            scheduledArrivalTime = scheduled,
            routeId = routeId,
            tripId = tripId
        )
        return ArrivalInfo(
            context = null,
            data = data,
            now = ServerTime(1_000_000L),
            includeArrivalDepartureInStatusLabel = false
        )
    }

    @Test
    fun `empty arrivals list returns no outage`() {
        val result = detectRealtimeOutages(emptyList()) { "Metro" }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `agency with all predicted arrivals has no outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = true),
            arrival("r1", predicted = true),
            arrival("r1", predicted = true)
        )
        val result = detectRealtimeOutages(arrivals) { "Metro" }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `agency with mix of predicted and scheduled arrivals has no outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = true),
            arrival("r1", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) { "Metro" }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `agency with at least 3 scheduled-only arrivals flags outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = false),
            arrival("r2", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) { "Metro" }
        assertEquals(listOf(RealtimeOutage("Metro")), result)
    }

    @Test
    fun `agency with fewer than 3 scheduled-only arrivals does not flag outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) { "Metro" }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple agencies with only one affected flags only affected agency`() {
        val arrivals = listOf(
            // Metro: 3 arrivals, 0 predicted -> outage
            arrival("metro-1", predicted = false),
            arrival("metro-1", predicted = false),
            arrival("metro-2", predicted = false),
            // Sound Transit: 3 arrivals, 2 predicted -> no outage
            arrival("st-1", predicted = true),
            arrival("st-1", predicted = true),
            arrival("st-2", predicted = false),
            // Pierce Transit: 2 arrivals, 0 predicted -> below threshold, no outage
            arrival("pt-1", predicted = false),
            arrival("pt-1", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) {
            when {
                it.routeId.startsWith("metro") -> "King County Metro"
                it.routeId.startsWith("st") -> "Sound Transit"
                it.routeId.startsWith("pt") -> "Pierce Transit"
                else -> null
            }
        }
        assertEquals(listOf(RealtimeOutage("King County Metro")), result)
    }

    @Test
    fun `multiple affected agencies are sorted alphabetically`() {
        val arrivals = listOf(
            arrival("st-1", predicted = false),
            arrival("st-2", predicted = false),
            arrival("st-3", predicted = false),
            arrival("kcm-1", predicted = false),
            arrival("kcm-2", predicted = false),
            arrival("kcm-3", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) {
            if (it.routeId.startsWith("st")) "Sound Transit" else "King County Metro"
        }
        assertEquals(
            listOf(
                RealtimeOutage("King County Metro"),
                RealtimeOutage("Sound Transit")
            ),
            result
        )
    }

    @Test
    fun `null or blank agency name is ignored`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r2", predicted = false),
            arrival("r3", predicted = false)
        )
        val resultNull = detectRealtimeOutages(arrivals) { null }
        assertTrue(resultNull.isEmpty())

        val resultBlank = detectRealtimeOutages(arrivals) { "   " }
        assertTrue(resultBlank.isEmpty())
    }

    @Test
    fun `recovery with predicted arrival clears the outage`() {
        val outageArrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = false),
            arrival("r2", predicted = false)
        )
        assertEquals(1, detectRealtimeOutages(outageArrivals) { "Metro" }.size)

        // After refresh, predictions return for one or more trips
        val recoveredArrivals = listOf(
            arrival("r1", predicted = true),
            arrival("r1", predicted = false),
            arrival("r2", predicted = false)
        )
        assertTrue(detectRealtimeOutages(recoveredArrivals) { "Metro" }.isEmpty())
    }

    private data class FakeArrivalData(
        override val predicted: Boolean,
        override val predictedArrivalTime: ServerTime?,
        override val scheduledArrivalTime: ServerTime,
        override val routeId: String = "1_100",
        override val tripId: String = "1_trip",
        override val stopId: String = "1_82673",
        override val headsign: String? = "Downtown",
        override val shortName: String? = "230",
        override val routeLongName: String? = null,
        override val stopSequence: Int = 5,
        override val serviceDate: Long = 0L,
        override val vehicleId: String? = null,
        override val scheduledDepartureTime: ServerTime = ServerTime(0L),
        override val predictedDepartureTime: ServerTime? = null,
        override val status: Status? = null,
        override val frequency: FrequencyWindow? = null,
        override val situationIds: List<String> = emptyList(),
        override val historicalOccupancy: Occupancy? = null,
        override val predictedOccupancy: Occupancy? = null,
        override val hasTripStatus: Boolean = false,
        override val scheduleDeviation: Long = 0L,
        override val lastKnownLat: Double? = null,
        override val lastKnownLon: Double? = null,
        override val hasPlottableVehicle: Boolean = false
    ) : ArrivalData
}
