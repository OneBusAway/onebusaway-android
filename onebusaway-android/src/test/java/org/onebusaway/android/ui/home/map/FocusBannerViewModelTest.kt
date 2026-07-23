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
package org.onebusaway.android.ui.home.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.RouteFavorites
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopFavoritesRepository
import org.onebusaway.android.database.oba.StopListRow
import org.onebusaway.android.database.oba.StopLocationRow
import org.onebusaway.android.database.oba.StopRecentRow
import org.onebusaway.android.database.oba.StopRecord
import org.onebusaway.android.database.oba.StopUserInfoMapRow
import org.onebusaway.android.database.oba.StopUserInfoRow
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.util.GeoPoint

@OptIn(ExperimentalCoroutinesApi::class)
class FocusBannerViewModelTest {

    // Unconfined so the derived StateFlows (favoriteStopIds, stopFavoritesReady) recompute eagerly.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

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
     * A [StopDao] backing the favorite flag in-memory, whose FIRST write can be parked mid-flight
     * (via [firstWriteGate]) to force the out-of-order completion the #2001 race needs: a slow star
     * write that would otherwise land after a later, faster unstar.
     */
    private class FakeStopDao : FakeStopDaoBase() {
        val stored = MutableStateFlow<Set<String>>(emptySet())
        var firstWriteGate: CompletableDeferred<Unit>? = null
        val firstWriteEntered = CompletableDeferred<Unit>()
        private var writes = 0

        override suspend fun setFavoriteEnsuringRow(identity: StopRecord, favorite: Int) {
            if (writes++ == 0) {
                firstWriteEntered.complete(Unit)
                firstWriteGate?.await()
            }
            stored.update { if (favorite == 1) it + identity.id else it - identity.id }
        }

        override fun favoriteStopIds(): Flow<List<String>> = stored.map { it.toList() }
    }

    @Test
    fun `a fast star then unstar leaves both the store and the overlay unstarred`() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeStopDao().apply { firstWriteGate = CompletableDeferred() }
        val stopFavorites = StopFavoritesRepository(dao, FakeRegionRepository(), NoopImportGate)
        val viewModel = FocusBannerViewModel(FakeRouteFavorites(), stopFavorites)
        advanceUntilIdle()
        // The banner ignores taps until the persisted set is known.
        assertTrue(viewModel.stopFavoritesReady.value)

        val stop = FocusedStop("1_100", "Pine St & 3rd Ave", "577", GeoPoint(47.6, -122.3))

        // Tap 1: star. Its write parks mid-flight (a slow write).
        viewModel.toggleStopFavorite(stop)
        dao.firstWriteEntered.await()
        // Tap 2: unstar, while the star write is still parked. Without per-id serialization this write
        // would complete first and the parked star would land last, leaving the store starred.
        viewModel.toggleStopFavorite(stop)

        // The optimistic overlay already reflects the last tap.
        assertEquals(emptySet<String>(), viewModel.favoriteStopIds.value)

        // Release the parked star write.
        dao.firstWriteGate!!.complete(Unit)
        advanceUntilIdle()

        // The persisted store converged to the last tap — the writes did not reorder.
        assertEquals(emptySet<String>(), dao.stored.value)
        // And the overlay reconciled against it (no override left stuck contradicting the store).
        assertEquals(emptySet<String>(), viewModel.favoriteStopIds.value)
    }
}

/**
 * The unused half of the [StopDao] surface, kept out of the way so [FocusBannerViewModelTest.FakeStopDao]
 * shows only the two methods the favorite path touches.
 */
private abstract class FakeStopDaoBase : StopDao {
    override suspend fun userInfo(stopId: String): StopUserInfoRow? = null
    override suspend fun setFavorite(stopId: String, favorite: Int) {}
    override suspend fun userInfoMap(): List<StopUserInfoMapRow> = emptyList()
    override suspend fun location(stopId: String): StopLocationRow? = null
    override suspend fun nameForStop(stopId: String): String? = null
    override suspend fun getStop(stopId: String): StopRecord? = null
    override suspend fun upsert(stop: StopRecord) {}
    override suspend fun markStopUsed(
        id: String,
        code: String,
        name: String,
        direction: String,
        latitude: Double,
        longitude: Double,
        regionId: Long?,
        now: Long
    ) {}
    override fun recents(cutoff: Long, regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())
    override fun recentsForSearch(cutoff: Long, regionId: Long?): Flow<List<StopRecentRow>> = flowOf(emptyList())
    override fun starredByName(regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())
    override fun starredByFrequency(regionId: Long?): Flow<List<StopListRow>> = flowOf(emptyList())
    override fun favoriteStopIds(): Flow<List<String>> = flowOf(emptyList())
    override suspend fun markUnused(stopId: String) {}
    override suspend fun markAllUnused() {}
    override suspend fun clearAllFavorites() {}
}
