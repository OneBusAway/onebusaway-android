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

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.provider.loadStopUserInfo
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.DBUtil
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.ObaRequestErrors
import org.onebusaway.android.util.SituationUtils
import org.onebusaway.android.util.getRouteDisplayName

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
    val alerts: List<AlertItem>,
    val hiddenAlertCount: Int,
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

    /** Marks the given service alerts as hidden. */
    suspend fun hideAlerts(ids: List<String>)

    /** Un-hides every service alert (the "show hidden alerts" action). */
    suspend fun showAllAlerts()

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
    private val stopArrivals: StopArrivalsDataSource
) : ArrivalsRepository {

    private var lastGood: StopArrivals? = null

    // Whether the viewed stop has been recorded in the Stops table this session. Recording (a) creates
    // the row so the favorite toggle's UPDATE actually persists, and (b) marks it used so it appears in
    // Recent stops. markAsUsed bumps USE_COUNT, so this is done once — not on every 60s poll/refresh.
    private var stopRecorded = false

    override suspend fun getArrivals(
        stopId: String,
        minutesAfter: Int,
        routeFilter: Set<String>?
    ): Result<ArrivalsData> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val filter = routeFilter ?: ObaContract.StopRouteFilters.get(context, stopId).toSet()
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
                lastGood = snapshot
                // Record the stop once per session so favoriting persists (markAsFavorite is an
                // UPDATE — it needs the row to exist) and the stop shows in Recent stops.
                if (!stopRecorded) {
                    snapshot.stop?.let { DBUtil.addToDB(it); stopRecorded = true }
                }
                Result.success(toData(snapshot, filter, isStale = false, now))
            },
            // Refresh failed but we have prior data — keep showing it (legacy stale fallback).
            onFailure = { error ->
                lastGood?.let { Result.success(toData(it, filter, isStale = true, now)) }
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

    private fun toData(
        snapshot: StopArrivals,
        routeFilter: Set<String>,
        isStale: Boolean,
        now: Long
    ): ArrivalsData {
        val style = BuildFlavorUtils.getArrivalInfoStyleFromPreferences(context)
        // Style B includes the arrival/departure word in the status label; Style A does not.
        val includeArrivalDepartureLabel = style == BuildFlavorUtils.ARRIVAL_INFO_STYLE_B
        val arrivals = convertArrivals(
            context, snapshot.arrivals, routeFilter, now, includeArrivalDepartureLabel
        )
        val stop = snapshot.stop
        val userInfo = loadStopUserInfo(context, snapshot.stopId)
        val header = StopHeader(
            stopId = snapshot.stopId,
            name = MyTextUtils.formatDisplayText(stop?.name).orEmpty(),
            direction = stop?.direction,
            isFavorite = userInfo?.isFavorite ?: false,
            routeCount = stop?.routeIds?.size ?: 0
        )
        val routeOptions = buildRouteFilterOptions(snapshot, stop, routeFilter)
        val (alerts, hiddenAlertCount) = buildAlerts(snapshot, routeFilter, now)
        return ArrivalsData(
            arrivals = arrivals,
            header = header,
            minutesAfter = snapshot.minutesAfter,
            style = style,
            isStale = isStale,
            effectiveRouteFilter = routeFilter,
            actions = buildActions(snapshot, arrivals),
            alerts = alerts,
            hiddenAlertCount = hiddenAlertCount,
            routeFilterOptions = routeOptions,
            filteredRouteCount = routeFilter.size,
            stopCode = stop?.stopCode,
            stopLat = stop?.latitude ?: 0.0,
            stopLon = stop?.longitude ?: 0.0,
            stopUserName = userInfo?.userName
        )
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

    /** Ports ArrivalsListFragment.refreshSituations: persist, then keep active + non-hidden. */
    private fun buildAlerts(
        snapshot: StopArrivals,
        routeFilter: Set<String>,
        now: Long
    ): Pair<List<AlertItem>, Int> {
        val situations = snapshot.situations(ArrayList(routeFilter))
        if (situations.isEmpty()) return emptyList<AlertItem>() to 0
        val active = mutableListOf<AlertItem>()
        var hiddenCount = 0
        for (situation in situations) {
            // Make sure this situation is recorded so read/hidden state can be tracked.
            ObaContract.ServiceAlerts.insertOrUpdate(situation.id, ContentValues(), false, null)
            val isHidden = ObaContract.ServiceAlerts.isHidden(situation.id)
            if (SituationUtils.isActiveWindowForSituation(situation, now) && !isHidden) {
                active.add(
                    AlertItem(situation.id, situation.summary.orEmpty(), severityOf(situation.severity))
                )
            }
            if (isHidden) hiddenCount++
        }
        return active to hiddenCount
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
        withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId)
            ObaContract.Stops.markAsFavorite(context, uri, favorite)
        }
    }

    override suspend fun favoriteRoute(
        routeId: String,
        headsign: String?,
        stopId: String?,
        shortName: String?,
        longName: String?,
        favorite: Boolean
    ) = withContext(Dispatchers.IO) {
        val regionId = regionRepository.region.value?.id
        // Ensure the route row exists (stamped with the current region) before marking the favorite.
        val values = ContentValues().apply {
            put(ObaContract.Routes.SHORTNAME, shortName)
            put(ObaContract.Routes.LONGNAME, longName)
            regionId?.let { put(ObaContract.Routes.REGION_ID, it) }
        }
        ObaContract.Routes.insertOrUpdate(context, routeId, values, true)
        ObaContract.RouteHeadsignFavorites.markAsFavorite(context, routeId, headsign, stopId, favorite)

        // Backfill the full route details so the long name can be shown later (was an AsyncTaskLoader).
        fetchAndStoreRouteDetails(routeId, regionId)
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

        val values = ContentValues().apply {
            put(ObaContract.Routes.SHORTNAME, shortName)
            put(ObaContract.Routes.LONGNAME, longName)
            put(ObaContract.Routes.URL, route.url)
            regionId?.let { put(ObaContract.Routes.REGION_ID, it) }
        }
        ObaContract.Routes.insertOrUpdate(context, route.id, values, true)
    }

    override suspend fun setRouteFilter(stopId: String, filter: Set<String>) {
        withContext(Dispatchers.IO) {
            ObaContract.StopRouteFilters.set(context, stopId, ArrayList(filter))
        }
    }

    override suspend fun setArrivalStyle(style: Int) {
        withContext(Dispatchers.IO) {
            BuildFlavorUtils.setArrivalInfoStyle(context, style)
        }
    }

    override suspend fun hideAlerts(ids: List<String>) {
        withContext(Dispatchers.IO) {
            for (id in ids) {
                ObaContract.ServiceAlerts.insertOrUpdate(id, ContentValues(), false, true)
            }
        }
    }

    override suspend fun showAllAlerts() {
        withContext(Dispatchers.IO) {
            ObaContract.ServiceAlerts.showAllAlerts()
        }
    }

    override fun alertDetails(id: String): AlertDetails? =
        lastGood?.situation(id)?.let {
            AlertDetails(it.id, it.summary, it.description, it.url)
        }

    override fun lastLoaded(): ArrivalsLoaded? {
        val snapshot = lastGood ?: return null
        return ArrivalsLoaded(snapshot.stop, snapshot.routes, snapshot.hasArrivals)
    }

    companion object {

        const val MINUTES_AFTER_DEFAULT = 65

        const val MINUTES_AFTER_INCREMENT = 60

        const val MINUTES_AFTER_MAX = 1440

        private fun severityOf(severity: String?): AlertSeverity = when (severity) {
            ObaSituation.SEVERITY_NO_IMPACT -> AlertSeverity.INFO
            ObaSituation.SEVERITY_SEVERE, ObaSituation.SEVERITY_VERY_SEVERE -> AlertSeverity.ERROR
            else -> AlertSeverity.WARNING
        }
    }
}
