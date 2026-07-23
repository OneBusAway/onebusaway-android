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

import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.contract.TripReference
import org.onebusaway.android.api.data.StopArrivals
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.RouteFavorites
import org.onebusaway.android.database.oba.ServiceAlertDao
import org.onebusaway.android.database.oba.ServiceAlertRecord
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopFavoritesRepository
import org.onebusaway.android.database.oba.StopListRow
import org.onebusaway.android.database.oba.StopLocationRow
import org.onebusaway.android.database.oba.StopRecentRow
import org.onebusaway.android.database.oba.StopRecord
import org.onebusaway.android.database.oba.StopUserInfoMapRow
import org.onebusaway.android.database.oba.StopUserInfoRow
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime

/**
 * Tests for [DefaultArrivalsRepository]'s stale-fallback/CAS concurrency against the REAL class
 * (issue #1909) — the single-write [DefaultArrivalsRepository] `LastGood` holder, the
 * fresh-wins-over-stale `compareAndSet`, the elapsed-time re-projection of stale ETAs (#1612), and
 * the `lastLoaded()` trip derivation. The Android edges come in through the [ArrivalsDisplay] /
 * [ElapsedClock] seams; [ArrivalInfo] tolerates a null context, so the display fake below builds
 * real display models with real ETA math on the JVM.
 *
 * The interleaving tests hold the stale-fallback path *between* its `lastGood` read and its CAS by
 * gating the fake [StopDao.userInfo] (a suspension point inside `toData`) — the same
 * [CompletableDeferred] gate technique `ArrivalsViewModelTest` uses one layer up.
 */
class DefaultArrivalsRepositoryTest {

    private companion object {
        const val STOP_ID = "1_100"

        /** A server clock on a whole minute, so the floored-minutes ETA math asserts exactly. */
        const val T0 = 1_200_000_000_000L
    }

    // --- Fakes ------------------------------------------------------------------------------------

    /** Scripted [StopArrivalsDataSource]: answers with [respond] and records each requested window. */
    private class FakeStopArrivalsDataSource : StopArrivalsDataSource {
        val requestedMinutes = mutableListOf<Int>()
        lateinit var respond: (minutesAfter: Int) -> Result<StopArrivals>

        override suspend fun arrivals(stopId: String, minutesAfter: Int): Result<StopArrivals> {
            requestedMinutes.add(minutesAfter)
            return respond(minutesAfter)
        }
    }

    /**
     * A [StopDao] whose [userInfo] — a suspension point on every `toData` — can be gated one-shot:
     * the next call signals [userInfoEntered] and parks on [userInfoGate] until the test releases it,
     * consuming the gate so subsequent (e.g. concurrent fresh-load) calls pass straight through.
     */
    private class FakeStopDao : StopDao {
        val markStopUsedCalls = mutableListOf<String>()

        /** Rows inserted via [upsert] — the branch [StopDao.setFavoriteEnsuringRow] takes when the
         *  stop row is absent (this fake's [getStop] always returns null). */
        val upsertedStops = mutableListOf<StopRecord>()

        @Volatile
        var userInfoGate: CompletableDeferred<Unit>? = null
        val userInfoEntered = CompletableDeferred<Unit>()

        override suspend fun userInfo(stopId: String): StopUserInfoRow? {
            val gate = userInfoGate
            if (gate != null) {
                userInfoGate = null
                userInfoEntered.complete(Unit)
                gate.await()
            }
            return null
        }

        override suspend fun markStopUsed(
            id: String,
            code: String,
            name: String,
            direction: String,
            latitude: Double,
            longitude: Double,
            regionId: Long?,
            now: Long
        ) {
            markStopUsedCalls.add(id)
        }

        override suspend fun setFavorite(stopId: String, favorite: Int) {}
        override suspend fun userInfoMap(): List<StopUserInfoMapRow> = emptyList()
        override suspend fun location(stopId: String): StopLocationRow? = null
        override suspend fun nameForStop(stopId: String): String? = null
        override suspend fun getStop(stopId: String): StopRecord? = null
        override suspend fun upsert(stop: StopRecord) {
            upsertedStops.add(stop)
        }
        override fun recents(cutoff: Long, regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())
        override fun recentsForSearch(cutoff: Long, regionId: Long?): Flow<List<StopRecentRow>> = flowOf(emptyList())
        override fun starredByName(regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())
        override fun starredByFrequency(regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())
        override fun favoriteStopIds(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun markUnused(stopId: String) {}
        override suspend fun markAllUnused() {}
        override suspend fun clearAllFavorites() {}
    }

    private class FakeServiceAlertDao : ServiceAlertDao {
        override suspend fun insertIfAbsent(row: ServiceAlertRecord) {}
        override fun hideDecisions(): Flow<List<ServiceAlertRecord>> = flowOf(emptyList())
        override suspend fun updateMarkedReadTime(id: String, time: Long) {}
        override suspend fun updateHidden(id: String, hidden: Int) {}
        override suspend fun isHidden(id: String): Boolean = false
        override suspend fun setAllHidden(hidden: Int) {}
    }

    private class FakeRouteFavorites : RouteFavorites {
        override fun favoriteRouteIds(): Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setFavorite(
            routeId: String,
            shortName: String?,
            longName: String?,
            url: String?,
            favorite: Boolean
        ) {}
    }

    private object NoopImportGate : ImportGate {
        override suspend fun awaitReady() {}
        override fun start() {}
    }

    /**
     * Builds REAL [ArrivalInfo]s with a null context (labels degrade to empty strings; the ETA and
     * prediction math is untouched), and applies the production negative-ETA filter with
     * show-negative-arrivals off — so a stale re-projection observably *drops* an arrival whose time
     * has passed, making the CAS write to the derived map snapshot assertable.
     */
    private class FakeArrivalsDisplay : ArrivalsDisplay {
        override fun convert(
            arrivals: List<ArrivalData>,
            now: ServerTime,
            includeArrivalDepartureInStatusLabel: Boolean
        ): List<ArrivalInfo> = arrivals.map { ArrivalInfo(null, it, now, includeArrivalDepartureInStatusLabel) }
            .filter { it.eta >= 0 }

        override fun stopErrorMessage(code: Int): String = "stop-error-$code"
    }

    /** A manually-advanced monotonic clock, so the stale projection's delta is exact. */
    private class FakeElapsedClock : ElapsedClock {
        private var current = ElapsedTime(100_000L)
        override fun now(): ElapsedTime = current
        fun advance(duration: Duration) {
            current = ElapsedTime(current.ms + duration.inWholeMilliseconds)
        }
    }

    private fun repository(
        dataSource: FakeStopArrivalsDataSource,
        stopDao: FakeStopDao = FakeStopDao(),
        clock: FakeElapsedClock = FakeElapsedClock()
    ) = DefaultArrivalsRepository(
        regionRepository = FakeRegionRepository(),
        stopArrivals = dataSource,
        serviceAlertDao = FakeServiceAlertDao(),
        stopDao = stopDao,
        // The real shared owner over the same fake StopDao, so setStopFavorite exercises the actual
        // ensure-row delegation (#1996) rather than a stub.
        stopFavorites = StopFavoritesRepository(stopDao, FakeRegionRepository(), NoopImportGate),
        routeFavorites = FakeRouteFavorites(),
        importGate = NoopImportGate,
        preferences = FakePreferencesRepository(),
        display = FakeArrivalsDisplay(),
        elapsedClock = clock
    )

    // --- Fixtures ---------------------------------------------------------------------------------

    /** A real [StopArrivals] snapshot (wire fixtures, no Android): one arrival [arrivalAt] on a trip. */
    private fun snapshot(
        currentTime: Long = T0,
        tripId: String = "trip-A",
        shapeId: String = "shape-A",
        arrivalAt: Long = currentTime + 10 * 60_000L,
        hasArrivals: Boolean = true,
        minutesAfter: Int = 65
    ) = StopArrivals(
        data = EntryWithReferences(
            entry = ArrivalsForStop(
                stopId = STOP_ID,
                arrivalsAndDepartures = if (hasArrivals) {
                    listOf(
                        ArrivalDeparture(
                            routeId = "route-1",
                            tripId = tripId,
                            stopId = STOP_ID,
                            stopSequence = 3,
                            scheduledArrivalTime = arrivalAt,
                            scheduledDepartureTime = arrivalAt
                        )
                    )
                } else {
                    emptyList()
                }
            ),
            references = References(
                agencies = listOf(AgencyReference(id = "agency-1", name = "Metro")),
                stops = listOf(
                    StopReference(
                        id = STOP_ID,
                        name = "Pine St & 3rd Ave",
                        lat = 47.61,
                        lon = -122.33,
                        routeIds = listOf("route-1")
                    )
                ),
                routes = listOf(RouteReference(id = "route-1", shortName = "5", agencyId = "agency-1")),
                trips = listOf(TripReference(id = tripId, routeId = "route-1", shapeId = shapeId, directionId = "0"))
            )
        ),
        currentTime = currentTime,
        minutesAfter = minutesAfter
    )

    // --- Fresh loads ------------------------------------------------------------------------------

    @Test
    fun `a fresh load publishes a lastLoaded snapshot consistent with the displayed trips`() = runTest {
        val dataSource = FakeStopArrivalsDataSource()
        dataSource.respond = { Result.success(snapshot()) }
        val repository = repository(dataSource)
        assertNull(repository.lastLoaded())

        val data = repository.getArrivals(STOP_ID, 65).getOrThrow()

        assertEquals(10L, data.arrivals.single().eta)
        val loaded = repository.lastLoaded()!!
        assertEquals(STOP_ID, loaded.stop?.id)
        assertEquals(listOf("route-1"), loaded.routes?.map { it.id })
        assertTrue(loaded.hasArrivals)
        // The exact displayed trips, derived from the same data the drawer shows.
        assertEquals(
            setOf(FocusedTrip("trip-A", "route-1", "shape-A", null, directionId = 0)),
            loaded.focusedTrips
        )
    }

    @Test
    fun `the empty-window widen loop grows the window until arrivals appear`() = runTest {
        val dataSource = FakeStopArrivalsDataSource()
        dataSource.respond = { minutes ->
            Result.success(snapshot(hasArrivals = minutes >= 185, minutesAfter = minutes))
        }
        val repository = repository(dataSource)

        val data = repository.getArrivals(STOP_ID, 65).getOrThrow()

        assertEquals(listOf(65, 125, 185), dataSource.requestedMinutes)
        assertEquals(185, data.minutesAfter)
    }

    @Test
    fun `the stop is recorded once per session, not on every poll`() = runTest {
        val dataSource = FakeStopArrivalsDataSource()
        dataSource.respond = { Result.success(snapshot()) }
        val stopDao = FakeStopDao()
        val repository = repository(dataSource, stopDao = stopDao)

        repository.getArrivals(STOP_ID, 65).getOrThrow()
        repository.getArrivals(STOP_ID, 65).getOrThrow()

        assertEquals(listOf(STOP_ID), stopDao.markStopUsedCalls)
    }

    // --- Stop favoriting --------------------------------------------------------------------------

    @Test
    fun `setStopFavorite ensures the stop row exists instead of a bare no-op update`() = runTest {
        // The row is absent (this fake's getStop always returns null) — the pre-#1996 bare
        // stopDao.setFavorite UPDATE would have silently no-op'd. Delegating to StopFavoritesRepository
        // instead inserts the identity row with the flag already set, matching the map focus banner.
        val stopDao = FakeStopDao()
        val repository = repository(FakeStopArrivalsDataSource(), stopDao = stopDao)

        repository.setStopFavorite(
            stopId = STOP_ID,
            code = "577",
            name = "Pine St & 3rd Ave",
            latitude = 47.6,
            longitude = -122.3,
            favorite = true
        )

        val inserted = stopDao.upsertedStops.single()
        assertEquals(STOP_ID, inserted.id)
        assertEquals("577", inserted.code)
        assertEquals("Pine St & 3rd Ave", inserted.name)
        assertEquals(1, inserted.favorite)
    }

    // --- The stale-fallback path ------------------------------------------------------------------

    @Test
    fun `a failed refresh with no prior data fails with the display error message`() = runTest {
        val dataSource = FakeStopArrivalsDataSource()
        dataSource.respond = { Result.failure(ObaApiException(404)) }
        val repository = repository(dataSource)

        val result = repository.getArrivals(STOP_ID, 65)

        assertEquals("stop-error-404", result.exceptionOrNull()!!.message)
        assertNull(repository.lastLoaded())
    }

    @Test
    fun `a failed refresh serves the stale snapshot with ETAs projected forward by elapsed device time`() = runTest {
        val dataSource = FakeStopArrivalsDataSource()
        val clock = FakeElapsedClock()
        val repository = repository(dataSource, clock = clock)
        dataSource.respond = { Result.success(snapshot()) }
        // Arrival 10 minutes out at load time.
        assertEquals(10L, repository.getArrivals(STOP_ID, 65).getOrThrow().arrivals.single().eta)

        // 4 minutes of device time pass, then the refresh fails.
        clock.advance(4.minutes)
        dataSource.respond = { Result.failure(IOException("down")) }
        val stale = repository.getArrivals(STOP_ID, 65).getOrThrow()

        assertTrue(stale.isStale)
        // The last good server clock projected forward by exactly the elapsed device time (#1612).
        assertEquals(6L, stale.arrivals.single().eta)
        // The footnote window still names the boundary of the (old) data actually shown.
        assertEquals(ServerTime(T0) + 65.minutes, stale.windowEnd)
    }

    @Test
    fun `an uncontended stale re-projection refreshes the derived map snapshot`() = runTest {
        val dataSource = FakeStopArrivalsDataSource()
        val clock = FakeElapsedClock()
        val repository = repository(dataSource, clock = clock)
        dataSource.respond = { Result.success(snapshot()) }
        repository.getArrivals(STOP_ID, 65).getOrThrow()
        assertEquals(1, repository.lastLoaded()!!.focusedTrips.size)

        // The 10-minute arrival's time passes; the re-projection drops it (negative ETA), so the
        // CAS-published refresh of [loaded] must show the focused trip gone.
        clock.advance(11.minutes)
        dataSource.respond = { Result.failure(IOException("down")) }
        val stale = repository.getArrivals(STOP_ID, 65).getOrThrow()

        assertTrue(stale.isStale)
        assertTrue(stale.arrivals.isEmpty())
        val loaded = repository.lastLoaded()!!
        assertTrue(loaded.focusedTrips.isEmpty())
        // The rest of the holder is the same last good snapshot, unrolled.
        assertEquals(STOP_ID, loaded.stop?.id)
        assertTrue(loaded.hasArrivals)
    }

    @Test
    fun `a stale re-projection cannot roll back a concurrently published fresh load`() = runTest {
        val dataSource = FakeStopArrivalsDataSource()
        val stopDao = FakeStopDao()
        val repository = repository(dataSource, stopDao = stopDao)
        dataSource.respond = { Result.success(snapshot(tripId = "trip-A", shapeId = "shape-A")) }
        repository.getArrivals(STOP_ID, 65).getOrThrow()

        // Park the next load — a failing refresh, i.e. the stale-fallback path — inside toData,
        // after it has read the lastGood holder but before its compareAndSet.
        val gate = CompletableDeferred<Unit>()
        stopDao.userInfoGate = gate
        dataSource.respond = { Result.failure(IOException("down")) }
        val staleLoad = async(Dispatchers.Default) { repository.getArrivals(STOP_ID, 65) }
        stopDao.userInfoEntered.await()

        // While it is parked, a fresh load lands and publishes trip-B unconditionally.
        dataSource.respond = { Result.success(snapshot(tripId = "trip-B", shapeId = "shape-B")) }
        repository.getArrivals(STOP_ID, 65).getOrThrow()
        val freshTrips = setOf(FocusedTrip("trip-B", "route-1", "shape-B", null, directionId = 0))
        assertEquals(freshTrips, repository.lastLoaded()!!.focusedTrips)

        // Release the stale path: its CAS must lose, leaving the fresh holder standing.
        gate.complete(Unit)
        val stale = staleLoad.await().getOrThrow()

        // The caller still gets the stale-projected data (of the OLD snapshot it read)...
        assertTrue(stale.isStale)
        assertEquals("trip-A", stale.arrivals.single().tripId)
        // ...but the published holder is NOT rolled back to it.
        assertEquals(freshTrips, repository.lastLoaded()!!.focusedTrips)
    }
}
