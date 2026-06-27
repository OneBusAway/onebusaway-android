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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.io.elements.ObaSituation
import org.onebusaway.android.io.request.ObaArrivalInfoResponse
import org.onebusaway.android.testing.MainDispatcherRule

/** The arguments of a single [ArrivalsRepository.favoriteRoute] call, for assertions. */
private data class FavoriteRouteCall(
    val routeId: String,
    val headsign: String?,
    val stopId: String?,
    val shortName: String?,
    val longName: String?,
    val favorite: Boolean
)

private class FakeArrivalsRepository(
    var result: Result<ArrivalsData>,
    private var persistedFilter: Set<String> = emptySet()
) : ArrivalsRepository {

    val requestedMinutesAfter = mutableListOf<Int>()

    val requestedFilters = mutableListOf<Set<String>?>()

    var lastFavoriteSet: Pair<String, Boolean>? = null

    var lastFavoriteRoute: FavoriteRouteCall? = null

    var lastSetFilter: Set<String>? = null

    var hiddenAlertIds: List<String>? = null

    var showAllAlertsCalled = false

    override suspend fun getArrivals(
        stopId: String,
        minutesAfter: Int,
        routeFilter: Set<String>?
    ): Result<ArrivalsData> {
        requestedMinutesAfter.add(minutesAfter)
        requestedFilters.add(routeFilter)
        // Echo the effective filter back, like the real repo (persisted when the caller passes null)
        val effective = routeFilter ?: persistedFilter
        return result.map { it.copy(effectiveRouteFilter = effective) }
    }

    override suspend fun setStopFavorite(stopId: String, favorite: Boolean) {
        lastFavoriteSet = stopId to favorite
    }

    override suspend fun favoriteRoute(
        routeId: String,
        headsign: String?,
        stopId: String?,
        shortName: String?,
        longName: String?,
        favorite: Boolean
    ) {
        lastFavoriteRoute = FavoriteRouteCall(routeId, headsign, stopId, shortName, longName, favorite)
    }

    override suspend fun setRouteFilter(stopId: String, filter: Set<String>) {
        lastSetFilter = filter
        persistedFilter = filter
    }

    var lastSetStyle: Int? = null

    override suspend fun setArrivalStyle(style: Int) {
        lastSetStyle = style
    }

    override suspend fun hideAlerts(ids: List<String>) {
        hiddenAlertIds = ids
    }

    override suspend fun showAllAlerts() {
        showAllAlertsCalled = true
    }

    override fun situation(id: String): ObaSituation? = null

    override fun lastResponse(): ObaArrivalInfoResponse? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalsViewModelTest {

    // Unconfined so the derived `state` (a stateIn combine) recomputes eagerly — tests read
    // `state.value` synchronously right after an action, with no advanceUntilIdle in between.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun header(favorite: Boolean = false) =
        StopHeader("1_100", "Pine St & 3rd Ave", "S", favorite, routeCount = 4)

    private fun data(minutesAfter: Int = 65, isStale: Boolean = false, favorite: Boolean = false) =
        ArrivalsData(
            arrivals = emptyList(),
            header = header(favorite),
            minutesAfter = minutesAfter,
            style = 0,
            isStale = isStale,
            effectiveRouteFilter = emptySet(),
            actions = emptyMap(),
            activeAlerts = emptyList(),
            dbHiddenIds = emptySet(),
            routeFilterOptions = emptyList(),
            filteredRouteCount = 0,
            stopCode = null,
            stopLat = 0.0,
            stopLon = 0.0,
            stopUserName = null
        )

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = ArrivalsViewModel("1_100", false, FakeArrivalsRepository(Result.success(data())))

        assertEquals(ArrivalsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `refresh emits Content on success`() = runTest {
        val viewModel = ArrivalsViewModel("1_100", false, FakeArrivalsRepository(Result.success(data())))

        viewModel.refresh()

        val state = viewModel.state.value
        assertTrue(state is ArrivalsUiState.Content)
        assertEquals("Pine St & 3rd Ave", (state as ArrivalsUiState.Content).header.name)
    }

    @Test
    fun `refresh emits Error when there is no content and the load fails`() = runTest {
        val viewModel = ArrivalsViewModel(
            "1_100",
            false,
            FakeArrivalsRepository(Result.failure(IOException("No network")))
        )

        viewModel.refresh()

        assertEquals(ArrivalsUiState.Error("No network"), viewModel.state.value)
    }

    @Test
    fun `a failed poll keeps existing content instead of showing Error`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()
        assertTrue(viewModel.state.value is ArrivalsUiState.Content)

        repository.result = Result.failure(IOException("blip"))
        viewModel.refresh()

        assertTrue(viewModel.state.value is ArrivalsUiState.Content)
    }

    @Test
    fun `the stale flag flows through to the Content state`() = runTest {
        val viewModel = ArrivalsViewModel(
            "1_100",
            false,
            FakeArrivalsRepository(Result.success(data(isStale = true)))
        )

        viewModel.refresh()

        assertTrue((viewModel.state.value as ArrivalsUiState.Content).isStale)
    }

    @Test
    fun `load more widens the time window on the next request`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data(minutesAfter = 65)))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        viewModel.loadMore()
        advanceUntilIdle()

        // 65 (initial) then 125 (65 + 60 increment)
        assertEquals(listOf(65, 125), repository.requestedMinutesAfter)
    }

    @Test
    fun `toggle favorite optimistically updates the header and persists`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data(favorite = false)))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        viewModel.toggleFavorite()

        assertTrue((viewModel.state.value as ArrivalsUiState.Content).header.isFavorite)
        advanceUntilIdle()
        assertEquals("1_100" to true, repository.lastFavoriteSet)
    }

    private fun routeActions(isRouteFavorite: Boolean) = ArrivalActions(
        tripId = "t1",
        routeId = "1_5",
        headsign = "Downtown",
        stopId = "1_100",
        routeShortName = "5",
        routeLongName = "Fifth Ave",
        scheduleUrl = null,
        agencyName = null,
        blockId = null,
        isRouteFavorite = isRouteFavorite
    )

    @Test
    fun `requestRouteFavorite opens the dialog and favoriteRoute applies the choice and reloads`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        val actions = routeActions(isRouteFavorite = false)

        viewModel.requestRouteFavorite(actions)
        assertEquals(actions, viewModel.favoriteRequest.value)

        viewModel.favoriteRoute(actions, allStops = true)
        advanceUntilIdle()

        // The dialog request is cleared as soon as a choice is applied.
        assertEquals(null, viewModel.favoriteRequest.value)
        // "All stops" => null stopId, and favorite = !isRouteFavorite (starring an unstarred route).
        assertEquals(
            FavoriteRouteCall("1_5", "Downtown", null, "5", "Fifth Ave", true),
            repository.lastFavoriteRoute
        )
        // The write is followed by a reload (initial load + the post-favorite refresh).
        assertEquals(2, repository.requestedMinutesAfter.size)
    }

    @Test
    fun `favoriteRoute scoped to this stop keeps the stop id`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        val actions = routeActions(isRouteFavorite = true)

        viewModel.favoriteRoute(actions, allStops = false)
        advanceUntilIdle()

        // This-stop only => the stop id is kept, and favorite = false (unstarring a starred route).
        assertEquals(
            FavoriteRouteCall("1_5", "Downtown", "1_100", "5", "Fifth Ave", false),
            repository.lastFavoriteRoute
        )
    }

    @Test
    fun `the route filter is seeded from the provider on the first load`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()), persistedFilter = setOf("1_5"))
        val viewModel = ArrivalsViewModel("1_100", false, repository)

        viewModel.refresh() // first load: null lets the repo read the persisted filter
        viewModel.refresh() // second load: uses the seeded filter

        assertEquals(listOf<Set<String>?>(null, setOf("1_5")), repository.requestedFilters)
    }

    @Test
    fun `setRouteFilter persists the filter and reloads with it`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        viewModel.setRouteFilter(setOf("1_10"))
        advanceUntilIdle()

        assertEquals(setOf("1_10"), repository.lastSetFilter)
        assertEquals(setOf("1_10"), repository.requestedFilters.last())
    }

    @Test
    fun `showOnlyRoute narrows to that route, then clears when repeated`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        viewModel.showOnlyRoute("1_5")
        advanceUntilIdle()
        assertEquals(setOf("1_5"), repository.lastSetFilter)

        // Already showing only this route -> the toggle clears the filter
        viewModel.showOnlyRoute("1_5")
        advanceUntilIdle()
        assertEquals(emptySet<String>(), repository.lastSetFilter)
    }

    @Test
    fun `hideAllAlerts hides the currently shown alerts`() = runTest {
        val withAlerts = data().copy(
            activeAlerts = listOf(AlertItem("a1", "Reduced service", AlertSeverity.WARNING))
        )
        val repository = FakeArrivalsRepository(Result.success(withAlerts))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        viewModel.hideAllAlerts()
        advanceUntilIdle()

        assertEquals(listOf("a1"), repository.hiddenAlertIds)
    }

    @Test
    fun `hideAlert removes it from the shown list reactively, without re-fetching`() = runTest {
        val withAlerts = data().copy(
            activeAlerts = listOf(
                AlertItem("a1", "Reduced service", AlertSeverity.WARNING),
                AlertItem("a2", "Detour", AlertSeverity.INFO)
            )
        )
        val repository = FakeArrivalsRepository(Result.success(withAlerts))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()
        assertEquals(2, (viewModel.state.value as ArrivalsUiState.Content).alerts.size)

        viewModel.hideAlert("a1")

        // The shown list and hidden count update from the hidden-id source — no second load.
        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(listOf("a2"), content.alerts.map { it.id })
        assertEquals(1, content.hiddenAlertCount)
        assertEquals(1, repository.requestedMinutesAfter.size)
    }

    @Test
    fun `showHiddenAlerts reveals hidden alerts reactively, without re-fetching`() = runTest {
        val withHidden = data().copy(
            activeAlerts = listOf(AlertItem("a1", "Reduced service", AlertSeverity.WARNING)),
            dbHiddenIds = setOf("a1")
        )
        val repository = FakeArrivalsRepository(Result.success(withHidden))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()
        // Seeded as hidden from the DB: shown empty, counted hidden.
        assertEquals(0, (viewModel.state.value as ArrivalsUiState.Content).alerts.size)
        assertEquals(1, (viewModel.state.value as ArrivalsUiState.Content).hiddenAlertCount)

        viewModel.showHiddenAlerts()

        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(listOf("a1"), content.alerts.map { it.id })
        assertEquals(0, content.hiddenAlertCount)
        assertEquals(1, repository.requestedMinutesAfter.size)
    }

    @Test
    fun `collapseRouteFilter clears the filter when every route is selected`() {
        assertEquals(emptySet<String>(), collapseRouteFilter(setOf("a", "b", "c"), 3))
        assertEquals(setOf("a"), collapseRouteFilter(setOf("a"), 3))
    }
}
