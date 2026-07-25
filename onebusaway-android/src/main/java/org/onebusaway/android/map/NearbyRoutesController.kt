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
import org.onebusaway.android.map.render.haversineMeters
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.getRouteDisplayName

/**
 * The "routes near here" hoop (#2004): while the home map is showing the plain base map, draws a
 * fixed half-mile ring around where the camera settled and, lightly, the full shape of every route
 * that passes through it — each route's directions sharing one colour, each labelled inside the hoop
 * with a tappable badge that enters route focus. The hoop selects; it doesn't crop, so the layer shows
 * where the routes running past you actually go. It gives the otherwise-spare base map some
 * situational awareness, reusing the minimal presentation focused-stop adjacency established (#1827).
 *
 * **Where the routes come from.** The route *set* is derived from the nearby-stop layer the map has
 * already loaded (see [nearbyRouteIds] for why that is the same set `routes-for-location` defines,
 * and why deriving it also gives the cap a meaningful ranking), so the layer adds no stop query of its
 * own. Each drawn route costs one `stops-for-route` fetch for its shape, through the shared cache
 * ([RouteMapRepository] over `StopsForRouteRepository`), so a route already opened this session — or
 * already drawn by a neighbouring hoop — costs nothing.
 *
 * **When it refreshes.** The hoop stays anchored where it was last surveyed until the camera drifts
 * [NEARBY_ROUTES_REFRESH_METERS] away; a smaller pan neither moves the ring nor re-queries, so the
 * ring always marks the area the drawn routes were actually gathered from. It hides entirely below
 * [NEARBY_ROUTES_MIN_ZOOM] (where a half-mile circle is a speck and the route density is unreadable)
 * and while a stop is focused (stop-focus adjacency owns the route geometry then).
 *
 * A cold driver over a [MapHost] like the other use-case controllers: it reacts to [MapHost.camera]
 * plus the published stop layer and writes [MapHost.renderState], with no map-SDK dependency.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NearbyRoutesController(
    private val host: MapHost,
    private val routeRepository: RouteMapRepository,
    // The route metadata (name + colour) the map has cached for the loaded stops — the nearby-stop
    // layer's own catalog, so the badge label costs no extra fetch. A route whose metadata hasn't
    // landed yet is skipped and picked up on the next refresh.
    private val routesById: () -> Map<String, ObaRoute>,
    private val scope: CoroutineScope
) {

    private val renderState get() = host.renderState

    private var job: Job? = null

    // The hue assigned to each drawn route, retained across refreshes so a route that stays in the
    // hoop keeps its colour as neighbours come and go (the same retention adjacency uses). Keyed by
    // route id, not route+direction: every direction of a route shares one colour (#2004).
    private var colors: Map<String, Int> = emptyMap()

    /** Start drawing the hoop layer (the map entered the plain base-map view). */
    fun start() {
        job?.cancel()
        job = launchLoader()
    }

    /** Stop the layer and clear it (any focus taking over, or leaving the base map). */
    fun stop() {
        job?.cancel()
        job = null
        colors = emptyMap()
        renderState.clearNearbyRoutes()
    }

    private fun launchLoader(): Job = scope.launch {
        // The hoop's anchor, held across emissions: kept until the camera drifts far enough (or the
        // layer hides), so pans smaller than a refresh neither move the ring nor re-query.
        var hoop: NearbyRoutesHoop? = null

        combine(
            host.camera.filterNotNull(),
            // The published stop layer is the route set's source, so a fresh viewport load is a
            // trigger just like a camera move; the focused-stop flag hides the layer. Mapped to just
            // those two before dedup, so the controller's own writes to the snapshot can't re-trigger it.
            renderState.snapshot.map { it.stops to (it.focusedStopId != null) }.distinctUntilChanged()
        ) { camera, (stops, stopFocused) -> Triple(camera, stops, stopFocused) }
            .debounce(NEARBY_ROUTES_DEBOUNCE_MS)
            // Settle on drag-end, like the stop loader: a viewport reached mid-gesture is superseded
            // by the gesture's own terminating idle.
            .filter { !host.cameraInteracting.value }
            .map { (camera, stops, stopFocused) ->
                if (stopFocused || camera.zoom < NEARBY_ROUTES_MIN_ZOOM) {
                    // Drop the anchor as well: re-entering the layer should survey afresh from
                    // wherever the camera then is, not resume the pre-hide hoop.
                    hoop = null
                    return@map null
                }
                val anchored = hoop
                    ?.takeIf { haversineMeters(it.center, camera.center) < NEARBY_ROUTES_REFRESH_METERS }
                    ?: NearbyRoutesHoop(camera.center, NEARBY_ROUTES_RADIUS_METERS).also { hoop = it }
                val routeIds = nearbyRouteIds(anchored, stops, MAX_NEARBY_ROUTES)
                NearbyRoutesRequest(
                    hoop = anchored,
                    routeIds = routeIds,
                    colors = adjacencyRouteColors(routeIds, retained = colors).also { colors = it }
                )
            }
            // An unchanged hoop + route set is the common case as stops trickle in; don't re-run the
            // fetch/clip pipeline for it.
            .distinctUntilChanged()
            .flatMapLatest { request -> request?.let(::presentations) ?: flowOf(null) }
            .collect { presentation ->
                renderState.setNearbyRoutes(
                    presentation?.polylines.orEmpty(),
                    presentation?.badges.orEmpty()
                )
            }
    }

    /**
     * The layer's presentations for one [request], emitted progressively: the bare ring first (so the
     * hoop appears the moment the camera settles rather than after the slowest shape), then a fresh
     * plan as each route's shape resolves. A request with no routes in it draws nothing at all.
     * Superseded by [flatMapLatest] when a newer request arrives, which cancels any shape fetch still
     * in flight.
     *
     * Runs on [Dispatchers.Default]: testing a dozen whole-route shapes against the hoop (for
     * membership and badge anchors) is real CPU work and must not land on the frame-producing main
     * thread.
     */
    private fun presentations(request: NearbyRoutesRequest): Flow<NearbyRoutesPresentation> = flow {
        if (request.routeIds.isEmpty()) {
            // Nothing runs through here — or the stop layer hasn't reached this hoop yet. Draw nothing
            // at all: a bare ring around an empty map would claim a survey we haven't made.
            emit(NearbyRoutesPresentation(emptyList(), emptyList()))
            return@flow
        }
        val metadata = routesById()
        val drawn = mutableListOf<NearbyRouteShapes>()
        emit(assembleNearbyRoutesPresentation(request.hoop, drawn, request.colors))
        val permits = Semaphore(MAX_CONCURRENT_NEARBY_ROUTE_FETCHES)
        coroutineScope {
            val fetches = request.routeIds.map { routeId ->
                async { permits.withPermit { loadShapes(routeId, metadata) } }
            }
            // Awaited in request order (nearest route first) so the layer fills in from the centre out.
            for (fetch in fetches) {
                val route = fetch.await() ?: continue
                drawn += route
                emit(assembleNearbyRoutesPresentation(request.hoop, drawn, request.colors))
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * One route's whole-route (all-directions) shape plus the label its badge shows, or null when the
     * route can't be drawn: no endpoint / a failed fetch, no shape on the wire, or no display name in
     * either the map's route catalog or the fetched route itself. A failure is logged and skipped —
     * one unreachable route must not blank the whole ambient layer.
     */
    private suspend fun loadShapes(routeId: String, metadata: Map<String, ObaRoute>): NearbyRouteShapes? {
        val route = routeRepository.getRoute(routeId)
            .onFailure { Log.w(TAG, "Nearby route $routeId shape fetch failed", it) }
            .getOrNull()
            ?: return null
        val shapes = route.polylines.filter { it.size >= 2 }
        if (shapes.isEmpty()) return null
        val name = (metadata[routeId] ?: route.route)
            ?.let(::getRouteDisplayName)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return NearbyRouteShapes(routeId, name, shapes)
    }

    private companion object {
        const val TAG = "NearbyRoutesController"
    }
}

/** One survey of the hoop: where it sits, which routes to draw in it, and the hue each one takes. */
private data class NearbyRoutesRequest(
    val hoop: NearbyRoutesHoop,
    val routeIds: List<String>,
    val colors: Map<String, Int>
)

/**
 * Below this zoom the layer hides: a half-mile hoop is a speck on screen, and the routes inside it are
 * too dense and too thin to read. A display decision, tunable to taste.
 */
private const val NEARBY_ROUTES_MIN_ZOOM = 14.0

/**
 * How far the camera centre must drift from the surveyed hoop before it is re-anchored and re-queried
 * — half the hoop's own radius, so the ring is never showing an area the camera has largely left, and
 * an ordinary nudge of the map costs nothing.
 */
private const val NEARBY_ROUTES_REFRESH_METERS = 400.0

/** Matches the stop loader's settle window: one survey per pan, not one per intermediate idle. */
private const val NEARBY_ROUTES_DEBOUNCE_MS = 400L

/** Ambient context must not monopolize the connection; the same ceiling the focused-stop shapes use. */
private const val MAX_CONCURRENT_NEARBY_ROUTE_FETCHES = 2
