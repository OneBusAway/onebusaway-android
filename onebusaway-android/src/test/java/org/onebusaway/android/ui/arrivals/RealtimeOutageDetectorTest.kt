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

/**
 * Unit tests for [detectRealtimeOutages].
 */
class RealtimeOutageDetectorTest {

    /**
     * Builds a minimal [ArrivalInfo] for outage detector tests.
     *
     * @param routeId the route identifier for the arrival
     * @param predicted whether realtime prediction is available
     * @param tripId the trip identifier
     * @return a constructed [ArrivalInfo] model
     */
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

    /**
     * Verifies that an empty arrivals list produces no outages.
     */
    @Test
    fun `empty arrivals list returns no outage`() {
        val result = detectRealtimeOutages(emptyList()) { OperatingAgency("1", "Metro") }
        assertTrue(result.isEmpty())
    }

    /**
     * Verifies that an agency with all predicted arrivals does not trigger an outage.
     */
    @Test
    fun `agency with all predicted arrivals has no outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = true),
            arrival("r1", predicted = true),
            arrival("r1", predicted = true)
        )
        val result = detectRealtimeOutages(arrivals) { OperatingAgency("1", "Metro") }
        assertTrue(result.isEmpty())
    }

    /**
     * Verifies that an agency with a mix of predicted and scheduled arrivals does not trigger an outage.
     */
    @Test
    fun `agency with mix of predicted and scheduled arrivals has no outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = true),
            arrival("r1", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) { OperatingAgency("1", "Metro") }
        assertTrue(result.isEmpty())
    }

    /**
     * Verifies that an agency with at least 3 scheduled-only arrivals triggers an outage.
     */
    @Test
    fun `agency with at least 3 scheduled-only arrivals flags outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = false),
            arrival("r2", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) { OperatingAgency("1", "Metro") }
        assertEquals(listOf(RealtimeOutage("1", "Metro")), result)
    }

    /**
     * Verifies that an agency with fewer than 3 scheduled-only arrivals does not trigger an outage.
     */
    @Test
    fun `agency with fewer than 3 scheduled-only arrivals does not flag outage`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) { OperatingAgency("1", "Metro") }
        assertTrue(result.isEmpty())
    }

    /**
     * Verifies that when multiple agencies serve a stop, only the affected agency is flagged.
     */
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
                it.routeId.startsWith("metro") -> OperatingAgency("1", "King County Metro")
                it.routeId.startsWith("st") -> OperatingAgency("40", "Sound Transit")
                it.routeId.startsWith("pt") -> OperatingAgency("3", "Pierce Transit")
                else -> null
            }
        }
        assertEquals(listOf(RealtimeOutage("1", "King County Metro")), result)
    }

    /**
     * Verifies that distinct agencies with the same display name do not have their arrivals
     * merged across agency IDs to falsely trigger an outage (regression test).
     */
    @Test
    fun `distinct agencies with the same display name do not merge arrivals to falsely trigger outage`() {
        val arrivals = listOf(
            // Agency 1: 2 arrivals, both scheduled (< 3 threshold)
            arrival("agency1-r1", predicted = false),
            arrival("agency1-r2", predicted = false),
            // Agency 2: 1 arrival, scheduled (< 3 threshold)
            arrival("agency2-r1", predicted = false)
        )
        val result = detectRealtimeOutages(arrivals) {
            when {
                it.routeId.startsWith("agency1") -> OperatingAgency("id-1", "Metro")
                it.routeId.startsWith("agency2") -> OperatingAgency("id-2", "Metro")
                else -> null
            }
        }
        // Neither agency has >= 3 arrivals, so neither should be flagged
        assertTrue(result.isEmpty())
    }

    /**
     * Verifies that distinct agencies with the same display name evaluate realtime predictions
     * independently so one agency's live data doesn't suppress another agency's outage.
     */
    @Test
    fun `distinct agencies with the same display name evaluate independently`() {
        val arrivals = listOf(
            // Agency 1: 3 arrivals, all scheduled (outage)
            arrival("agency1-r1", predicted = false),
            arrival("agency1-r2", predicted = false),
            arrival("agency1-r3", predicted = false),
            // Agency 2: 3 arrivals, all predicted (no outage)
            arrival("agency2-r1", predicted = true),
            arrival("agency2-r2", predicted = true),
            arrival("agency2-r3", predicted = true)
        )
        val result = detectRealtimeOutages(arrivals) {
            when {
                it.routeId.startsWith("agency1") -> OperatingAgency("id-1", "Metro")
                it.routeId.startsWith("agency2") -> OperatingAgency("id-2", "Metro")
                else -> null
            }
        }
        assertEquals(listOf(RealtimeOutage("id-1", "Metro")), result)
    }

    /**
     * Verifies that multiple affected agencies are sorted alphabetically by agency name.
     */
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
            if (it.routeId.startsWith("st")) {
                OperatingAgency("40", "Sound Transit")
            } else {
                OperatingAgency("1", "King County Metro")
            }
        }
        assertEquals(
            listOf(
                RealtimeOutage("1", "King County Metro"),
                RealtimeOutage("40", "Sound Transit")
            ),
            result
        )
    }

    /**
     * Verifies that null or blank agency IDs or names are ignored.
     */
    @Test
    fun `null or blank agency info is ignored`() {
        val arrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r2", predicted = false),
            arrival("r3", predicted = false)
        )
        val resultNull = detectRealtimeOutages(arrivals) { null }
        assertTrue(resultNull.isEmpty())

        val resultBlankName = detectRealtimeOutages(arrivals) { OperatingAgency("1", "   ") }
        assertTrue(resultBlankName.isEmpty())

        val resultBlankId = detectRealtimeOutages(arrivals) { OperatingAgency("   ", "Metro") }
        assertTrue(resultBlankId.isEmpty())
    }

    /**
     * Verifies that when realtime predictions recover on a subsequent poll, the outage clears.
     */
    @Test
    fun `recovery with predicted arrival clears the outage`() {
        val outageArrivals = listOf(
            arrival("r1", predicted = false),
            arrival("r1", predicted = false),
            arrival("r2", predicted = false)
        )
        assertEquals(1, detectRealtimeOutages(outageArrivals) { OperatingAgency("1", "Metro") }.size)

        // After refresh, predictions return for one or more trips
        val recoveredArrivals = listOf(
            arrival("r1", predicted = true),
            arrival("r1", predicted = false),
            arrival("r2", predicted = false)
        )
        assertTrue(detectRealtimeOutages(recoveredArrivals) { OperatingAgency("1", "Metro") }.isEmpty())
    }

    /**
     * Minimal [ArrivalData] test stub for constructing [ArrivalInfo].
     */
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
