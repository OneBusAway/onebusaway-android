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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.RouteFavoritesRepository
import org.onebusaway.android.database.oba.ServiceAlertDao
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.markStopUsed
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.TripPatternGeometry
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.models.contentKey
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
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

/**
 * The representative *active* service-alert id for an arrival: the first of the arrival's referenced
 * [situationIds] that is currently active, or null when none apply. Drives the per-row alert
 * indicator (issue #1687 Bug 2) so a row lights up only for an alert the banner is also surfacing.
 */
internal fun activeAlertFor(situationIds: List<String>, activeSituationIds: Set<String>): String? =
    situationIds.firstOrNull { it in activeSituationIds }

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

/** A loaded snapshot of a stop's arrivals plus the header, actions, and alerts. */
data class ArrivalsData(
    val arrivals: List<ArrivalInfo>,
    /** [arrivals] grouped into one row per (route, direction), ordered by earliest ETA. */
    val routeGroups: List<RouteRowGroup>,
    val header: StopHeader,
    /** The effective time window after the loader's empty-result expansion. */
    val minutesAfter: Int,
    /** The server-clock instant the shown arrivals window ends at (the response's `currentTime` plus
     *  [minutesAfter]) — the "Showing arrivals until HH:MM" footnote formats this. */
    val windowEnd: ServerTime,
    val isStale: Boolean,
    val actions: Map<String, ArrivalActions>,
    /** Every active, de-duplicated alert for the stop — *including* hidden ones. The ViewModel
     *  derives the shown list and hidden count by combining this with the repository's reactive
     *  [ArrivalsRepository.alertHideState], so hiding/un-hiding updates the UI without a re-fetch
     *  (and picks up a hide/un-hide from any other surface for free). */
    val activeAlerts: List<AlertItem>,
    /** The "hide all alerts" preference at load time — the default for alerts the rider hasn't
     *  explicitly hidden or shown. Carried in the snapshot so the shown/hidden split is a pure
     *  function of the snapshot plus [ArrivalsRepository.alertHideState]. */
    val hideAlertsByDefault: Boolean,
    /** Display names of every route serving the stop, for the stop-details dialog. */
    val routeDisplayNames: List<String>,
    val stopCode: String?,
    val stopLat: Double,
    val stopLon: Double,
    val stopUserName: String?
)

/** Loads real-time arrivals for a stop and persists the stop / route favorites. */
interface ArrivalsRepository {

    suspend fun getArrivals(
        stopId: String,
        minutesAfter: Int
    ): Result<ArrivalsData>

    /** Marks (or unmarks) the stop as a favorite in the provider. */
    suspend fun setStopFavorite(stopId: String, favorite: Boolean)

    /**
     * Stars (or unstars) a route wholesale (#1751), then backfills the route's full details
     * (short/long name, URL) from the API so the long name can be shown later.
     */
    suspend fun favoriteRoute(
        routeId: String,
        shortName: String?,
        longName: String?,
        favorite: Boolean
    )

    /**
     * The starred route ids, live — the ViewModel overlays this onto the loaded arrivals so a row's
     * star + the drawer-header promotion re-flag on any star toggle (an arrival row, the route-map
     * header) with no re-fetch. Mirrors [alertHideState]'s reactive-overlay pattern (#1751).
     */
    fun favoriteRouteIds(): Flow<Set<String>>

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
    /** The exact distinct GTFS shapes used by the displayed arrivals' trips, without expanding a
     *  route into unrelated branches or variants. */
    val tripPatterns: Set<TripPatternGeometry>,
    /**
     * The displayed routes paired with the GTFS directions of their actual upcoming trips. An empty
     * direction set means trip metadata was incomplete and stop minimization must retain the route.
     */
    val routeDirections: Map<String, Set<Int>>,
)

/**
 * Collapses upcoming (route, trip-direction) pairs into one stable route -> direction-set map. If any
 * arrival for a route lacks trip metadata, that route's empty set wins so adjacency never hides a
 * potentially valid direction based on incomplete data. When there are no upcoming arrivals, all
 * routes serving the focused stop are returned with unknown direction so adjacency can still focus
 * their stops.
 */
internal fun focusedRouteDirections(
    entries: List<Pair<String, Int?>>,
    fallbackRouteIds: Iterable<String> = emptyList(),
): Map<String, Set<Int>> {
    val directions = LinkedHashMap<String, MutableSet<Int>?>()
    for ((routeId, directionId) in entries) {
        if (!directions.containsKey(routeId)) {
            directions[routeId] = directionId?.let { linkedSetOf(it) }
        } else {
            val known = directions[routeId]
            if (directionId == null) directions[routeId] = null else known?.add(directionId)
        }
    }
    if (directions.isEmpty()) return fallbackRouteIds.associateWith { emptySet() }
    return directions.mapValuesTo(LinkedHashMap()) { (_, value) -> value?.toSet().orEmpty() }
}

/** The fields the service-alert dialog shows, decoupled from `ObaSituation`. */
data class AlertDetails(
    val id: String,
    val summary: String?,
    val description: String?,
    val url: String?,
)

/**
 * Default implementation over the api [StopArrivalsDataSource]. Ports ArrivalsListLoader's
 * behavior: widen the time window until arrivals are found, and fall back to the last good response
 * when a refresh fails. Builds the [ArrivalInfo] display model plus the per-arrival actions and service
 * alerts on the IO thread (their constructors read ContentProviders). All Android statics are
 * quarantined here so [ArrivalsViewModel] stays JVM-testable.
 *
 * Note: this repo is **stateful** ([lastGood]) and 1:1 with its [ArrivalsViewModel], so its
 * `@Binds` is intentionally **unscoped** (a fresh instance per VM) — do NOT make it `@Singleton`,
 * which would share `lastGood` across stops and corrupt per-stop state.
 */
class DefaultArrivalsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val stopArrivals: StopArrivalsDataSource,
    private val serviceAlertDao: ServiceAlertDao,
    private val stopDao: StopDao,
    private val routeFavorites: RouteFavoritesRepository,
    private val importGate: ImportGate,
    private val preferences: PreferencesRepository,
) : ArrivalsRepository {

    /**
     * Everything derived from the last good load, held as one unit so all three readers
     * ([alertDetails], [lastLoaded], and the stale-fallback path) see a mutually consistent set:
     * - [snapshot] — the raw response, for situation lookups and stale re-projection.
     * - [receivedAt] — the monotonic device time the snapshot was received, so the stale-fallback path
     *   can project that server clock forward by elapsed device time (#1612).
     * - [loaded] — the map-relevant snapshot prebuilt from the *computed* [ArrivalsData] so its
     *   [ArrivalsLoaded.routeDirections] matches the drawer's rows (see [ArrivalsLoaded]).
     *
     * Published through the single [AtomicReference] [lastGood] with one write per load, so a
     * concurrent getArrivals (e.g. a user refresh overlapping the poll loop) can't publish a new
     * snapshot while [lastLoaded] still reflects the old one, or mix a new snapshot with an old
     * receipt time. A fresh load publishes unconditionally (new data always wins); the stale-fallback
     * path publishes with `compareAndSet` against the holder it read, so it can never roll a
     * concurrently-published fresh snapshot back to the old one.
     */
    private data class LastGood(
        val snapshot: StopArrivals,
        val receivedAt: ElapsedTime,
        val loaded: ArrivalsLoaded,
    )

    private val lastGood = AtomicReference<LastGood?>(null)

    // Whether the viewed stop has been recorded in the Stops table this session. Recording (a) creates
    // the row so the favorite toggle's UPDATE actually persists, and (b) marks it used so it appears in
    // Recent stops. markAsUsed bumps USE_COUNT, so this is done once — not on every 60s poll/refresh.
    private var stopRecorded = false

    override suspend fun getArrivals(
        stopId: String,
        minutesAfter: Int
    ): Result<ArrivalsData> = withContext(Dispatchers.IO) {
        importGate.awaitReady()
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
                // Stamp the receipt time as close to the response as possible (before the DB reads in
                // toData), then publish the whole consistent set with one write once [data] is built.
                val receivedAt = ElapsedTime.now()
                // Record the stop once per session so favoriting persists (setFavorite is an
                // UPDATE — it needs the row to exist) and the stop shows in Recent stops.
                if (!stopRecorded) {
                    snapshot.stop?.let { recordStop(it, System.currentTimeMillis()); stopRecorded = true }
                }
                val data = toData(snapshot, isStale = false, now = ServerTime(snapshot.currentTime))
                lastGood.set(LastGood(snapshot, receivedAt, loadedSnapshot(snapshot, data)))
                Result.success(data)
            },
            // Refresh failed but we have prior data — keep showing it (legacy stale fallback).
            onFailure = { error ->
                lastGood.get()?.let { stale ->
                    // No fresh server time; project the last good server clock forward by the elapsed
                    // device time so stale ETAs/countdowns keep advancing (legacy behavior) without
                    // reintroducing device clock skew (#1612) — a same-domain ElapsedTime subtraction,
                    // so the typed API allows it directly.
                    val now = ServerTime(stale.snapshot.currentTime) +
                        (ElapsedTime.now() - stale.receivedAt)
                    val data = toData(stale.snapshot, isStale = true, now = now)
                    // Keep the same snapshot/receipt time; refresh only the derived map snapshot so its
                    // stale ETAs advance. CAS so this can't roll back a fresh snapshot a concurrent
                    // successful load published while toData ran — if it did, its holder simply stands.
                    lastGood.compareAndSet(stale, stale.copy(loaded = loadedSnapshot(stale.snapshot, data)))
                    Result.success(data)
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
        isStale: Boolean,
        // Server clock as "now" so ETAs/countdowns and alert active-window checks cancel any device
        // clock skew — the fresh response's currentTime, or (on the stale path) the last good server
        // clock projected forward by elapsed device time (#1612).
        now: ServerTime
    ): ArrivalsData {
        // The unified route row shows lateness via the ETA pill's color, not a verbose status label,
        // so we don't fold the arrival/departure word in. Every production caller now passes false
        // (the classic short form, e.g. "2 min late"); the arrive/depart long form remains available
        // on ArrivalInfo and is still covered by UIUtilTest.
        val includeArrivalDepartureLabel = false
        // Favorite state is a live overlay applied in the ViewModel (from the reactive starred-route
        // set), not baked here — so a star toggle re-flags the list without this re-fetch.
        val arrivals = convertArrivals(
            context, snapshot.arrivals, now, includeArrivalDepartureLabel
        )
        val stop = snapshot.stop
        val userInfo = stopDao.userInfo(snapshot.stopId)
        val header = StopHeader(
            stopId = snapshot.stopId,
            name = MyTextUtils.formatDisplayText(stop?.name).orEmpty(),
            direction = stop?.direction,
            isFavorite = userInfo?.favorite == 1
        )
        val routeDisplayNames = buildRouteDisplayNames(snapshot, stop)
        // Pure grouping; no store write. Hidden state is derived in the ViewModel from [alertHideState]
        // plus [ArrivalsData.hideAlertsByDefault], so nothing on the load path can race the snapshot.
        val situations = snapshot.situations()
        // .epochMs unwrap: isActiveWindowForSituation is a legacy Long-taking helper (its "now" is
        // documented as the server clock); the domain is re-asserted right here at the hand-off.
        val isActive = { s: ObaSituation -> SituationUtils.isActiveWindowForSituation(s, now.epochMs) }
        val activeAlerts = planActiveAlerts(situations, isActive)
        // The situation ids that are active right now, so a per-arrival alert indicator lights up
        // only for currently-active alerts — matching the banner's active set (issue #1687 Bug 2).
        val activeSituationIds = situations.filter(isActive).mapTo(HashSet()) { it.id }
        return ArrivalsData(
            arrivals = arrivals,
            // Stable (agency, line, headsign) row order (#1822), not ETA — a row's position shouldn't
            // drift as arrivals tick down or a poll refreshes.
            routeGroups = groupArrivalsByRouteDirection(arrivals) { agencyNameFor(snapshot, it.routeId) },
            header = header,
            minutesAfter = snapshot.minutesAfter,
            // The window ends [minutesAfter] past the response's own server clock — not the projected
            // `now` — so the footnote names the true boundary of the data actually shown.
            windowEnd = ServerTime(snapshot.currentTime) + snapshot.minutesAfter.minutes,
            isStale = isStale,
            actions = buildActions(snapshot, arrivals, activeSituationIds),
            activeAlerts = activeAlerts,
            hideAlertsByDefault =
                preferences.getBoolean(R.string.preference_key_hide_alerts, false),
            routeDisplayNames = routeDisplayNames,
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

    /** The operating agency's display name for a route, or null when either the route or its agency
     *  reference is missing from the snapshot. Shared by the route-row sort key and [buildActions] so
     *  the two don't independently reimplement the same route→agency lookup. */
    private fun agencyNameFor(snapshot: StopArrivals, routeId: String): String? =
        snapshot.route(routeId)?.agencyId?.let(snapshot::agencyName)

    /** Precomputes the navigation/dialog data for each arrival (legacy reads these on menu tap). */
    private fun buildActions(
        snapshot: StopArrivals,
        arrivals: List<ArrivalInfo>,
        activeSituationIds: Set<String>
    ): Map<String, ArrivalActions> = arrivals.associate { arrival ->
        val route = snapshot.route(arrival.routeId)
        arrival.tripId to ArrivalActions(
            tripId = arrival.tripId,
            routeId = arrival.routeId,
            routeShortName = route?.shortName,
            routeLongName = arrival.routeLongName,
            routeColor = route?.color,
            scheduleUrl = route?.url,
            agencyName = agencyNameFor(snapshot, arrival.routeId),
            blockId = snapshot.trip(arrival.tripId)?.blockId,
            alertSituationId = activeAlertFor(arrival.situationIds, activeSituationIds)
        )
    }

    /** Display names of every route serving the stop, for the stop-details dialog's "Routes:" line. */
    private fun buildRouteDisplayNames(snapshot: StopArrivals, stop: ObaStop?): List<String> {
        val routeIds = stop?.routeIds ?: return emptyList()
        return routeIds.mapNotNull { snapshot.route(it) }
            .map { getRouteDisplayName(it) }
    }

    override suspend fun setStopFavorite(stopId: String, favorite: Boolean) {
        importGate.awaitReady()
        stopDao.setFavorite(stopId, if (favorite) 1 else 0)
    }

    override suspend fun favoriteRoute(
        routeId: String,
        shortName: String?,
        longName: String?,
        favorite: Boolean
    ) {
        // The shared write ensures the row (no URL in hand yet — leaves any existing one), flips the
        // flag, gates on the import, reports analytics, and backfills the full details from the network
        // on a star so the long name shows in the folder.
        routeFavorites.setFavorite(routeId, shortName, longName, url = null, favorite = favorite)
    }

    override fun favoriteRouteIds(): Flow<Set<String>> = routeFavorites.favoriteRouteIds()

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
        lastGood.get()?.snapshot?.situation(id)?.let {
            AlertDetails(it.id, it.summary, it.description, it.url)
        }

    override fun lastLoaded(): ArrivalsLoaded? = lastGood.get()?.loaded

    /** Pairs the response's map payload with exact trip shapes and directions from displayed arrivals. */
    private fun loadedSnapshot(snapshot: StopArrivals, data: ArrivalsData): ArrivalsLoaded =
        ArrivalsLoaded(
            stop = snapshot.stop,
            routes = snapshot.routes,
            hasArrivals = snapshot.hasArrivals,
            tripPatterns = snapshot.tripPatternGeometries(data.arrivals.map { it.tripId }),
            routeDirections = focusedRouteDirections(
                data.arrivals.map { arrival ->
                    arrival.routeId to snapshot.trip(arrival.tripId)?.directionId
                },
                fallbackRouteIds = snapshot.stop?.routeIds.orEmpty().asIterable(),
            ),
        )

    companion object {

        const val MINUTES_AFTER_DEFAULT = 65

        const val MINUTES_AFTER_INCREMENT = 60

        const val MINUTES_AFTER_MAX = 1440
    }
}
