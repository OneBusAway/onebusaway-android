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
package org.onebusaway.android.ui.widget

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime

/** JVM tests for [buildWidgetSnapshot] and [WidgetArrivalSnapshot.isFor]. */
class WidgetArrivalSnapshotTest {

    // Minute-aligned, so an arrival N whole minutes out has an ETA of exactly N.
    private val now = ServerTime(28_333_334L * 60_000L)

    private fun at(minutesFromNow: Int, route: String = ROUTE_A) = FakeArrivalData(
        routeId = route,
        tripId = "trip_${route}_$minutesFromNow",
        scheduledArrivalTime = ServerTime(now.epochMs + minutesFromNow * 60_000L)
    )

    private fun build(arrivals: List<ArrivalData>, routes: Map<String, String> = mapOf(ROUTE_A to "A")) = buildWidgetSnapshot(
        stopId = STOP,
        routeShortNames = routes,
        arrivals = arrivals,
        serverNow = now,
        receivedAt = ElapsedTime(0L)
    )

    private fun WidgetArrivalSnapshot.Route.etas() = arrivals.map { ((it.displayTimeMs - now.epochMs) / 60_000L).toInt() }

    @Test
    fun `departed trips are dropped before they can crowd out upcoming ones`() {
        // bmander's repro: four long-departed trips used to fill the whole cache, leaving only -2.
        val snapshot = build(listOf(-5, -4, -3, -2, 1, 3).map { at(it) })

        assertEquals(listOf(-2, 1, 3), snapshot.routes.single().etas())
    }

    @Test
    fun `every upcoming arrival is kept so the row survives departures until the next fetch`() {
        val snapshot = build((1..8).map { at(it) })

        assertEquals((1..8).toList(), snapshot.routes.single().etas())
    }

    @Test
    fun `a route whose trips have all departed is kept as empty and sorted after routes with arrivals`() {
        val snapshot = build(
            listOf(at(-6, ROUTE_A), at(-4, ROUTE_A), at(7, ROUTE_B)),
            routes = mapOf(ROUTE_A to "A", ROUTE_B to "B")
        )

        assertEquals(listOf("B", "A"), snapshot.routes.map { it.shortName })
        assertTrue(snapshot.routes[1].arrivals.isEmpty())
    }

    @Test
    fun `a canceled trip is kept and marked, so it can't pass for an ordinary countdown`() {
        // bmander's probe: otherwise-identical canceled and running +5 min trips used to snapshot equal.
        val canceled = build(listOf(at(5).copy(status = Status.CANCELED))).routes.single().arrivals.single()
        val running = build(listOf(at(5))).routes.single().arrivals.single()

        assertTrue(canceled.isCanceled)
        assertFalse(running.isCanceled)
        assertEquals(running.copy(isCanceled = true), canceled)
    }

    @Test
    fun `snapshot is for the stop and routes it was fetched with, whatever the names`() {
        val snapshot = build(listOf(at(3)))

        assertTrue(snapshot.isFor(WidgetConfig(STOP, "Pine St", "Renamed widget", PUGET_SOUND, mapOf(ROUTE_A to "A renamed"))))
        assertFalse(snapshot.isFor(WidgetConfig("1_other", "Other", "Other", PUGET_SOUND, mapOf(ROUTE_A to "A"))))
        assertFalse(snapshot.isFor(WidgetConfig(STOP, "Pine St", "Pine St", PUGET_SOUND, mapOf(ROUTE_A to "A", ROUTE_B to "B"))))
        assertFalse(snapshot.isFor(WidgetConfig(STOP, "Pine St", "Pine St", PUGET_SOUND, mapOf(ROUTE_B to "B"))))
    }

    @Test
    fun `config identity survives the prefs round trip`() {
        val snapshot = build(listOf(at(3)), routes = mapOf(ROUTE_A to "A", ROUTE_B to "B"))
        val decoded = Json.decodeFromString(WidgetArrivalSnapshot.serializer(), Json.encodeToString(WidgetArrivalSnapshot.serializer(), snapshot))

        assertEquals(snapshot, decoded)
        assertTrue(decoded.isFor(WidgetConfig(STOP, "Pine St", "Pine St", PUGET_SOUND, mapOf(ROUTE_B to "B", ROUTE_A to "A"))))
    }

    private companion object {
        const val STOP = "1_75403"
        const val ROUTE_A = "1_100"
        const val ROUTE_B = "1_102"
        val PUGET_SOUND = WidgetDeployment(customApiUrl = null, regionId = 1L, displayName = "Puget Sound")
    }
}

/** Minimal scheduled-only [ArrivalData] stub; only route and time matter to the snapshot. */
private data class FakeArrivalData(
    override val routeId: String,
    override val tripId: String,
    override val scheduledArrivalTime: ServerTime,
    override val predicted: Boolean = false,
    override val predictedArrivalTime: ServerTime? = null,
    override val stopId: String = "1_75403",
    override val headsign: String? = "Downtown",
    override val shortName: String? = null,
    override val routeLongName: String? = null,
    override val stopSequence: Int = 5,
    override val serviceDate: Long = 0L,
    override val vehicleId: String? = null,
    override val scheduledDepartureTime: ServerTime = scheduledArrivalTime,
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
