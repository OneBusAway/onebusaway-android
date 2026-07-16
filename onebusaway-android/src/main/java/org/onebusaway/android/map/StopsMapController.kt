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
package org.onebusaway.android.map

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.database.oba.CachedViewport
import org.onebusaway.android.database.oba.StopCacheRepository
import org.onebusaway.android.models.NearbyStops
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.primaryRouteType
import org.onebusaway.android.map.render.stopZoomBand
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.RegionUtils

/**
 * The nearby-stops use case (the legacy `StopMapController`): loads + accumulates the bus stops in the
 * current viewport as the camera pans/zooms, and owns the stop **render focus** (the 1.5× icon + the
 * center-on-tap camera move). A cold driver over a [MapHost]: it reacts to [MapHost.camera] and writes
 * [MapHost.renderState], so it carries no map-SDK dependency. [start] launches the loader on [scope];
 * [cancel] stops it.
 *
 * Shared by every map that shows nearby stops — the home map, the trip-results / report / location-picker
 * screens — and reused by the route map (which feeds the route's own stops in via [showStops]). The
 * route-header camera bias on programmatic focus is supplied by [routeActive] (the home map passes a
 * route-mode predicate; standalone stop maps leave it false).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class StopsMapController(
    private val host: MapHost,
    private val mapDataSource: MapDataSource,
    private val regionRepo: RegionRepository,
    private val locationRepository: LocationRepository,
    private val scope: CoroutineScope,
    private val routeActive: () -> Boolean = { false },
    // The in-memory stop cap, read on each load so a change in advanced settings applies on the next pan.
    private val cacheSize: () -> Int = { DEFAULT_STOP_CACHE_SIZE },
    // The ids of the user's starred (favorite) stops, live: starring/unstarring re-flags the drawn
    // markers so their star icon + tap preference appear immediately (#1680). Defaults to no favorites
    // for the slim stop maps (the report/picker) that don't surface starred stops.
    private val favoriteStopIds: Flow<Set<String>> = flowOf(emptySet()),
    // The persistent stop cache (#1754): each viewport renders cached stops first, then reconciles
    // with the network. Null disables read-through (the slim report/picker maps, which are transient
    // and would only add write churn), mirroring the favoriteStopIds no-op default.
    private val stopCache: StopCacheRepository? = null,
    // The device wall clock for the cache TTL (a purely local timer). Read once per load; injectable so
    // tests pin it. The default is the sanctioned WallTime.now() mint (inert for the slim maps, whose
    // cache is disabled so it's never read).
    private val now: () -> WallTime = { WallTime.now() },
) {

    private val renderState get() = host.renderState

    // Stop accumulation across pans: bounded to the [cacheSize] stops nearest the viewport centre (see
    // trimToNearest), never by recency — so an incomplete load can't evict the centred core. Plain
    // insertion order; nothing consumes access order. Alongside the routes cache used to resolve a
    // stop's icon route type and to report a stop's routes to listeners.
    private val stopAccum = LinkedHashMap<String, StopMarker>()

    private val cachedRoutes = HashMap<String, ObaRoute>()

    // routeId -> ObaRoute.TYPE_*, maintained alongside cachedRoutes so toStopMarker doesn't rebuild
    // the lookup on every pan.
    private val routeTypeById = HashMap<String, Int>()

    // The user's current favorite stop ids, kept in sync by the collector in init. Read on the main
    // thread only (all mutators here run there), so a plain field is safe.
    private var favoriteIds: Set<String> = emptySet()

    // Optional route-owned presentation over the canonical nearby-stop cache. A single route hides
    // nearby stops; a focused stop combines them with exact scheduled trip stops and hides non-members.
    private var routePresentation: RouteStopPresentation? = null

    private var loadJob: Job? = null

    init {
        // Derive the stop zoom band from the camera and publish it into the render snapshot, so a pure
        // zoom (which changes no other snapshot field) re-fires the renderer to swap full icons ⇄ dots
        // like any other snapshot change. Always-on (not tied to the viewport loader's start/stop), so
        // it keeps updating in route mode where the loader is stopped. host.camera only emits on camera
        // idle, and distinctUntilChanged collapses it to just the band crossings.
        scope.launch {
            host.camera
                .filterNotNull()
                .map { stopZoomBand(it.zoom.toFloat()) }
                .distinctUntilChanged()
                .collect { renderState.setStopBand(it) }
        }

        // Track the user's starred stops: re-flag the already-accumulated markers on any change so a
        // star/unstar shows/hides the star icon + tap preference without waiting for the next pan (#1680).
        scope.launch {
            favoriteStopIds.collect { ids ->
                favoriteIds = ids
                applyFavorites()
            }
        }
    }

    /**
     * Re-flag the accumulated markers against the current [favoriteIds] and republish if any changed.
     * Collects the changes before mutating [stopAccum] so we never write to the access-ordered map while
     * iterating it.
     */
    private fun applyFavorites() {
        val updates = buildList {
            for ((id, marker) in stopAccum) {
                val favorite = id in favoriteIds
                if (marker.favorite != favorite) add(id to marker.copy(favorite = favorite))
            }
        }
        if (updates.isEmpty()) return
        for ((id, marker) in updates) stopAccum[id] = marker
        publishStops()
    }

    /** Install a single-route or focused-trip stop presentation, or return to ordinary nearby stops. */
    internal fun setRoutePresentation(presentation: RouteStopPresentation?) {
        if (routePresentation == presentation) return
        presentation?.routes?.let(::cacheRoutes)
        routePresentation = presentation
        publishStops()
    }

    /** (Re)start the viewport stop loader (the old StopMapController's camera watch). */
    fun start() {
        loadJob?.cancel()
        loadJob = launchLoader()
    }

    /** Stop the viewport loader (its accumulated stops + focus are left intact for the next [start]). */
    fun stop() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun launchLoader(): Job = scope.launch {
        // The "is the last load still good for this viewport?" state (the old StopsResponse): the
        // camera the last completed load was made at + whether it had a response + its limit flag.
        var lastLoad: CameraSnapshot? = null
        var lastHadResponse = false
        var lastLimitExceeded = false

        host.camera
            .filterNotNull()
            .debounce(STOP_LOAD_DEBOUNCE_MS)
            // Settle on drag-end: if the debounce elapses while a gesture is still in flight (the user is
            // mid-pan, or a fling that emitted an intermediate idle is still going), drop this viewport —
            // the gesture's terminating camera-idle re-arms the debounce and fires one load at the true
            // drag-end. Combined with the debounce this keeps a whole pan to a single load + redraw
            // instead of one per intermediate settle, killing the mid-pan jank.
            .filter { !host.cameraInteracting.value }
            .filterNot { next -> stopRequestFulfilled(lastLoad, lastHadResponse, lastLimitExceeded, next) }
            .flatMapLatest { snapshot ->
                // flatMapLatest cancels an in-flight load when a newer viewport arrives, matching the
                // controller's `loadJob?.cancel()`; a cancelled load leaves lastLoad untouched (and
                // skips a superseded viewport's cache save).
                flow {
                    val regionId = regionRepo.region.value?.id
                    // Read the (prefs-backed) cache size and clock once — the cache read, the network
                    // request, and the cache save all share the same viewport's values.
                    val maxStops = cacheSize()
                    val loadTime = now()

                    // 1. Serve the cache first so stops render before (or without) the network. Skipped
                    // when there's no cache (slim maps) or no region resolved yet (cold start); never a
                    // region-less query, which would mix feeds. Best-effort: a cache read failure must
                    // fall through to the network, not kill the loader (guardCache below).
                    var servedCache = false
                    if (stopCache != null && regionId != null) {
                        val cached = guardCache("read") {
                            stopCache.stopsFor(
                                snapshot.center.latitude, snapshot.center.longitude,
                                snapshot.latSpan, snapshot.lonSpan, regionId, loadTime, maxStops,
                            )
                        }
                        if (cached != null && cached.stops.isNotEmpty()) {
                            servedCache = true
                            emit(StopLoad.Cached(cached, snapshot))
                        }
                    }

                    // 2. Network — the unchanged request + fulfillment-gate update.
                    host.setProgress(true)
                    val result = mapDataSource
                        .nearbyStops(snapshot.center.latitude, snapshot.center.longitude, snapshot.latSpan, snapshot.lonSpan, maxStops)
                    // Only a usable load updates the fulfillment gate: a success — OK stops, or a null
                    // no-op (e.g. no stops endpoint, which intentionally fulfills future same-center
                    // viewports). A failure (error code / transport) showed no stops, so leave the gate
                    // untouched — like a cancelled load — otherwise stopRequestFulfilled would treat this
                    // viewport as already satisfied and short-circuit the retry.
                    result.onSuccess { nearby ->
                        lastLoad = snapshot
                        lastHadResponse = nearby != null
                        lastLimitExceeded = nearby?.limitExceeded ?: false
                    }

                    // 3. Persist a usable, in-range, non-empty result (upsert + evict) for next time.
                    // Best-effort: a save failure must not abort the flow (leaving the spinner stuck).
                    if (stopCache != null && regionId != null) {
                        result.getOrNull()?.let { nearby ->
                            if (!nearby.outOfRange && nearby.stops.isNotEmpty()) {
                                guardCache("save") { stopCache.save(nearby, regionId, loadTime) }
                            }
                        }
                    }

                    emit(StopLoad.Network(result, servedCache, snapshot))
                }
                    // Run the query pipeline (cache read + row mapping, network, cache save) off the main
                    // thread; only the emitted results cross back to the collector, which does the (cheap,
                    // main-confined) stopAccum merge + publish. flatMapLatest still cancels this on a newer
                    // viewport.
                    .flowOn(Dispatchers.Default)
            }
            .collect { load ->
                when (load) {
                    // A cache emission renders immediately and deliberately bypasses onStopsLoaded, so
                    // the out-of-range / limit-exceeded / progress machinery stays network-only.
                    is StopLoad.Cached -> showCachedStops(load.viewport, load.snapshot)
                    is StopLoad.Network -> {
                        host.setProgress(false)
                        onStopsLoaded(load.result, load.servedCache, load.snapshot)
                    }
                }
            }
    }

    /**
     * Runs a persistent-cache [block] best-effort: a Room/SQLite failure is logged and swallowed
     * (returning null) so it can't escape the loader flow and cancel future viewport loads — the cache
     * is a nice-to-have, the network path is authoritative. Cancellation is rethrown so flatMapLatest
     * can still abandon a superseded viewport.
     */
    private suspend fun <T> guardCache(op: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stop cache $op failed", e)
            null
        }

    /**
     * One viewport's emission: a [Cached] render (served instantly from the persistent cache) followed
     * by the [Network] result. The two are keyed by stop id in [stopAccum], so the network render
     * merges/replaces the cached markers automatically (no separate reconciliation).
     */
    private sealed interface StopLoad {
        data class Cached(val viewport: CachedViewport, val snapshot: CameraSnapshot) : StopLoad

        /** [servedCache] = whether a [Cached] render preceded this in the same viewport load. */
        data class Network(
            val result: Result<NearbyStops?>,
            val servedCache: Boolean,
            val snapshot: CameraSnapshot,
        ) : StopLoad
    }

    /**
     * Render cached stops immediately: seed only the icon route-type lookup (never [cachedRoutes],
     * which feeds stop-tap route reporting and must reflect real loaded routes), then accumulate + publish
     * like [showStops]. A cache render is provisional (no cache-bust, [complete] = false); it deliberately
     * skips the out-of-range / limit-exceeded handling in [onStopsLoaded] — a cache render makes no claim
     * about the region or truncation.
     */
    private fun showCachedStops(cached: CachedViewport, snapshot: CameraSnapshot) {
        routeTypeById.putAll(cached.routeTypes)
        accumulateAndPublish(cached.stops, viewport = snapshot, complete = false)
    }

    private fun onStopsLoaded(result: Result<NearbyStops?>, servedCache: Boolean, snapshot: CameraSnapshot) {
        // Default the banner off for this load; the branches below turn it back on. Clearing once up
        // front means every early-out (error, null, out-of-range) leaves it cleared without having to
        // remember to — the class of bug that previously left the banner stuck on after a truncated
        // load was followed by a null one.
        host.setStopsBanner(StopsBanner.None)
        val nearby = result.getOrElse {
            // The load failed. If the cache already rendered stops for this viewport (offline), show the
            // "showing saved stops" banner instead of the generic error toast — the user has stops on
            // screen, and a per-pan toast would spam. A genuine failure with nothing cached still toasts.
            if (servedCache) host.setStopsBanner(StopsBanner.ShowingSavedStops)
            else host.emitEffect(MapEffect.ShowError.from(it))
            return
        }
        if (nearby == null) {
            // No endpoint yet (or a null no-op); no stops loaded, so do nothing (#615).
            return
        }
        if (nearby.outOfRange) {
            notifyOutOfRange()
            return
        }

        // Workaround for https://github.com/OneBusAway/onebusaway-application-modules/issues/59 where
        // the outOfRange element is false even if the location was out of range. We also make sure the
        // list of stops is empty, otherwise we'd screen out valid responses.
        val myLocation = locationRepository.location.value
        val region = regionRepo.region.value
        if (myLocation != null && region != null) {
            var inRegion = true // Assume user is in region unless we detect otherwise.
            try {
                inRegion = RegionUtils.isLocationWithinRegion(myLocation, region)
            } catch (e: IllegalArgumentException) {
                // Issue #69 - some devices are providing invalid lat/long coordinates.
                Log.e(
                    TAG, "Invalid latitude or longitude - lat = " + myLocation.latitude +
                            ", long = " + myLocation.longitude
                )
            }
            if (!inRegion && nearby.stops.isEmpty()) {
                Log.d(TAG, "Device location is outside region range, notifying...")
                notifyOutOfRange()
                return
            }
        }

        // More stops match the viewport than the API returned: prompt the user to zoom in, and treat
        // the response as an incomplete sample — merge it in (bounded to the nearest to centre) but do
        // NOT let it evict the cached stops it omitted. A complete response is authoritative, so it also
        // cache-busts stale stops the response dropped inside the viewport (#1754).
        val complete = !nearby.limitExceeded
        if (!complete) host.setStopsBanner(StopsBanner.MoreStopsAvailable)
        showStops(nearby.stops, nearby.routes, viewport = snapshot, complete = complete)
    }

    private fun notifyOutOfRange() {
        host.setStopsBanner(StopsBanner.None)
        host.emitEffect(MapEffect.OutOfRange)
    }

    // ----- Focus -----
    // This owns only the *render* focus (the 1.5x icon, via renderState.focusedStopId). The home-side
    // focus (the arrivals sheet + analytics) is driven directly by the owner's tap callback, so a re-tap
    // of the same stop isn't swallowed by state-dedup.

    /**
     * A stop marker was tapped: render-focus it (the old GoogleMapHost.onStopClick). Deliberately does
     * *not* move the camera — a tapped marker is already on screen, so recentering/zooming would only
     * throw away the user's pre-tap view with no way back.
     */
    fun onStopTapped(stop: ObaStop) {
        setFocusedStopId(stop.id)
    }

    /** A tap away from any marker clears the stop render focus (the old onMapClick). */
    fun clearStopFocus() {
        setFocusedStopId(null)
    }

    /**
     * Programmatic focus for a restored/deep-linked stop once its arrivals load (driven by
     * `MapDirective.FocusStop`): ensure the stop is on the map + render-focused, and center on it
     * (route-header bias only when [routeActive] and the sheet settled expanded).
     */
    fun focusStop(
        stop: ObaStop,
        routes: List<ObaRoute>?,
        overlayExpanded: Boolean,
        recenter: Boolean = true,
    ) {
        if (recenter) {
            val loc = stop.location
            host.dispatchGesture(
                CameraCommand.Recenter(
                    loc.latitude, loc.longitude,
                    animate = false,
                    applyRouteBias = routeActive() && overlayExpanded,
                )
            )
        }
        setFocusStop(stop, routes)
    }

    /** Clear the render focus (back-press from a peeking sheet; a `MapDirective.ClearFocus`). */
    fun clearFocus() {
        setFocusStop(null, null)
    }

    // ----- Stops -----

    /** Accumulate ordinary nearby [stops]; route styling is applied separately by [setRoutePresentation]. */
    fun showStops(
        stops: List<ObaStop>,
        routes: List<ObaRoute>,
        viewport: CameraSnapshot? = null,
        complete: Boolean = false,
    ) {
        cacheRoutes(routes)
        accumulateAndPublish(stops, viewport, complete)
    }

    /**
     * Merge [stops] into [stopAccum], reconcile against [viewport], and publish — the tail shared by the
     * nearby-stops loader ([showStops]) and the cache render ([showCachedStops]); the caller seeds the
     * route lookup first.
     *
     * [viewport], when non-null, bounds the accumulation to the stops nearest its centre. [complete]
     * additionally marks an authoritative response: stops it omitted inside the viewport are cache-busted.
     */
    private fun accumulateAndPublish(
        stops: List<ObaStop>,
        viewport: CameraSnapshot? = null,
        complete: Boolean = false,
    ) {
        for (stop in stops) {
            val marker = toStopMarker(stop)
            val existing = stopAccum[stop.id]
            // Reuse the existing instance when its style + position are unchanged, so a stationary re-poll
            // of the same set yields an equal list the StateFlow conflates and the renderer never runs.
            // Replace it when a mode switch flipped the stop's route-circle vs nearby style/point —
            // otherwise a retained (focused) stop would keep its pre-switch icon. (Favorites are re-synced
            // separately by applyFavorites, so they're not part of this reuse test.)
            stopAccum[stop.id] =
                if (existing != null && existing.routeStop == marker.routeStop && existing.point == marker.point) existing
                else marker
        }
        if (viewport == null) {
            publishStops()
            return
        }
        val focusedId = renderState.snapshot.value.focusedStopId
        // An authoritative (complete) response drops stops it omitted inside the viewport. An incomplete
        // one is a sample, so it evicts nothing (#1754).
        if (complete) {
            evictStaleInViewport(
                stopAccum, viewport.southWest, viewport.northEast, stops.mapTo(HashSet()) { it.id }, focusedId,
            )
        }
        // Bound to the nearest to centre (never evicting the centred core against an incomplete sample).
        trimToNearest(stopAccum, viewport.center, cacheSize(), focusedId)
        publishStops()
    }

    /** Clears accumulated stops; keeps the focused one unless [clearFocusedStop]. */
    fun clearStops(clearFocusedStop: Boolean) {
        if (clearFocusedStop) {
            stopAccum.clear()
            renderState.setFocusedStopId(null)
        } else {
            retainOnlyFocusedStop(stopAccum, renderState.snapshot.value.focusedStopId)
        }
        publishStops()
    }

    /** Programmatic focus (intent/rotation): ensures the stop is on the map, then focuses it. */
    fun setFocusStop(stop: ObaStop?, routes: List<ObaRoute>?) {
        if (stop == null) {
            renderState.setFocusedStopId(null)
            return
        }
        if (!stopAccum.containsKey(stop.id)) {
            routes?.let { cacheRoutes(it) }
            stopAccum[stop.id] = toStopMarker(stop)
            publishStops()
        }
        renderState.setFocusedStopId(stop.id)
    }

    fun setFocusedStopId(stopId: String?) = renderState.setFocusedStopId(stopId)

    /** A snapshot copy of the cached routes, for reporting a stop's routes to focus listeners. */
    fun cachedRoutes(): HashMap<String, ObaRoute> = HashMap(cachedRoutes)

    /** Adds routes to the caches that a stop tap reports + the icon route-type lookup. */
    private fun cacheRoutes(routes: Iterable<ObaRoute>) {
        for (route in routes) {
            cachedRoutes[route.id] = route
            routeTypeById[route.id] = route.type
        }
    }

    private fun toStopMarker(stop: ObaStop): StopMarker {
        val routeType = primaryRouteType(stop.routeIds, routeTypeById)
        // ObaStop.getDirection() is "N".."NW" or the literal "null" string for no direction.
        val direction = stop.direction ?: "null"
        return StopMarker(
            stop.id, stop.location.toGeoPoint(), direction, routeType, stop,
            favorite = stop.id in favoriteIds,
        )
    }

    /** Rebuild the rendered marker list from canonical nearby data and the current route presentation. */
    private fun publishStops() {
        val presentation = routePresentation
        if (presentation == null) {
            renderState.setStops(ArrayList(stopAccum.values))
            return
        }
        renderState.setStops(
            applyRouteStopPresentation(
                nearby = stopAccum.values,
                focusedStopId = renderState.snapshot.value.focusedStopId,
                presentation = presentation,
                markerFor = ::toStopMarker,
            )
        )
    }

    companion object {
        private const val TAG = "StopsMapController"

        /** Default in-memory stop cap (nearest-to-centre); overridable via the advanced cache-size option. */
        const val DEFAULT_STOP_CACHE_SIZE = 200
    }
}

internal data class RouteStopPresentation(
    val stops: List<ObaStop>,
    val routes: List<ObaRoute>,
    val routeStopIds: Set<String>,
    val projectedPoints: Map<String, GeoPoint>,
)

/** Pure marker merge/style policy shared by base-route and exact-trip stop presentations. */
internal fun applyRouteStopPresentation(
    nearby: Collection<StopMarker>,
    focusedStopId: String?,
    presentation: RouteStopPresentation,
    markerFor: (ObaStop) -> StopMarker,
): List<StopMarker> {
    val source = LinkedHashMap<String, StopMarker>()
    nearby.firstOrNull { it.id == focusedStopId }?.let { source[it.id] = it }
    presentation.stops.forEach { source.putIfAbsent(it.id, markerFor(it)) }
    return source.values.map { marker ->
        val routeStop = marker.id in presentation.routeStopIds
        marker.copy(
            point = presentation.projectedPoints[marker.id] ?: marker.point,
            routeStop = routeStop,
        )
    }
}
