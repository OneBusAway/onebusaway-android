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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.getRouteDisplayName

/**
 * The "routes near here" hoop (#2004): while the home map is showing the plain base map, draws a
 * fixed half-mile ring around where the camera settled and, lightly, the full shape of every route
 * that passes through it — each route's directions sharing one colour, each labelled inside the hoop
 * with a tappable badge that enters route focus. The hoop selects; it doesn't crop, so the layer shows
 * where the routes running past you actually go. It gives the otherwise-spare base map some
 * situational awareness, reusing the minimal presentation focused-stop adjacency established (#1827).
 *
 * **Where the routes come from.** Each settle asks OBA the question directly: `routes-for-location`
 * over the hoop's circle ([MapDataSource.routesNearby]). That's one small response — route references,
 * not the hundreds of stops behind them — and it's zoom-independent, which the map's viewport stop
 * layer is not (once you pull back, that layer is a truncated, server-shuffled citywide sample that
 * says almost nothing about any particular half mile, and the hoop has to mean the same thing at every
 * zoom).
 *
 * The one thing that endpoint can't do is choose *which* routes when more run through the hoop than
 * [MAX_NEARBY_ROUTES]: it returns an unordered set with no distances. Only in that case does the layer
 * also run a stops-for-location query over the hoop, to rank by nearest stop (see
 * [rankRoutesByNearestStop]) — so the extra query is paid for exactly where it buys something, and
 * most places never pay it. Each drawn route then costs one `stops-for-route` fetch for its shape,
 * through the shared cache ([RouteMapRepository] over `StopsForRouteRepository`), so a route already
 * opened this session — or already drawn by a neighbouring hoop — costs nothing.
 *
 * **When it refreshes.** The ring is drawn in screen space at the centre of the viewport (see
 * [hoop] and `hoopRadiusDp`), so a drag carries it along with the gesture for free — but the *survey*
 * fires only once the camera settles, at the settled centre, so panning never turns into a burst of
 * queries. It stays on well past a citywide view — down to [NEARBY_ROUTES_MIN_ZOOM], where the routes
 * near you are drawn across the whole city and the badges spread along them rather than crowding the
 * (by then tiny) ring. It hides while a stop is focused, where stop-focus adjacency owns the route
 * geometry.
 *
 * A cold driver over a [MapHost] like the other use-case controllers: it reacts to [MapHost.camera]
 * plus the published stop layer and writes [MapHost.renderState], with no map-SDK dependency.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NearbyRoutesController(
    private val host: MapHost,
    private val mapDataSource: MapDataSource,
    private val routeRepository: RouteMapRepository,
    private val scope: CoroutineScope
) {

    private val renderState get() = host.renderState

    private var job: Job? = null

    private val _hoop = MutableStateFlow<NearbyRoutesHoop?>(null)

    /**
     * The hoop being shown, or null when the layer is off. The ring is *not* map geometry: the map
     * overlay draws it in screen space at the centre of the viewport, so a drag slides it with the
     * gesture instead of leaving it planted on the ground. Only the radius (constant) and whether the
     * layer is on are read from here — the centre stays for the survey the drawn routes came from.
     */
    val hoop: StateFlow<NearbyRoutesHoop?> = _hoop.asStateFlow()

    // The hue assigned to each drawn route, retained across refreshes so a route that stays in the
    // hoop keeps its colour as neighbours come and go (the same retention adjacency uses). Keyed by
    // route id, not route+direction: every direction of a route shares one colour (#2004).
    private var colors: Map<String, Int> = emptyMap()

    /** Start drawing the hoop layer (the map entered the plain base-map view). */
    fun start() {
        job?.cancel()
        // Reset the palette here rather than in [stop], so the field is only ever touched by the
        // loader coroutine that follows (and never races a survey still winding down).
        colors = emptyMap()
        job = launchLoader()
    }

    /** Stop the layer and clear it (any focus taking over, or leaving the base map). */
    fun stop() {
        job?.cancel()
        job = null
        _hoop.value = null
        renderState.clearNearbyRoutes()
    }

    private fun launchLoader(): Job = scope.launch {
        combine(
            host.camera.filterNotNull(),
            // Stop focus hides the layer. Mapped to just that flag before dedup, so the controller's
            // own writes to the snapshot can't re-trigger it.
            renderState.snapshot.map { it.focusedStopId != null }.distinctUntilChanged()
        ) { camera, stopFocused -> camera to stopFocused }
            .debounce(NEARBY_ROUTES_DEBOUNCE_MS)
            // Settle on drag-end, like the stop loader: a viewport reached mid-gesture is superseded
            // by the gesture's own terminating idle.
            .filter { !host.cameraInteracting.value }
            .map { (camera, stopFocused) ->
                if (stopFocused || camera.zoom < NEARBY_ROUTES_MIN_ZOOM) {
                    _hoop.value = null
                    return@map null
                }
                // The camera has settled, so survey from exactly where it came to rest — which is
                // also where the overlay has been drawing the ring all through the drag. No drift
                // threshold: the two must agree at rest, and a settle costs one small query.
                val hoop = NearbyRoutesHoop(camera.center, NEARBY_ROUTES_RADIUS_METERS)
                _hoop.value = hoop
                NearbyRoutesRequest(
                    hoop = hoop,
                    badgesInHoop = badgesFitInHoop(
                        hoopRadiusDp(hoop.radiusMeters, camera.zoom, camera.center.latitude)
                    )
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { request -> request?.let(::survey) ?: flowOf(null) }
            .collect { presentation ->
                renderState.setNearbyRoutes(
                    presentation?.polylines.orEmpty(),
                    presentation?.badges.orEmpty()
                )
            }
    }

    /**
     * The layer's presentations for one [request], emitted progressively as each route's shape
     * resolves. It deliberately does *not* open with an empty plan: since every settle re-surveys, that
     * would blink the whole layer off and back on after each pan. The previous survey's routes stay on
     * screen until the first route of this one lands, and an empty plan is emitted only when this
     * survey genuinely has nothing to draw. Superseded by [flatMapLatest] when a newer request
     * arrives, which cancels any shape fetch still in flight.
     *
     * Runs on [Dispatchers.Default]: testing a dozen whole-route shapes against the hoop (for
     * membership and badge anchors) is real CPU work and must not land on the frame-producing main
     * thread.
     */
    private fun survey(request: NearbyRoutesRequest): Flow<NearbyRoutesPresentation> = flow {
        // The routes running through the hoop. A failed query leaves the previous survey on screen
        // rather than blanking the layer over one dropped request.
        val routes = routesInHoop(request.hoop) ?: return@flow
        if (routes.isEmpty()) {
            // Nothing runs through here. Draw nothing at all.
            emit(NearbyRoutesPresentation(emptyList(), emptyList()))
            return@flow
        }
        val palette = adjacencyRouteColors(routes.map(ObaRoute::id), retained = colors).also { colors = it }
        val drawn = mutableListOf<NearbyRouteShapes>()
        val permits = Semaphore(MAX_CONCURRENT_NEARBY_ROUTE_FETCHES)
        coroutineScope {
            val fetches = routes.map { route ->
                async { permits.withPermit { loadShapes(route) } }
            }
            // Awaited in request order (nearest route first) so the layer fills in from the centre out.
            for (fetch in fetches) {
                val route = fetch.await() ?: continue
                drawn += route
                emit(assembleNearbyRoutesPresentation(request.hoop, drawn, palette, request.badgesInHoop))
            }
        }
        // Every candidate failed to load or missed the hoop: clear, rather than stranding the previous
        // survey's routes on screen.
        if (drawn.isEmpty()) emit(NearbyRoutesPresentation(emptyList(), emptyList()))
    }.flowOn(Dispatchers.Default)

    /**
     * The routes to draw for [hoop], nearest first, at most [MAX_NEARBY_ROUTES] — or null when the
     * query failed / there is no endpoint yet, which leaves the previous survey on screen rather than
     * clearing the layer over one dropped request. Failures are logged and swallowed: this is ambient
     * context, not something to toast about.
     *
     * Answered by `routes-for-location` alone whenever the hoop holds no more routes than the layer
     * will draw — the usual case. Past that the choice of *which* has to come from somewhere, so a
     * stops-for-location query over the hoop supplies the nearest-stop ranking; if that second query
     * fails, the routes fall back to id order, which at least keeps the drawn set stable between
     * settles instead of reshuffling.
     */
    private suspend fun routesInHoop(hoop: NearbyRoutesHoop): List<ObaRoute>? {
        val routes = mapDataSource
            .routesNearby(hoop.center.latitude, hoop.center.longitude, hoop.radiusMeters.toInt())
            .onFailure { Log.w(TAG, "Nearby-routes query failed", it) }
            .getOrNull()
            ?: return null
        if (routes.size <= MAX_NEARBY_ROUTES) return routes
        val stops = stopsInHoop(hoop) ?: return routes.sortedBy(ObaRoute::id).take(MAX_NEARBY_ROUTES)
        return rankRoutesByNearestStop(hoop, routes, stops, MAX_NEARBY_ROUTES)
    }

    /** The stops inside [hoop]'s bounding box — the ranking input, fetched only when it's needed. */
    private suspend fun stopsInHoop(hoop: NearbyRoutesHoop): List<ObaStop>? {
        val (latSpan, lonSpan) = hoop.spanDegrees()
        return mapDataSource
            .nearbyStops(hoop.center.latitude, hoop.center.longitude, latSpan, lonSpan, HOOP_STOP_LIMIT)
            .onFailure { Log.w(TAG, "Nearby-routes ranking stop query failed", it) }
            .getOrNull()
            ?.stops
    }

    /**
     * One route's whole-route (all-directions) shape plus the label its badge shows, or null when the
     * route can't be drawn: no endpoint / a failed fetch, no shape on the wire, or no display name on
     * the fetched route. A failure is logged and skipped — one unreachable route must not blank the
     * whole ambient layer.
     */
    private suspend fun loadShapes(route: ObaRoute): NearbyRouteShapes? {
        val loaded = routeRepository.getRoute(route.id)
            .onFailure { Log.w(TAG, "Nearby route ${route.id} shape fetch failed", it) }
            .getOrNull()
            ?: return null
        val shapes = loaded.polylines.filter { it.size >= 2 }
        if (shapes.isEmpty()) return null
        // The name comes from the routes-for-location reference; the loaded route is the fallback for
        // a reference that carried neither a short nor a long name.
        val name = getRouteDisplayName(route).takeIf(String::isNotBlank)
            ?: loaded.route?.let(::getRouteDisplayName)?.takeIf(String::isNotBlank)
            ?: return null
        return NearbyRouteShapes(route.id, name, shapes)
    }

    private companion object {
        const val TAG = "NearbyRoutesController"
    }
}

/** One survey of the hoop: where it sits, and whether its ring has room to hold the badges. */
private data class NearbyRoutesRequest(
    val hoop: NearbyRoutesHoop,
    val badgesInHoop: Boolean
)

/**
 * Below this zoom the layer hides. Set well past a citywide view (zoom 11 is roughly a city across a
 * phone screen) so you can pull back and see where the routes running past you actually go; below it
 * the survey is a half mile in a viewport tens of kilometres wide, which stops meaning anything. A
 * display decision, tunable to taste.
 */
private const val NEARBY_ROUTES_MIN_ZOOM = 11.0

/**
 * The cap on the ranking stop query. Generous, because it covers only the hoop's bounding box (a mile
 * square) rather than the viewport: the server truncates by *shuffling*, so a query that fits under
 * the cap is also a stable one. It degrades gently past it anyway — the route *set* is already known
 * from routes-for-location, so a partial stop sample only costs precision in the ordering.
 */
private const val HOOP_STOP_LIMIT = 250

/** Matches the stop loader's settle window: one survey per pan, not one per intermediate idle. */
private const val NEARBY_ROUTES_DEBOUNCE_MS = 400L

/** Ambient context must not monopolize the connection; the same ceiling the focused-stop shapes use. */
private const val MAX_CONCURRENT_NEARBY_ROUTE_FETCHES = 2
