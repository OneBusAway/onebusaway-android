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
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime

/**
 * Unit tests for [collapseDuplicateTripInstances]. It reduces the OBA server's duplicate
 * vehicle-matched entries to one per `(tripId, serviceDate, stopSequence)` — the block-id "phantom"
 * (#1710 / onebusaway-application-modules#469) and the two-coach-ids-on-one-block shape (#2012) —
 * preferring the non-phantom, then the AVL-located entry, then feed order, and leaves genuinely
 * distinct trip instances alone.
 */
class ArrivalDedupTest {

    private companion object {
        const val TRIP = "1_801565550"
        const val BLOCK = "1_8110104" // block id of TRIP; the phantom's vehicleId equals this
        const val COACH = "1_7099" // a real coach number
        const val OTHER_COACH = "1_7033" // a second real coach matched to the same block
        val blockIds: (String) -> String? = { tripId -> if (tripId == TRIP) BLOCK else null }
    }

    @Test
    fun `collapses the phantom (vehicleId == block id) when a real sibling shares the trip instance`() {
        val real = arrival(vehicleId = COACH)
        val phantom = arrival(vehicleId = BLOCK)
        assertEquals(listOf(real), listOf(real, phantom).collapseDuplicateTripInstances(blockIds))
        assertEquals(listOf(real), listOf(phantom, real).collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `prefers the non-phantom over the AVL fix (block-id signal outranks position)`() {
        // The phantom happens to carry a location and the real coach hasn't reported one yet: the
        // exact block-id rule still decides, since it identifies the stand-in rather than inferring it.
        val real = arrival(vehicleId = COACH, hasAvl = false)
        val phantom = arrival(vehicleId = BLOCK, hasAvl = true)
        assertEquals(listOf(real), listOf(phantom, real).collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `collapses even with no AVL anywhere (block-id signal, not position, distinguishes them)`() {
        // A TripUpdates-only deployment: neither entry carries a last-known location, but the
        // phantom is still identifiable by vehicleId == block id.
        val real = arrival(vehicleId = COACH, hasAvl = false)
        val phantom = arrival(vehicleId = BLOCK, hasAvl = false)
        assertEquals(listOf(real), listOf(real, phantom).collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `collapses two ordinary coach ids on one block, keeping the located one (issue 2012)`() {
        // The shape that crashed the ETA strip: Everett Transit stop 97_256, trip 97_108567,
        // stopSequence 23, block 97_119, vehicles 97_737 (reported a fix) and 97_143 (never did).
        // Neither vehicleId is the block id, so the #1710 rule can't see this duplicate at all.
        val located = arrival(vehicleId = COACH, hasAvl = true)
        val notYetLocated = arrival(vehicleId = OTHER_COACH, hasAvl = false)
        assertEquals(
            listOf(located),
            listOf(located, notYetLocated).collapseDuplicateTripInstances(blockIds)
        )
        assertEquals(
            listOf(located),
            listOf(notYetLocated, located).collapseDuplicateTripInstances(blockIds)
        )
    }

    @Test
    fun `collapses when the block id can't be resolved (trip absent from references)`() {
        // Crash-safety can't depend on the references pool: with no block id resolvable, nothing is a
        // phantom, so the AVL tie-break decides and the instance still yields exactly one entry.
        val notYetLocated = arrival(vehicleId = BLOCK, hasAvl = false)
        val located = arrival(vehicleId = COACH, hasAvl = true)
        assertEquals(
            listOf(located),
            listOf(notYetLocated, located).collapseDuplicateTripInstances { null }
        )
    }

    @Test
    fun `falls back to feed order when neither preference discriminates`() {
        val first = arrival(vehicleId = COACH, hasAvl = true)
        val second = arrival(vehicleId = OTHER_COACH, hasAvl = true)
        assertEquals(listOf(first), listOf(first, second).collapseDuplicateTripInstances(blockIds))
        assertEquals(listOf(second), listOf(second, first).collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `keeps a lone phantom when no sibling shares its trip instance`() {
        val phantomOnly = listOf(arrival(vehicleId = BLOCK))
        assertEquals(phantomOnly, phantomOnly.collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `does not collapse across different trips`() {
        val realA = arrival(tripId = TRIP, vehicleId = COACH)
        val phantomB = arrival(tripId = "1_other", vehicleId = BLOCK)
        val input = listOf(realA, phantomB)
        assertEquals(input, input.collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `does not collapse a loop route's two genuine visits (same trip, different stopSequence)`() {
        val first = arrival(vehicleId = COACH, stopSequence = 5)
        val phantomFirst = arrival(vehicleId = BLOCK, stopSequence = 5)
        val second = arrival(vehicleId = COACH, stopSequence = 42)
        // The phantom collapses against the first visit; both real visits survive.
        assertEquals(
            listOf(first, second),
            listOf(first, phantomFirst, second).collapseDuplicateTripInstances(blockIds)
        )
    }

    @Test
    fun `does not collapse the same stop_time on two service dates`() {
        val today = arrival(vehicleId = COACH, serviceDate = 1_784_876_400_000L)
        val yesterday = arrival(vehicleId = COACH, serviceDate = 1_784_790_000_000L)
        val input = listOf(yesterday, today)
        assertEquals(input, input.collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `a duplicate-free list is returned untouched`() {
        val input = listOf(
            arrival(vehicleId = COACH, stopSequence = 5),
            arrival(vehicleId = OTHER_COACH, stopSequence = 42)
        )
        assertSame(input, input.collapseDuplicateTripInstances(blockIds))
    }

    @Test
    fun `every surviving trip instance is unique (the ETA strip's LazyRow key invariant)`() {
        val input = listOf(
            arrival(vehicleId = COACH, stopSequence = 5),
            arrival(vehicleId = BLOCK, stopSequence = 5),
            arrival(vehicleId = COACH, stopSequence = 23),
            arrival(vehicleId = OTHER_COACH, stopSequence = 23, hasAvl = false),
            arrival(tripId = "1_other", vehicleId = COACH, stopSequence = 23)
        )
        val instances = input.collapseDuplicateTripInstances(blockIds)
            .map { Triple(it.tripId, it.serviceDate, it.stopSequence) }
        assertEquals(instances.distinct(), instances)
        assertEquals(3, instances.size)
    }

    @Test
    fun `empty and singleton lists pass through unchanged`() {
        assertEquals(
            emptyList<ArrivalData>(),
            emptyList<ArrivalData>().collapseDuplicateTripInstances(blockIds)
        )
        val one = listOf(arrival(vehicleId = BLOCK))
        assertEquals(one, one.collapseDuplicateTripInstances(blockIds))
    }

    private fun arrival(
        tripId: String = TRIP,
        serviceDate: Long = 1_783_234_800_000L,
        stopSequence: Int = 15,
        vehicleId: String?,
        hasAvl: Boolean = true
    ): ArrivalData = FakeArrivalData(
        tripId = tripId,
        serviceDate = serviceDate,
        stopSequence = stopSequence,
        vehicleId = vehicleId,
        hasTripStatus = true,
        lastKnownLat = if (hasAvl) 47.615 else null,
        lastKnownLon = if (hasAvl) -122.317 else null
    )
}

/** Minimal [ArrivalData] stub; only trip-instance identity, vehicleId, and the AVL fix matter here. */
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
    override val scheduleDeviation: Long = 0L
) : ArrivalData
