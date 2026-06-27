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

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.elements.ObaSituation
import org.onebusaway.android.io.elements.ObaStop
import org.onebusaway.android.io.elements.contentKey
import org.onebusaway.android.io.request.ObaArrivalInfoRequest
import org.onebusaway.android.io.request.ObaArrivalInfoResponse
import org.onebusaway.android.io.request.ObaRouteRequest
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.provider.loadStopUserInfo
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.DBUtil
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.ObaRequestErrors
import org.onebusaway.android.util.SituationUtils
import org.onebusaway.android.util.getRouteDisplayName

/**
 * Collapses situations that present identically to the rider (by [contentKey]), keeping the first
 * occurrence — how republished-duplicate alerts are folded into one row (see #1593).
 */
private fun List<ObaSituation>.dedupeByContent(): List<ObaSituation> = distinctBy { it.contentKey }

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
     *  derives the shown list and hidden count by combining this with the reactive hidden-id set,
     *  so hiding/un-hiding updates the UI without a re-fetch. */
    val activeAlerts: List<AlertItem>,
    /** The subset of [activeAlerts] ids currently hidden in the DB (incl. the "hide all alerts"
     *  preference's auto-hide), used to seed the ViewModel's hidden-id source on each load. */
    val dbHiddenIds: Set<String>,
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

    /** The full situation for an alert id, from the last good response (for the alert dialog). */
    fun situation(id: String): ObaSituation?

    /** The last good response, for the map panel's tutorials / map recentering. */
    fun lastResponse(): ObaArrivalInfoResponse?
}

/**
 * Default implementation wrapping the blocking arrivals-and-departures request. Ports
 * ArrivalsListLoader's behavior: widen the time window until arrivals are found, and fall back
 * to the last good response when a refresh fails. Builds the existing [ArrivalInfo] display
 * model plus the per-arrival actions, service alerts, and route-filter options on the IO thread
 * (their constructors read ContentProviders). All Android statics are quarantined here so
 * [ArrivalsViewModel] stays JVM-testable.
 *
 * Note: this repo is **stateful** ([lastGood]/[situation]/[lastResponse]) and 1:1 with its
 * [ArrivalsViewModel], so its `@Binds` is intentionally **unscoped** (a fresh instance per VM) — do
 * NOT make it `@Singleton`, which would share `lastGood` across stops and corrupt per-stop state.
 */
class DefaultArrivalsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository
) : ArrivalsRepository {

    private var lastGood: ObaArrivalInfoResponse? = null

    private var lastGoodMinutesAfter: Int = MINUTES_AFTER_DEFAULT

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
        var response: ObaArrivalInfoResponse
        var empty: Boolean
        do {
            response = ObaArrivalInfoRequest.newRequest(context, stopId, minutes).call()
            empty = response.arrivalInfo.isNullOrEmpty()
            if (empty) {
                minutes += MINUTES_AFTER_INCREMENT
            }
        } while (empty && minutes <= MINUTES_AFTER_MAX)

        when {
            response.code == ObaApi.OBA_OK -> {
                lastGood = response
                lastGoodMinutesAfter = minutes
                // Record the stop once per session so favoriting persists (markAsFavorite is an
                // UPDATE — it needs the row to exist) and the stop shows in Recent stops.
                if (!stopRecorded) {
                    response.stop?.let {
                        DBUtil.addToDB(it)
                        stopRecorded = true
                    }
                }
                Result.success(toData(stopId, response, minutes, filter, isStale = false, now))
            }
            // Refresh failed but we have prior data — keep showing it (legacy stale fallback)
            lastGood != null ->
                Result.success(
                    toData(stopId, lastGood!!, lastGoodMinutesAfter, filter, isStale = true, now)
                )

            else -> Result.failure(IOException(ObaRequestErrors.getStopErrorString(context, response.code)))
        }
    }

    private fun toData(
        stopId: String,
        response: ObaArrivalInfoResponse,
        minutesAfter: Int,
        routeFilter: Set<String>,
        isStale: Boolean,
        now: Long
    ): ArrivalsData {
        val style = BuildFlavorUtils.getArrivalInfoStyleFromPreferences(context)
        // Style B includes the arrival/departure word in the status label; Style A does not
        val includeArrivalDepartureLabel = style == BuildFlavorUtils.ARRIVAL_INFO_STYLE_B
        val arrivals = ArrivalInfoUtils.convertObaArrivalInfo(
            context,
            response.arrivalInfo ?: emptyArray(),
            ArrayList(routeFilter),
            now,
            includeArrivalDepartureLabel
        )
        val stop = response.stop
        val userInfo = loadStopUserInfo(context, stopId)
        val header = StopHeader(
            stopId = stopId,
            name = MyTextUtils.formatDisplayText(stop?.name).orEmpty(),
            direction = stop?.direction,
            isFavorite = userInfo?.isFavorite ?: false,
            routeCount = stop?.routeIds?.size ?: 0
        )
        val routeOptions = buildRouteFilterOptions(response, stop, routeFilter)
        val (activeAlerts, dbHiddenIds) = buildActiveAlerts(response, routeFilter, now)
        return ArrivalsData(
            arrivals = arrivals,
            header = header,
            minutesAfter = minutesAfter,
            style = style,
            isStale = isStale,
            effectiveRouteFilter = routeFilter,
            actions = buildActions(response, arrivals),
            activeAlerts = activeAlerts,
            dbHiddenIds = dbHiddenIds,
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
        response: ObaArrivalInfoResponse,
        arrivals: List<ArrivalInfo>
    ): Map<String, ArrivalActions> = arrivals.associate { arrival ->
        val info = arrival.info
        val route = response.getRoute(info.routeId)
        info.tripId to ArrivalActions(
            tripId = info.tripId,
            routeId = info.routeId,
            headsign = info.headsign.orEmpty(),
            stopId = info.stopId,
            routeShortName = route?.shortName,
            routeLongName = info.routeLongName,
            scheduleUrl = route?.url,
            agencyName = route?.agencyId?.let { response.getAgency(it)?.name },
            blockId = response.getTrip(info.tripId)?.blockId,
            isRouteFavorite = arrival.isRouteAndHeadsignFavorite
        )
    }

    /**
     * Builds the stop's active alerts (the deduped, in-window representatives — see #1593) and the
     * subset currently hidden in the DB. The hidden subset is returned rather than filtered out so
     * the ViewModel owns the shown/hidden split reactively; this just records and reports DB state.
     */
    private fun buildActiveAlerts(
        response: ObaArrivalInfoResponse,
        routeFilter: Set<String>,
        now: Long
    ): Pair<List<AlertItem>, Set<String>> {
        // Some feeds republish the same alert under new ids/active windows; collapse the duplicates
        // to one representative first, then record + classify only those. Recording a duplicate id
        // that's immediately collapsed would just be a wasted DB write.
        val active = SituationUtils.getAllSituations(response, ArrayList(routeFilter))
            .dedupeByContent()
            .filter { SituationUtils.isActiveWindowForSituation(it, now) }
        active.forEach {
            // Record so read/hidden state is tracked (and auto-hidden when the "hide all alerts"
            // preference is on).
            ObaContract.ServiceAlerts.insertOrUpdate(it.id, ContentValues(), false, null)
        }
        val alerts = active.map { AlertItem(it.id, it.summary.orEmpty(), severityOf(it.severity)) }
        val hiddenIds = active.filter { ObaContract.ServiceAlerts.isHidden(it.id) }
            .mapTo(mutableSetOf()) { it.id }
        return alerts to hiddenIds
    }

    private fun buildRouteFilterOptions(
        response: ObaArrivalInfoResponse,
        stop: ObaStop?,
        routeFilter: Set<String>
    ): List<RouteFilterOption> {
        val routeIds = stop?.routeIds ?: return emptyList()
        return response.getRoutes(routeIds).map { route ->
            RouteFilterOption(
                routeId = route.id,
                displayName = getRouteDisplayName(route),
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

    /** Blocking route-details fetch (already on IO via [favoriteRoute]); writes name/url back. */
    private fun fetchAndStoreRouteDetails(routeId: String, regionId: Long?) {
        val response = ObaRouteRequest.newRequest(context, routeId).call() ?: return
        if (response.code != ObaApi.OBA_OK) return

        var shortName = response.shortName
        var longName = response.longName
        if (shortName.isNullOrEmpty()) {
            shortName = longName
        }
        if (longName.isNullOrEmpty() || shortName == longName) {
            longName = response.description
        }

        val values = ContentValues().apply {
            put(ObaContract.Routes.SHORTNAME, shortName)
            put(ObaContract.Routes.LONGNAME, longName)
            put(ObaContract.Routes.URL, response.url)
            regionId?.let { put(ObaContract.Routes.REGION_ID, it) }
        }
        ObaContract.Routes.insertOrUpdate(context, response.id, values, true)
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

    override fun situation(id: String): ObaSituation? = lastGood?.refs?.getSituation(id)

    override fun lastResponse(): ObaArrivalInfoResponse? = lastGood

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
