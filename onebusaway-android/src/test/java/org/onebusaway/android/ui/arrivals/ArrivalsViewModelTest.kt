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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.time.ServerTime

/** The arguments of a single [ArrivalsRepository.favoriteRoute] call, for assertions. */
private data class FavoriteRouteCall(
    val routeId: String,
    val shortName: String?,
    val longName: String?,
    val favorite: Boolean
)

private class FakeArrivalsRepository(
    var result: Result<ArrivalsData>,
    initialHideState: AlertHideState = AlertHideState()
) : ArrivalsRepository {

    val requestedMinutesAfter = mutableListOf<Int>()

    /** When set, [getArrivals] suspends until it completes — lets a test hold a load in flight
     *  (e.g. to fire a superseding load-more before the first finishes). */
    var gate: CompletableDeferred<Unit>? = null

    /** Per-call gate/result overrides indexed by call order, for driving *overlapping* loads that
     *  complete out of order (issue #1933). When a slot is present the Nth [getArrivals] awaits that
     *  gate and returns that result instead of [gate]/[result]. */
    val callGates = mutableListOf<CompletableDeferred<Unit>>()
    val callResults = mutableListOf<Result<ArrivalsData>>()

    var lastFavoriteSet: Pair<String, Boolean>? = null

    var lastFavoriteRoute: FavoriteRouteCall? = null

    var hiddenAlertIds: List<String>? = null

    var shownAlertIds: List<String>? = null

    /** The DB's hide/show truth — the single source the ViewModel derives hidden state from.
     *  [hideAlerts]/[showAlerts] mutate it (as the real ContentProvider writes would), and a test can
     *  mutate it directly to stand in for a hide/un-hide from another surface (the alert dialog). */
    val hideState = MutableStateFlow(initialHideState)

    /** The starred route ids the ViewModel overlays onto the arrivals; a test mutates it to stand in
     *  for a star toggle from any surface. */
    val favoriteRoutes = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun getArrivals(
        stopId: String,
        minutesAfter: Int
    ): Result<ArrivalsData> {
        val call = requestedMinutesAfter.size
        requestedMinutesAfter.add(minutesAfter)
        (callGates.getOrNull(call) ?: gate)?.await()
        return callResults.getOrNull(call) ?: result
    }

    override suspend fun setStopFavorite(
        stopId: String,
        code: String?,
        name: String?,
        latitude: Double,
        longitude: Double,
        favorite: Boolean
    ) {
        lastFavoriteSet = stopId to favorite
    }

    override suspend fun favoriteRoute(
        routeId: String,
        shortName: String?,
        longName: String?,
        favorite: Boolean
    ) {
        lastFavoriteRoute = FavoriteRouteCall(routeId, shortName, longName, favorite)
    }

    override fun favoriteRouteIds(): Flow<Set<String>> = favoriteRoutes

    override fun alertHideState(): Flow<AlertHideState> = hideState

    override suspend fun hideAlerts(ids: List<String>) {
        hiddenAlertIds = ids
        hideState.update { it.copy(decisions = it.decisions + ids.associateWith { true }) }
    }

    override suspend fun showAlerts(ids: List<String>) {
        shownAlertIds = ids
        hideState.update { it.copy(decisions = it.decisions + ids.associateWith { false }) }
    }

    override suspend fun markAlertRead(id: String) {}

    override suspend fun setAlertHidden(id: String, hidden: Boolean) {}

    override suspend fun hideAllRecordedAlerts() {}

    override fun alertDetails(id: String): AlertDetails? = null

    override fun lastLoaded(): ArrivalsLoaded? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalsViewModelTest {

    // Unconfined so the derived `state` (a stateIn combine) recomputes eagerly — tests read
    // `state.value` synchronously right after an action, with no advanceUntilIdle in between.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun header(favorite: Boolean = false) = StopHeader("1_100", "Pine St & 3rd Ave", "S", favorite)

    private fun data(
        minutesAfter: Int = 65,
        isStale: Boolean = false,
        favorite: Boolean = false,
        hideAlertsByDefault: Boolean = false
    ) = ArrivalsData(
        arrivals = emptyList(),
        routeGroups = emptyList(),
        header = header(favorite),
        minutesAfter = minutesAfter,
        windowEnd = ServerTime(minutesAfter * 60_000L),
        isStale = isStale,
        actions = emptyMap(),
        activeAlerts = emptyList(),
        hideAlertsByDefault = hideAlertsByDefault,
        routeDisplayNames = emptyList(),
        stopCode = null,
        stopLat = 0.0,
        stopLon = 0.0,
        stopUserName = null
    )

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = ArrivalsViewModel("1_100", FakeArrivalsRepository(Result.success(data())))

        assertEquals(ArrivalsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `refresh emits Content on success`() = runTest {
        val viewModel = ArrivalsViewModel("1_100", FakeArrivalsRepository(Result.success(data())))

        viewModel.refresh()

        val state = viewModel.state.value
        assertTrue(state is ArrivalsUiState.Content)
        assertEquals("Pine St & 3rd Ave", (state as ArrivalsUiState.Content).header.name)
        // The window-end instant rides through unchanged from ArrivalsData into the Content state.
        assertEquals(ServerTime(65 * 60_000L), state.windowEnd)
    }

    @Test
    fun `refresh emits Error when there is no content and the load fails`() = runTest {
        val viewModel = ArrivalsViewModel(
            "1_100",
            FakeArrivalsRepository(Result.failure(IOException("No network")))
        )

        viewModel.refresh()

        assertEquals(ArrivalsUiState.Error("No network"), viewModel.state.value)
    }

    @Test
    fun `a failed poll keeps existing content instead of showing Error`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", repository)
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
            FakeArrivalsRepository(Result.success(data(isStale = true)))
        )

        viewModel.refresh()

        assertTrue((viewModel.state.value as ArrivalsUiState.Content).isStale)
    }

    @Test
    fun `load more widens the time window on the next request`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data(minutesAfter = 65)))
        val viewModel = ArrivalsViewModel("1_100", repository)
        viewModel.refresh()

        viewModel.loadMore()
        advanceUntilIdle()

        // 65 (initial) then 125 (65 + 60 increment)
        assertEquals(listOf(65, 125), repository.requestedMinutesAfter)
    }

    // --- The "load more trips" footer button (loadingMore) -------------------------------------

    @Test
    fun `loadingMore is true while the request is in flight and false once it lands`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", repository)
        viewModel.refresh()
        repository.gate = CompletableDeferred()

        viewModel.loadMore()
        assertTrue(viewModel.loadingMore.value)

        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.loadingMore.value)
    }

    @Test
    fun `loadMore is ignored while a request is already in flight`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data(minutesAfter = 65)))
        val viewModel = ArrivalsViewModel("1_100", repository)
        viewModel.refresh()
        repository.gate = CompletableDeferred()

        viewModel.loadMore()
        viewModel.loadMore() // ignored: a request is already in flight

        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        // Only one widen (65 -> 125), not two.
        assertEquals(listOf(65, 125), repository.requestedMinutesAfter)
    }

    // --- Overlapping out-of-order refreshes (latest-wins guard, #1933) -------------------------

    @Test
    fun `an out-of-order overlapping refresh cannot clobber the fresher result with a stale one`() = runTest {
        // The poll refresh (call #0) starts first but lands LAST as a stale fallback; a user refresh
        // (call #1) starts later and lands FIRST with fresh data. The stale, older-started completion
        // must not overwrite the fresh one it superseded. Regression for #1933.
        val repository = FakeArrivalsRepository(Result.success(data()))
        repository.callGates += CompletableDeferred() // call #0 (poll)
        repository.callGates += CompletableDeferred() // call #1 (user refresh)
        repository.callResults += Result.success(data(minutesAfter = 65, isStale = true)) // #0 stale fallback
        repository.callResults += Result.success(data(minutesAfter = 125, isStale = false)) // #1 fresh
        val viewModel = ArrivalsViewModel("1_100", repository)

        viewModel.manualRefresh() // poll-like refresh, parks on gate #0
        viewModel.manualRefresh() // superseding refresh, parks on gate #1

        // The later-started refresh lands first with fresh data.
        repository.callGates[1].complete(Unit)
        advanceUntilIdle()
        val fresh = viewModel.state.value as ArrivalsUiState.Content
        assertFalse(fresh.isStale)
        assertEquals(ServerTime(125 * 60_000L), fresh.windowEnd)

        // The earlier-started refresh lands last as a stale fallback — it must be dropped, not applied.
        repository.callGates[0].complete(Unit)
        advanceUntilIdle()
        val afterStale = viewModel.state.value as ArrivalsUiState.Content
        assertFalse(afterStale.isStale)
        assertEquals(ServerTime(125 * 60_000L), afterStale.windowEnd)
    }

    @Test
    fun `toggle favorite optimistically updates the header and persists`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data(favorite = false)))
        val viewModel = ArrivalsViewModel("1_100", repository)
        viewModel.refresh()

        viewModel.toggleFavorite()

        assertTrue((viewModel.state.value as ArrivalsUiState.Content).header.isFavorite)
        advanceUntilIdle()
        assertEquals("1_100" to true, repository.lastFavoriteSet)
    }

    private val routeActions = ArrivalActions(
        tripId = "t1",
        routeId = "1_5",
        routeShortName = "5",
        routeLongName = "Fifth Ave",
        scheduleUrl = null,
        agencyName = null,
        blockId = null
    )

    @Test
    fun `toggleRouteFavorite stars an unstarred route wholesale`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        val viewModel = ArrivalsViewModel("1_100", repository)
        viewModel.refresh()
        // Route not in the live favorite set -> toggling stars it.

        viewModel.toggleRouteFavorite(routeActions)
        advanceUntilIdle()

        // Wholesale star (#1751): route id + names, favorite = true.
        assertEquals(
            FavoriteRouteCall("1_5", "5", "Fifth Ave", true),
            repository.lastFavoriteRoute
        )
        // No reload — the star re-flags reactively from the favorite overlay (only the initial load).
        assertEquals(1, repository.requestedMinutesAfter.size)
    }

    @Test
    fun `toggleRouteFavorite unstars a starred route`() = runTest {
        val repository = FakeArrivalsRepository(Result.success(data()))
        repository.favoriteRoutes.value = setOf("1_5") // already starred (from any surface)
        val viewModel = ArrivalsViewModel("1_100", repository)
        viewModel.refresh()

        viewModel.toggleRouteFavorite(routeActions)
        advanceUntilIdle()

        // Already in the live set => unstar (favorite = false).
        assertEquals(
            FavoriteRouteCall("1_5", "5", "Fifth Ave", false),
            repository.lastFavoriteRoute
        )
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
        val viewModel = ArrivalsViewModel("1_100", repository)
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
        val viewModel = ArrivalsViewModel("1_100", repository)
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
        val viewModel = ArrivalsViewModel("1_100", repository)
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
        val viewModel = ArrivalsViewModel("1_100", repository)
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
        val viewModel = ArrivalsViewModel("1_100", repository)
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
        val viewModel = ArrivalsViewModel("1_100", repository)
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
        val viewModel = ArrivalsViewModel("1_100", repository)

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
        val viewModel = ArrivalsViewModel("1_100", repository)

        viewModel.refresh()

        val content = viewModel.state.value as ArrivalsUiState.Content
        assertEquals(listOf("a1"), content.alerts.map { it.situationId })
        assertEquals(0, content.hiddenAlertCount)
    }
}
