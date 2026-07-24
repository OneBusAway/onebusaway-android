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
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.Position
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.TripReference
import org.onebusaway.android.api.contract.TripStatus

/**
 * The wire→domain half of the #2012 fix: [StopArrivals.arrivals] — the list the ETA strip ultimately
 * renders — must expose at most one entry per `(tripId, serviceDate, stopSequence)`, because the strip
 * keys its `LazyRow` pills by exactly that triple and a duplicate key throws at measure time.
 *
 * The payload below is the real response that crashed the app: Everett Transit stop `97_256`,
 * 2026-07-24, where trips `97_108567` and `97_108568` each came back twice with two ordinary coach
 * ids matched to one block (so the block-id phantom rule of #1710 can't see the duplicate). The two
 * entries agreed on every predicted time and even on the interpolated `position`; only one of them
 * carried a real `lastKnownLocation`.
 *
 * [ArrivalDedupTest] covers the collapse rule itself; this test pins the wiring, so a refactor that
 * drops the dedup from this path fails here rather than on a rider's phone.
 */
class StopArrivalsDedupTest {

    @Test
    fun `collapses duplicate vehicle matches to one arrival per trip instance (issue 2012)`() {
        val arrivals = everettStop97_256().arrivals

        assertEquals(
            listOf("97_108567" to "97_737", "97_108568" to "97_149"),
            arrivals.map { it.tripId to it.vehicleId }
        )
    }

    @Test
    fun `surviving trip instances are unique (the ETA strip's LazyRow key invariant)`() {
        val instances = everettStop97_256().arrivals
            .map { Triple(it.tripId, it.serviceDate, it.stopSequence) }

        assertEquals(instances.distinct(), instances)
    }

    private companion object {
        const val SERVICE_DATE = 1_784_876_400_000L
        const val PREDICTED = 1_784_927_682_000L

        /** The four-entry response — two trip instances, each duplicated by a second coach id. */
        fun everettStop97_256() = StopArrivals(
            data = EntryWithReferences(
                entry = ArrivalsForStop(
                    stopId = "97_256",
                    arrivalsAndDepartures = listOf(
                        arrival(tripId = "97_108567", vehicleId = "97_737", located = true),
                        arrival(tripId = "97_108567", vehicleId = "97_143", located = false),
                        arrival(tripId = "97_108568", vehicleId = "97_149", located = true),
                        arrival(tripId = "97_108568", vehicleId = "97_317", located = false)
                    )
                ),
                references = References(
                    trips = listOf(
                        // Neither duplicate's vehicleId is the block id — the #1710 rule alone leaves
                        // both entries standing, which is what crashed the strip.
                        TripReference(id = "97_108567", routeId = "97_5", blockId = "97_119"),
                        TripReference(id = "97_108568", routeId = "97_5", blockId = "97_139")
                    )
                )
            ),
            currentTime = 1_784_926_038_080L,
            minutesAfter = 65
        )

        /** One wire arrival; [located] is the only field that differs within a duplicate pair. */
        fun arrival(tripId: String, vehicleId: String, located: Boolean) = ArrivalDeparture(
            routeId = "97_5",
            tripId = tripId,
            stopId = "97_256",
            routeShortName = "8",
            stopSequence = 23,
            serviceDate = SERVICE_DATE,
            vehicleId = vehicleId,
            predicted = true,
            scheduledArrivalTime = PREDICTED,
            predictedArrivalTime = PREDICTED,
            scheduledDepartureTime = PREDICTED,
            predictedDepartureTime = PREDICTED,
            tripStatus = TripStatus(
                activeTripId = tripId,
                predicted = true,
                serviceDate = SERVICE_DATE,
                status = "SCHEDULED",
                phase = "in_progress",
                vehicleId = vehicleId,
                // Both entries carry the same interpolated position; only the coach that actually
                // reported has a last-known location, so that is what tells them apart.
                position = Position(lat = 47.899057, lon = -122.214232),
                lastLocationUpdateTime = if (located) 1_784_926_069_000L else 0L,
                lastKnownLocation = if (located) {
                    Position(lat = 47.89638137817383, lon = -122.22335052490234)
                } else {
                    null
                }
            )
        )
    }
}
