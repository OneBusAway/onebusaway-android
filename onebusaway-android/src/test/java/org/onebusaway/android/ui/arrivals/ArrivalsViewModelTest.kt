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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
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
    private var persistedFilter: Set<String> = emptySet(),
    initialHideState: AlertHideState = AlertHideState()
) : ArrivalsRepository {

    val requestedMinutesAfter = mutableListOf<Int>()

    val requestedFilters = mutableListOf<Set<String>?>()

    var lastFavoriteSet: Pair<String, Boolean>? = null

    var lastFavoriteRoute: FavoriteRouteCall? = null

    var lastSetFilter: Set<String>? = null

    var hiddenAlertIds: List<String>? = null

    var shownAlertIds: List<String>? = null

    /** The DB's hide/show truth — the single source the ViewModel derives hidden state from.
     *  [hideAlerts]/[showAlerts] mutate it (as the real ContentProvider writes would), and a test can
     *  mutate it directly to stand in for a hide/un-hide from another surface (the alert dialog). */
    val hideState = MutableStateFlow(initialHideState)

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

    override fun alertHideState(): Flow<AlertHideState> = hideState

    override suspend fun hideAlerts(ids: List<String>) {
        hiddenAlertIds = ids
        hideState.update { it.copy(decisions = it.decisions + ids.associateWith { true }) }
    }

    override suspend fun showAlerts(ids: List<String>) {
        shownAlertIds = ids
        hideState.update { it.copy(decisions = it.decisions + ids.associateWith { false }) }
    }

    override fun alertDetails(id: String): AlertDetails? = null

    override fun lastLoaded(): ArrivalsLoaded? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalsViewModelTest {

    // Unconfined so the derived `state` (a stateIn combine) recomputes eagerly — tests read
    // `state.value` synchronously right after an action, with no advanceUntilIdle in between.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun header(favorite: Boolean = false) =
        StopHeader("1_100", "Pine St & 3rd Ave", "S", favorite, routeCount = 4)

    private fun data(
        minutesAfter: Int = 65,
        isStale: Boolean = false,
        favorite: Boolean = false,
        hideAlertsByDefault: Boolean = false
    ) =
        ArrivalsData(
            arrivals = emptyList(),
            header = header(favorite),
            minutesAfter = minutesAfter,
            style = 0,
            isStale = isStale,
            effectiveRouteFilter = emptySet(),
            actions = emptyMap(),
            activeAlerts = emptyList(),
            hideAlertsByDefault = hideAlertsByDefault,
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

    private fun alert(
        contentId: String,
        situationId: String,
        situationIds: Set<String> = setOf(situationId),
        summary: String = "Reduced service",
        severity: AlertSeverity = AlertSeverity.WARNING
    ) = AlertItem(contentId, situationId, situationIds, summary, severity)

    @Test
    fun `hideAllAlerts hides the currently shown alerts`() = runTest {
        val withAlerts = data().copy(activeAlerts = listOf(alert(contentId = "c1", situationId = "a1")))
        val repository = FakeArrivalsRepository(Result.success(withAlerts))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        viewModel.hideAllAlerts()
        advanceUntilIdle()

        assertEquals(listOf("a1"), repository.hiddenAlertIds)
    }

    @Test
    fun `hideAlert removes it from the shown list reactively, without re-fetching`() = runTest {
        val alert1 = alert(contentId = "c1", situationId = "a1")
        val withAlerts = data().copy(
            activeAlerts = listOf(alert1, alert(contentId = "c2", situationId = "a2", summary = "Detour"))
        )
        val repository = FakeArrivalsRepository(Result.success(withAlerts))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()
        assertEquals(2, (viewModel.state.value as ArrivalsUiState.Content).alerts.size)

        viewModel.hideAlert(alert1)

        // The shown list and hidden count update from the hidden-id flow — no second load.
        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(listOf("a2"), content.alerts.map { it.situationId })
        assertEquals(1, content.hiddenAlertCount)
        assertEquals(1, repository.requestedMinutesAfter.size)
    }

    @Test
    fun `hideAlert persists every id in the content group, not just the representative`() = runTest {
        // The feed serves the same alert under two live ids; hiding must write both so the hide holds
        // as the feed rotates which id leads the group (the #1593 restart-durability case).
        val grouped = alert(contentId = "c1", situationId = "a1", situationIds = setOf("a1", "a1b"))
        val repository = FakeArrivalsRepository(Result.success(data().copy(activeAlerts = listOf(grouped))))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()

        viewModel.hideAlert(grouped)
        advanceUntilIdle()

        assertEquals(setOf("a1", "a1b"), repository.hiddenAlertIds?.toSet())
    }

    @Test
    fun `showHiddenAlerts reveals hidden alerts reactively, without re-fetching`() = runTest {
        val withAlert = data().copy(activeAlerts = listOf(alert(contentId = "c1", situationId = "a1")))
        // The DB already reports a1 hidden.
        val repository = FakeArrivalsRepository(
            Result.success(withAlert),
            initialHideState = AlertHideState(mapOf("a1" to true))
        )
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()
        // Seeded as hidden from the DB: shown empty, counted hidden.
        assertEquals(0, (viewModel.state.value as ArrivalsUiState.Content).alerts.size)
        assertEquals(1, (viewModel.state.value as ArrivalsUiState.Content).hiddenAlertCount)

        viewModel.showHiddenAlerts()

        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(listOf("a1"), content.alerts.map { it.situationId })
        assertEquals(0, content.hiddenAlertCount)
        assertEquals(1, repository.requestedMinutesAfter.size)
        // The reveal must persist to the DB (records the active alert ids as shown).
        assertEquals(listOf("a1"), repository.shownAlertIds)
    }

    @Test
    fun `an un-hide written to the DB elsewhere is reflected with no ViewModel action or re-fetch`() = runTest {
        // Regression for #1593 finding #1: the alert dialog's Undo writes hidden=0 straight to the DB,
        // outside the ViewModel. With the DB as the single observed source, that un-hide must surface
        // on the screen — the old in-memory mirror could only ever add hides, so it stayed hidden.
        val withAlert = data().copy(activeAlerts = listOf(alert(contentId = "c1", situationId = "a1")))
        val repository = FakeArrivalsRepository(
            Result.success(withAlert),
            initialHideState = AlertHideState(mapOf("a1" to true))
        )
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()
        assertEquals(0, (viewModel.state.value as ArrivalsUiState.Content).alerts.size)

        // The alert dialog's Undo records the alert shown directly in the DB.
        repository.hideState.value = AlertHideState(mapOf("a1" to false))

        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(listOf("a1"), content.alerts.map { it.situationId })
        assertEquals(0, content.hiddenAlertCount)
        assertEquals(1, repository.requestedMinutesAfter.size)
    }

    @Test
    fun `a hide holds across a refresh because the DB is the single source`() = runTest {
        val alert1 = alert(contentId = "c1", situationId = "a1")
        val repository = FakeArrivalsRepository(Result.success(data().copy(activeAlerts = listOf(alert1))))
        val viewModel = ArrivalsViewModel("1_100", false, repository)
        viewModel.refresh()
        viewModel.hideAlert(alert1)
        assertEquals(0, (viewModel.state.value as ArrivalsUiState.Content).alerts.size)

        // A poll reloads the snapshot; the hide is in the DB, so it survives with no reconciliation.
        viewModel.refresh()

        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(0, content.alerts.size)
        assertEquals(1, content.hiddenAlertCount)
    }

    @Test
    fun `with hide-all-alerts on, a brand-new alert is hidden on first load with no DB write`() = runTest {
        // Regression for #1593 finding #2: the "hide all alerts" preference used to be applied via a
        // DB insert on load that raced the snapshot, so a new alert flashed visible for a frame. The
        // preference is now a pure input to the derivation — the alert is hidden the instant it's
        // derived, and nothing is written on load, so there is no write for the snapshot to race.
        val newAlert = alert(contentId = "c1", situationId = "a1")
        val repository = FakeArrivalsRepository(
            Result.success(data(hideAlertsByDefault = true).copy(activeAlerts = listOf(newAlert)))
        )
        val viewModel = ArrivalsViewModel("1_100", false, repository)

        viewModel.refresh()

        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(0, content.alerts.size)
        assertEquals(1, content.hiddenAlertCount)
        // No hide was written to seed the preference — the derivation alone hid it.
        assertEquals(null, repository.hiddenAlertIds)
        assertEquals(AlertHideState(), repository.hideState.value)
    }

    @Test
    fun `with hide-all-alerts on, an explicitly shown alert stays visible`() = runTest {
        // "Show hidden alerts" records the alert shown (HIDDEN=0); that explicit decision overrides
        // the preference, so it is not re-hidden on the next load.
        val shownAlert = alert(contentId = "c1", situationId = "a1")
        val repository = FakeArrivalsRepository(
            Result.success(data(hideAlertsByDefault = true).copy(activeAlerts = listOf(shownAlert))),
            initialHideState = AlertHideState(mapOf("a1" to false))
        )
        val viewModel = ArrivalsViewModel("1_100", false, repository)

        viewModel.refresh()

        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(listOf("a1"), content.alerts.map { it.situationId })
        assertEquals(0, content.hiddenAlertCount)
    }

    @Test
    fun `collapseRouteFilter clears the filter when every route is selected`() {
        assertEquals(emptySet<String>(), collapseRouteFilter(setOf("a", "b", "c"), 3))
        assertEquals(setOf("a"), collapseRouteFilter(setOf("a"), 3))
    }
}
