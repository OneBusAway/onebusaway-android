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

import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.api.data.StopArrivals
import org.onebusaway.android.api.data.RouteDataSource

import android.content.Context
import android.os.SystemClock
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.analytics.AnalyticsProvider
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.RouteDao
import org.onebusaway.android.database.oba.RouteHeadsignFavoriteDao
import org.onebusaway.android.database.oba.ServiceAlertDao
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopRouteFilterDao
import org.onebusaway.android.database.oba.applyRouteHeadsignFavorite
import org.onebusaway.android.database.oba.computeRouteHeadsignFavorite
import org.onebusaway.android.database.oba.markStopUsed
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.models.contentKey
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.ObaRequestErrors
import org.onebusaway.android.util.SituationUtils
import org.onebusaway.android.util.getRouteDisplayName

/**
 * Folds a stop's situations into active-alert rows (see #1593). Filters to the active set FIRST, then
 * groups by [contentKey] so an expired duplicate can't become a group's representative and suppress a
 * still-active one. Each row carries the full set of grouped ids ([AlertItem.situationIds]) so hidden
 * state can be tracked across the feed rotating an alert's id. Pure grouping only — hidden state is
 * derived downstream from the reactive hidden-id source — so it is JVM-unit-testable with no I/O.
 */
internal fun planActiveAlerts(
    situations: List<ObaSituation>,
    isActive: (ObaSituation) -> Boolean
): List<AlertItem> =
    situations.filter(isActive)
        .groupBy { it.contentKey }
        .map { (contentId, group) ->
            val representative = group.first()
            AlertItem(
                contentId = contentId,
                situationId = representative.id,
                situationIds = group.mapTo(mutableSetOf()) { it.id },
                summary = representative.summary.orEmpty(),
                severity = severityOf(representative.severity)
            )
        }

/** Maps an ObaSituation severity onto the three banner styles, matching the legacy SituationAlert. */
internal fun severityOf(severity: String?): AlertSeverity = when (severity) {
    ObaSituation.SEVERITY_NO_IMPACT -> AlertSeverity.INFO
    ObaSituation.SEVERITY_SEVERE, ObaSituation.SEVERITY_VERY_SEVERE -> AlertSeverity.ERROR
    else -> AlertSeverity.WARNING
}

/**
 * The explicit per-situation hide [decisions] recorded in the store: true = hidden, false = shown.
 * Ids absent from the map are undecided — the "hide all alerts" preference decides those. Because
 * visibility is a total function of the map plus the preference (no write happens on load), a
 * preference-hidden new alert is hidden the instant it's derived, with no flash. See #1593.
 */
data class AlertHideState(val decisions: Map<String, Boolean> = emptyMap()) {
    /** Whether [alert] should be hidden, given the "hide all alerts" preference [hideByDefault]:
     *  an explicit hide on any grouped id wins, then an explicit show, else the preference. */
    fun isHidden(alert: AlertItem, hideByDefault: Boolean): Boolean = when {
        alert.situationIds.any { decisions[it] == true } -> true
        alert.situationIds.any { decisions[it] == false } -> false
        else -> hideByDefault
    }
}

/** A loaded snapshot of a stop's arrivals plus the header, actions, alerts, and filter data. */
data class ArrivalsData(
    val arrivals: List<ArrivalInfo>,
    val header: StopHeader,
    /** The effective time window after the loader's empty-result expansion. */
    val minutesAfter: Int,
    val style: Int,
    val isStale: Boolean,
    /** The route filter actually applied (loaded from the provider when the caller passed null). */
    val effectiveRouteFilter: Set<String>,
    val actions: Map<String, ArrivalActions>,
    /** Every active, de-duplicated alert for the stop — *including* hidden ones. The ViewModel
     *  derives the shown list and hidden count by combining this with the repository's reactive
     *  [ArrivalsRepository.alertHideState], so hiding/un-hiding updates the UI without a re-fetch
     *  (and picks up a hide/un-hide from any other surface for free). */
    val activeAlerts: List<AlertItem>,
    /** The "hide all alerts" preference at load time — the default for alerts the rider hasn't
     *  explicitly hidden or shown. Carried in the snapshot (like [style]) so the shown/hidden split
     *  is a pure function of the snapshot plus [ArrivalsRepository.alertHideState]. */
    val hideAlertsByDefault: Boolean,
    val routeFilterOptions: List<RouteFilterOption>,
    val filteredRouteCount: Int,
    val stopCode: String?,
    val stopLat: Double,
    val stopLon: Double,
    val stopUserName: String?
)

/** Loads real-time arrivals for a stop and persists the per-stop route filter / favorite. */
interface ArrivalsRepository {

    /**
     * @param routeFilter the routes to keep, or null to load the persisted filter for this stop
     */
    suspend fun getArrivals(
        stopId: String,
        minutesAfter: Int,
        routeFilter: Set<String>?
    ): Result<ArrivalsData>

    /** Marks (or unmarks) the stop as a favorite in the provider. */
    suspend fun setStopFavorite(stopId: String, favorite: Boolean)

    /**
     * Stars (or unstars) a route/headsign favorite, then backfills the route's full details
     * (short/long name, URL) from the API so the long name can be shown later. [stopId] null means
     * "all stops". Replaces the legacy QueryUtils write + AsyncTaskLoader route-info fetch.
     */
    suspend fun favoriteRoute(
        routeId: String,
        headsign: String?,
        stopId: String?,
        shortName: String?,
        longName: String?,
        favorite: Boolean
    )

    /** Persists the per-stop route filter (empty == show all). */
    suspend fun setRouteFilter(stopId: String, filter: Set<String>)

    /** Persists the arrival-info display style (the legacy "sort by" view-mode toggle). */
    suspend fun setArrivalStyle(style: Int)

    /**
     * The explicit hide/show decisions recorded in the store, re-emitting on every service-alert
     * change. This is the single source of truth for hidden state: the ViewModel derives the
     * shown/hidden split from it (plus the per-load preference), and a hide/un-hide from any surface
     * (swipe, the alert dialog, "show hidden alerts") flows back here with nothing to reconcile.
     * See #1593.
     */
    fun alertHideState(): Flow<AlertHideState>

    /** Records the given service alerts as hidden. */
    suspend fun hideAlerts(ids: List<String>)

    /** Records the given service alerts as shown — an explicit reveal that overrides the "hide all
     *  alerts" preference (the "show hidden alerts" action). */
    suspend fun showAlerts(ids: List<String>)

    /** Records a single alert hidden/shown (the alert dialog's Hide / Undo). */
    suspend fun setAlertHidden(id: String, hidden: Boolean)

    /** Stamps a single alert read (the situation dialog marks the alert read on open). */
    suspend fun markAlertRead(id: String)

    /** Hides every recorded alert (the dialog's Hide All). */
    suspend fun hideAllRecordedAlerts()

    /** The service-alert dialog's content for an alert id, from the last good response, or null. */
    fun alertDetails(id: String): AlertDetails?

    /** The map-relevant snapshot from the last good load, for the map panel's recentering/tutorials. */
    fun lastLoaded(): ArrivalsLoaded?
}

/**
 * What the map host needs after each arrivals load: the focused [stop] to recenter on, its [routes]
 * (rendered on the map), and whether there are [hasArrivals] (gates the onboarding tutorial).
 * Decouples the host from the arrivals fetch. ([stop]/[routes] are the model interfaces the map
 * subsystem consumes.)
 */
data class ArrivalsLoaded(
    val stop: ObaStop?,
    val routes: List<ObaRoute>?,
    val hasArrivals: Boolean,
)

/** The fields the service-alert dialog shows, decoupled from `ObaSituation`. */
data class AlertDetails(
    val id: String,
    val summary: String?,
    val description: String?,
    val url: String?,
)

/**
 * Default implementation over the io.client [StopArrivalsDataSource]. Ports ArrivalsListLoader's
 * behavior: widen the time window until arrivals are found, and fall back to the last good response
 * when a refresh fails. Builds the [ArrivalInfo] display model plus the per-arrival actions, service
 * alerts, and route-filter options on the IO thread (their constructors read ContentProviders). All
 * Android statics are quarantined here so [ArrivalsViewModel] stays JVM-testable.
 *
 * Note: this repo is **stateful** ([lastGood]) and 1:1 with its [ArrivalsViewModel], so its
 * `@Binds` is intentionally **unscoped** (a fresh instance per VM) — do NOT make it `@Singleton`,
 * which would share `lastGood` across stops and corrupt per-stop state.
 */
class DefaultArrivalsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val routeRepository: RouteDataSource,
    private val stopArrivals: StopArrivalsDataSource,
    private val stopRouteFilterDao: StopRouteFilterDao,
    private val serviceAlertDao: ServiceAlertDao,
    private val stopDao: StopDao,
    private val routeDao: RouteDao,
    private val routeHeadsignFavoriteDao: RouteHeadsignFavoriteDao,
    private val importGate: ImportGate,
    private val preferences: PreferencesRepository,
    private val analyticsProvider: AnalyticsProvider,
) : ArrivalsRepository {

    /** The last good snapshot paired with the monotonic device time it was received, so the
     *  stale-fallback path can project that server clock forward by elapsed device time (#1612). The
     *  two must be read as a consistent pair; held in one `@Volatile` reference so a concurrent
     *  getArrivals (e.g. a user refresh overlapping the poll loop) can't mix a new snapshot with an
     *  old receipt time. */
    private data class LastGood(val snapshot: StopArrivals, val elapsedMs: Long)

    @Volatile
    private var lastGood: LastGood? = null

    // Whether the viewed stop has been recorded in the Stops table this session. Recording (a) creates
    // the row so the favorite toggle's UPDATE actually persists, and (b) marks it used so it appears in
    // Recent stops. markAsUsed bumps USE_COUNT, so this is done once — not on every 60s poll/refresh.
    private var stopRecorded = false

    override suspend fun getArrivals(
        stopId: String,
        minutesAfter: Int,
        routeFilter: Set<String>?
    ): Result<ArrivalsData> = withContext(Dispatchers.IO) {
        importGate.awaitReady()
        val now = System.currentTimeMillis()
        val filter = routeFilter ?: stopRouteFilterDao.routeIdsForStop(stopId).toSet()
        var minutes = minutesAfter
        // Widen the window while the fetch is empty (or failing), matching the legacy loader.
        var result: Result<StopArrivals>
        do {
            result = stopArrivals.arrivals(stopId, minutes)
            if (result.getOrNull()?.hasArrivals == true) break
            minutes += MINUTES_AFTER_INCREMENT
        } while (minutes <= MINUTES_AFTER_MAX)

        result.fold(
            onSuccess = { snapshot ->
                lastGood = LastGood(snapshot, SystemClock.elapsedRealtime())
                // Record the stop once per session so favoriting persists (setFavorite is an
                // UPDATE — it needs the row to exist) and the stop shows in Recent stops.
                if (!stopRecorded) {
                    snapshot.stop?.let { recordStop(it, now); stopRecorded = true }
                }
                Result.success(toData(snapshot, filter, isStale = false, now = snapshot.currentTime))
            },
            // Refresh failed but we have prior data — keep showing it (legacy stale fallback).
            onFailure = { error ->
                lastGood?.let { stale ->
                    // No fresh server time; project the last good server clock forward by the elapsed
                    // device time so stale ETAs/countdowns keep advancing (legacy behavior) without
                    // reintroducing device clock skew (#1612).
                    val now = stale.snapshot.currentTime +
                        (SystemClock.elapsedRealtime() - stale.elapsedMs)
                    Result.success(toData(stale.snapshot, filter, isStale = true, now = now))
                }
                    ?: Result.failure(
                        IOException(
                            ObaRequestErrors.getStopErrorString(
                                context, (error as? ObaApiException)?.code ?: ObaApi.OBA_IO_EXCEPTION
                            )
                        )
                    )
            }
        )
    }

    private suspend fun toData(
        snapshot: StopArrivals,
        routeFilter: Set<String>,
        isStale: Boolean,
        // Server clock as "now" so ETAs/countdowns and alert active-window checks cancel any device
        // clock skew — the fresh response's currentTime, or (on the stale path) the last good server
        // clock projected forward by elapsed device time (#1612).
        now: Long
    ): ArrivalsData {
        val style = BuildFlavorUtils.getArrivalInfoStyleFromPreferences(context)
        // Style B includes the arrival/departure word in the status label; Style A does not.
        val includeArrivalDepartureLabel = style == BuildFlavorUtils.ARRIVAL_INFO_STYLE_B
        // One favorites query for the whole list; ArrivalInfo's favorite state resolves from it in
        // memory (the legacy per-row ContentProvider lookup is gone).
        val favoriteRows = routeHeadsignFavoriteDao.favoritesForStopOrAll(snapshot.stopId)
        val arrivals = convertArrivals(
            context, snapshot.arrivals, routeFilter, ServerTime(now), includeArrivalDepartureLabel
        ) { routeId, headsign, stopId ->
            computeRouteHeadsignFavorite(favoriteRows, routeId, headsign, stopId)
        }
        val stop = snapshot.stop
        val userInfo = stopDao.userInfo(snapshot.stopId)
        val header = StopHeader(
            stopId = snapshot.stopId,
            name = MyTextUtils.formatDisplayText(stop?.name).orEmpty(),
            direction = stop?.direction,
            isFavorite = userInfo?.favorite == 1,
            routeCount = stop?.routeIds?.size ?: 0
        )
        val routeOptions = buildRouteFilterOptions(snapshot, stop, routeFilter)
        // Pure grouping; no store write. Hidden state is derived in the ViewModel from [alertHideState]
        // plus [ArrivalsData.hideAlertsByDefault], so nothing on the load path can race the snapshot.
        val activeAlerts = planActiveAlerts(
            situations = snapshot.situations(ArrayList(routeFilter)),
            isActive = { SituationUtils.isActiveWindowForSituation(it, now) }
        )
        return ArrivalsData(
            arrivals = arrivals,
            header = header,
            minutesAfter = snapshot.minutesAfter,
            style = style,
            isStale = isStale,
            effectiveRouteFilter = routeFilter,
            actions = buildActions(snapshot, arrivals),
            activeAlerts = activeAlerts,
            hideAlertsByDefault =
                preferences.getBoolean(R.string.preference_key_hide_alerts, false),
            routeFilterOptions = routeOptions,
            filteredRouteCount = routeFilter.size,
            stopCode = stop?.stopCode,
            stopLat = stop?.latitude ?: 0.0,
            stopLon = stop?.longitude ?: 0.0,
            stopUserName = userInfo?.userName
        )
    }

    /**
     * Records the viewed stop in the stops table (the legacy DBUtil.addToDB): creates the row so the
     * favorite UPDATE persists and marks it used so it appears in Recent stops. Done once per session.
     */
    private suspend fun recordStop(stop: ObaStop, now: Long) {
        stopDao.markStopUsed(stop, regionRepository.region.value?.id, now)
    }

    /** Precomputes the navigation/dialog data for each arrival (legacy reads these on menu tap). */
    private fun buildActions(
        snapshot: StopArrivals,
        arrivals: List<ArrivalInfo>
    ): Map<String, ArrivalActions> = arrivals.associate { arrival ->
        val route = snapshot.route(arrival.routeId)
        arrival.tripId to ArrivalActions(
            tripId = arrival.tripId,
            routeId = arrival.routeId,
            headsign = arrival.headsign.orEmpty(),
            stopId = arrival.stopId,
            routeShortName = route?.shortName,
            routeLongName = arrival.routeLongName,
            scheduleUrl = route?.url,
            agencyName = route?.agencyId?.let { snapshot.agencyName(it) },
            blockId = snapshot.trip(arrival.tripId)?.blockId,
            isRouteFavorite = arrival.isRouteAndHeadsignFavorite
        )
    }

    private fun buildRouteFilterOptions(
        snapshot: StopArrivals,
        stop: ObaStop?,
        routeFilter: Set<String>
    ): List<RouteFilterOption> {
        val routeIds = stop?.routeIds ?: return emptyList()
        return routeIds.mapNotNull { snapshot.route(it) }.map { route ->
            RouteFilterOption(
                routeId = route.id,
                displayName = getRouteDisplayName(route.shortName, route.longName),
                checked = routeFilter.contains(route.id)
            )
        }
    }

    override suspend fun setStopFavorite(stopId: String, favorite: Boolean) {
        importGate.awaitReady()
        stopDao.setFavorite(stopId, if (favorite) 1 else 0)
    }

    override suspend fun favoriteRoute(
        routeId: String,
        headsign: String?,
        stopId: String?,
        shortName: String?,
        longName: String?,
        favorite: Boolean
    ) {
        importGate.awaitReady()
        val regionId = regionRepository.region.value?.id
        // Ensure the route row exists (stamped with the current region) before marking the favorite.
        routeDao.markRouteUsed(routeId, shortName, longName, regionId, System.currentTimeMillis())
        setRouteHeadsignFavorite(routeId, headsign, stopId, favorite)

        // Backfill the full route details so the long name can be shown later (was an AsyncTaskLoader).
        fetchAndStoreRouteDetails(routeId, regionId)
    }

    /**
     * Marks (or unmarks) a route/headsign/stop combination a favorite (delegating the DB-semantic
     * reconciliation to [applyRouteHeadsignFavorite]), then reports bookmark analytics.
     */
    private suspend fun setRouteHeadsignFavorite(
        routeId: String,
        headsign: String?,
        stopId: String?,
        favorite: Boolean
    ) {
        applyRouteHeadsignFavorite(routeHeadsignFavoriteDao, routeDao, routeId, headsign, stopId, favorite)
        reportBookmarkAnalytics(routeId, headsign, stopId, favorite)
    }

    private fun reportBookmarkAnalytics(
        routeId: String,
        headsign: String?,
        stopId: String?,
        favorite: Boolean
    ) {
        val event = context.getString(
            if (favorite) R.string.analytics_label_star_route else R.string.analytics_label_unstar_route
        )
        val param = "${routeId}_$headsign for ${stopId ?: "all stops"}"
        ObaAnalytics.reportUiEvent(
            FirebaseAnalytics.getInstance(context),
            analyticsProvider.plausible,
            PlausibleAnalytics.REPORT_BOOKMARK_EVENT_URL,
            event,
            param,
        )
    }

    /** Route-details fetch via the modernized client; writes name/url back. */
    private suspend fun fetchAndStoreRouteDetails(routeId: String, regionId: Long?) {
        val route = routeRepository.getRoute(routeId).getOrNull() ?: return

        var shortName = route.shortName
        var longName = route.longName
        if (shortName.isNullOrEmpty()) {
            shortName = longName
        }
        if (longName.isNullOrEmpty() || shortName == longName) {
            longName = route.description
        }

        routeDao.storeRouteDetails(
            route.id, shortName, longName, route.url, regionId, System.currentTimeMillis()
        )
    }

    override suspend fun setRouteFilter(stopId: String, filter: Set<String>) {
        importGate.awaitReady()
        stopRouteFilterDao.replaceForStop(stopId, filter.toList())
    }

    override suspend fun setArrivalStyle(style: Int) {
        withContext(Dispatchers.IO) {
            BuildFlavorUtils.setArrivalInfoStyle(context, style)
        }
    }

    override fun alertHideState(): Flow<AlertHideState> =
        serviceAlertDao.hideDecisions()
            .onStart { importGate.awaitReady() }
            .map { rows -> AlertHideState(rows.associate { it.id to (it.hidden == 1) }) }

    override suspend fun hideAlerts(ids: List<String>) {
        importGate.awaitReady()
        for (id in ids) serviceAlertDao.setHidden(id, true)
    }

    override suspend fun showAlerts(ids: List<String>) {
        importGate.awaitReady()
        for (id in ids) serviceAlertDao.setHidden(id, false)
    }

    override suspend fun setAlertHidden(id: String, hidden: Boolean) {
        importGate.awaitReady()
        serviceAlertDao.setHidden(id, hidden)
    }

    override suspend fun markAlertRead(id: String) {
        importGate.awaitReady()
        serviceAlertDao.markRead(id, System.currentTimeMillis())
    }

    override suspend fun hideAllRecordedAlerts() {
        importGate.awaitReady()
        serviceAlertDao.setAllHidden(1)
    }

    override fun alertDetails(id: String): AlertDetails? =
        lastGood?.snapshot?.situation(id)?.let {
            AlertDetails(it.id, it.summary, it.description, it.url)
        }

    override fun lastLoaded(): ArrivalsLoaded? {
        val snapshot = lastGood?.snapshot ?: return null
        return ArrivalsLoaded(snapshot.stop, snapshot.routes, snapshot.hasArrivals)
    }

    companion object {

        const val MINUTES_AFTER_DEFAULT = 65

        const val MINUTES_AFTER_INCREMENT = 60

        const val MINUTES_AFTER_MAX = 1440
    }
}
