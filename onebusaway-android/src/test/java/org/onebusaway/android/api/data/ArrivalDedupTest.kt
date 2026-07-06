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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime

/**
 * Unit tests for [collapseBlockIdPhantoms]. It drops the OBA server's block-id "phantom" duplicate
 * (an entry whose `vehicleId` is the trip's block id) when a real-vehicle sibling shares the trip
 * instance, and leaves everything else alone (#1710 / onebusaway-application-modules#469).
 */
class ArrivalDedupTest {

    private companion object {
        const val TRIP = "1_801565550"
        const val BLOCK = "1_8110104" // block id of TRIP; the phantom's vehicleId equals this
        const val COACH = "1_7099"    // a real coach number
        val blockIds: (String) -> String? = { tripId -> if (tripId == TRIP) BLOCK else null }
    }

    @Test
    fun `collapses the phantom (vehicleId == block id) when a real sibling shares the trip instance`() {
        val real = arrival(vehicleId = COACH)
        val phantom = arrival(vehicleId = BLOCK)
        assertEquals(listOf(real), listOf(real, phantom).collapseBlockIdPhantoms(blockIds))
        assertEquals(listOf(real), listOf(phantom, real).collapseBlockIdPhantoms(blockIds))
    }

    @Test
    fun `collapses even with no AVL anywhere (block-id signal, not position, distinguishes them)`() {
        // A TripUpdates-only deployment: neither entry carries a last-known location, but the
        // phantom is still identifiable by vehicleId == block id.
        val real = arrival(vehicleId = COACH, hasAvl = false)
        val phantom = arrival(vehicleId = BLOCK, hasAvl = false)
        assertEquals(listOf(real), listOf(real, phantom).collapseBlockIdPhantoms(blockIds))
    }

    @Test
    fun `keeps a genuine second vehicle that has not reported a position (real vehicleId, no AVL)`() {
        // Two real coaches on one trip instance (the rare genuine double); one hasn't reported GPS
        // yet. Neither vehicleId is the block id, so neither is a phantom — both survive.
        val located = arrival(vehicleId = COACH, hasAvl = true)
        val notYetLocated = arrival(vehicleId = "1_7033", hasAvl = false)
        val input = listOf(located, notYetLocated)
        assertEquals(input, input.collapseBlockIdPhantoms(blockIds))
    }

    @Test
    fun `keeps both when the block id can't be resolved (trip absent from references)`() {
        val a = arrival(vehicleId = BLOCK)
        val b = arrival(vehicleId = COACH)
        val input = listOf(a, b)
        assertEquals(input, input.collapseBlockIdPhantoms { null })
    }

    @Test
    fun `keeps a lone phantom when no non-phantom sibling survives it`() {
        val phantomOnly = listOf(arrival(vehicleId = BLOCK))
        assertEquals(phantomOnly, phantomOnly.collapseBlockIdPhantoms(blockIds))
    }

    @Test
    fun `does not collapse across different trips`() {
        val realA = arrival(tripId = TRIP, vehicleId = COACH)
        val phantomB = arrival(tripId = "1_other", vehicleId = BLOCK)
        val input = listOf(realA, phantomB)
        assertEquals(input, input.collapseBlockIdPhantoms(blockIds))
    }

    @Test
    fun `does not collapse a loop route's two genuine visits (same trip, different stopSequence)`() {
        val first = arrival(vehicleId = COACH, stopSequence = 5)
        val phantomFirst = arrival(vehicleId = BLOCK, stopSequence = 5)
        val second = arrival(vehicleId = COACH, stopSequence = 42)
        // The phantom collapses against the first visit; both real visits survive.
        assertEquals(
            listOf(first, second),
            listOf(first, phantomFirst, second).collapseBlockIdPhantoms(blockIds),
        )
    }

    @Test
    fun `empty and singleton lists pass through unchanged`() {
        assertEquals(emptyList<ArrivalData>(), emptyList<ArrivalData>().collapseBlockIdPhantoms(blockIds))
        val one = listOf(arrival(vehicleId = BLOCK))
        assertEquals(one, one.collapseBlockIdPhantoms(blockIds))
    }

    private fun arrival(
        tripId: String = TRIP,
        serviceDate: Long = 1_783_234_800_000L,
        stopSequence: Int = 15,
        vehicleId: String?,
        hasAvl: Boolean = true,
    ): ArrivalData = FakeArrivalData(
        tripId = tripId,
        serviceDate = serviceDate,
        stopSequence = stopSequence,
        vehicleId = vehicleId,
        hasTripStatus = true,
        lastKnownLat = if (hasAvl) 47.615 else null,
        lastKnownLon = if (hasAvl) -122.317 else null,
    )
}

/** Minimal [ArrivalData] stub; only trip-instance identity + vehicleId matter here. */
private data class FakeArrivalData(
    override val tripId: String,
    override val serviceDate: Long,
    override val stopSequence: Int,
    override val vehicleId: String?,
    override val hasTripStatus: Boolean,
    override val lastKnownLat: Double?,
    override val lastKnownLon: Double?,
    override val routeId: String = "1_100252",
    override val stopId: String = "1_13330",
    override val headsign: String? = "Interlaken Park Via 19th Ave",
    override val shortName: String? = "12",
    override val routeLongName: String? = null,
    override val predicted: Boolean = true,
    override val scheduledArrivalTime: ServerTime = ServerTime(0L),
    override val predictedArrivalTime: ServerTime = ServerTime(0L),
    override val scheduledDepartureTime: ServerTime = ServerTime(0L),
    override val predictedDepartureTime: ServerTime = ServerTime(0L),
    override val status: Status? = null,
    override val frequency: FrequencyWindow? = null,
    override val situationIds: List<String> = emptyList(),
    override val historicalOccupancy: Occupancy? = null,
    override val predictedOccupancy: Occupancy? = null,
    override val scheduleDeviation: Long = 0L,
) : ArrivalData
