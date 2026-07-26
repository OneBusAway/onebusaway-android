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
import kotlinx.coroutines.flow.emptyFlow
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
import org.onebusaway.android.api.data.StopsForRouteRepository
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.getRouteDisplayName

/**
 * The "routes near here" hoop (#2004): while the home map is showing the plain base map, draws a ring
 * around where the camera settled holding the [TARGET_NEARBY_ROUTES] routes nearest it, and, lightly,
 * the full shape of each — directions sharing one colour, each carrying a tappable badge that enters
 * route focus. The hoop selects; it doesn't crop, so the layer shows where the routes running past you
 * actually go, and the badges ride the lines out across the map rather than stacking inside the ring.
 * It gives the otherwise-spare base map some situational awareness, reusing the minimal presentation
 * focused-stop adjacency established (#1827).
 *
 * **The ring is scaled to the routes, not fixed.** A half-mile ring made the layer's density a property
 * of the neighbourhood — fifty routes downtown, four on a residential street — so it was unreadable in
 * one place and empty in the other. Each settle instead searches outward from the settled centre until
 * it has gathered the target, and the ring is drawn at whatever radius that took ([nearestRoutes]). What
 * it means is "this is how far the nearest routes are", not a walkshed: where transit is sparse it can
 * reach kilometres.
 *
 * **Where the routes come from.** A `stops-for-location` box around the centre, whose stops carry the
 * coordinates the ranking needs and whose reference pool names every route they serve — so one query
 * answers both which routes are near and how near they are. (`routes-for-location` can't: it reports an
 * unordered set with no distances, and caps out at fifty.) Each drawn route then costs one shape, taken
 * from the shared shapes cache ([StopsForRouteRepository.routeShapes]), so a route already drawn by a
 * neighbouring hoop — or opened on the route screen this session — costs nothing; the rest are fetched a
 * couple at a time ([MAX_CONCURRENT_NEARBY_ROUTE_FETCHES]) and the layer fills in as they land.
 *
 * **When it refreshes.** The ring is drawn in screen space at the centre of the viewport (see
 * [hoop] and `hoopRadiusDp`), so a drag carries it along with the gesture for free — but the *survey*
 * fires only once the camera settles, at the settled centre, so panning never turns into a burst of
 * queries. It stays on well past a citywide view — down to [NEARBY_ROUTES_MIN_ZOOM], where the routes
 * near you are drawn across the whole city. It hides while a stop is focused, where stop-focus
 * adjacency owns the route geometry.
 *
 * A cold driver over a [MapHost] like the other use-case controllers: it reacts to [MapHost.camera]
 * plus the published stop layer and writes [MapHost.renderState], with no map-SDK dependency.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NearbyRoutesController(
    private val host: MapHost,
    private val mapDataSource: MapDataSource,
    private val stopsForRoute: StopsForRouteRepository,
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
        host.cameraInteracting
            .flatMapLatest { interacting ->
                // The whole settled pipeline exists only between gestures, so the moment the user
                // takes the camera it is cancelled outright — along with whatever survey was in
                // flight and every shape fetch it had running. A survey for where the map *was* is
                // wasted work the instant a pan starts; without this it would keep fetching until the
                // gesture ended and the next settle superseded it, which on a long drag is seconds of
                // requests for a viewport already gone.
                //
                // [emptyFlow] and not a null: emitting nothing leaves the routes already drawn on
                // screen (the layer never blinks off mid-gesture), where a null would clear them.
                if (interacting) emptyFlow() else settledSurveys()
            }
            .collect { presentation ->
                renderState.setNearbyRoutes(presentation.polylines, presentation.badges)
            }
    }

    /**
     * Surveys driven by the camera coming to rest — the layer's normal cadence, subscribed afresh
     * after each gesture. Stop focus, or zooming out past the layer, yields [NO_NEARBY_ROUTES].
     *
     * Re-subscribing per gesture is what makes an interrupted survey recoverable: [host.camera] is a
     * StateFlow, so a fresh collection replays where the camera came to rest, and the
     * [distinctUntilChanged] below starts empty rather than remembering the hoop whose survey was just
     * cancelled. A gesture that returns the camera to exactly where it started therefore re-surveys and
     * completes the layer, instead of being dedup'd away and leaving it half-drawn.
     */
    private fun settledSurveys(): Flow<NearbyRoutesPresentation> = combine(
        host.camera.filterNotNull(),
        // Stop focus hides the layer. Mapped to just that flag before dedup, so the controller's
        // own writes to the snapshot can't re-trigger it.
        renderState.snapshot.map { it.focusedStopId != null }.distinctUntilChanged()
    ) { camera, stopFocused -> camera to stopFocused }
        // Coalesce the intermediate idles a fling emits after the gesture proper has ended.
        .debounce(NEARBY_ROUTES_DEBOUNCE_MS)
        .map { (camera, stopFocused) ->
            if (stopFocused || camera.zoom < NEARBY_ROUTES_MIN_ZOOM) {
                _hoop.value = null
                return@map null
            }
            // The camera has settled, so survey from exactly where it came to rest — which is also
            // where the overlay has been drawing the ring all through the drag. No drift threshold:
            // the two must agree at rest. Only the centre is known here; how far the ring reaches is
            // whatever the survey has to travel to gather its routes.
            camera.center
        }
        .distinctUntilChanged()
        .flatMapLatest { center -> center?.let(::survey) ?: flowOf(NO_NEARBY_ROUTES).also { _hoop.value = null } }

    /**
     * The layer's presentations for one [hoop], emitted progressively as each route's shape
     * resolves. It deliberately does *not* open with an empty plan: since every settle re-surveys, that
     * would blink the whole layer off and back on after each pan. The previous survey's routes stay on
     * screen until the first route of this one lands, and an empty plan is emitted only when this
     * survey genuinely has nothing to draw. Superseded by [flatMapLatest] when a newer request
     * arrives, which cancels any shape fetch still in flight.
     *
     * Runs on [Dispatchers.Default]: ranking the stops and laying the badges out along whole-route
     * shapes is real CPU work, so it must not land on the frame-producing main thread.
     */
    private fun survey(center: GeoPoint): Flow<NearbyRoutesPresentation> = flow {
        // The nearest routes, and how far the hoop had to reach to gather them. A failed query leaves
        // the previous survey on screen rather than blanking the layer over one dropped request.
        val nearest = nearestRoutes(center) ?: return@flow
        _hoop.value = NearbyRoutesHoop(center, nearest.radiusMeters)
        val routes = nearest.routes
        if (routes.isEmpty()) {
            // Nothing runs anywhere near here. Draw nothing at all.
            emit(NO_NEARBY_ROUTES)
            return@flow
        }
        val palette = adjacencyRouteColors(routes.map(ObaRoute::id), retained = colors).also { colors = it }
        val drawn = mutableListOf<NearbyRouteShapes>()
        val permits = Semaphore(MAX_CONCURRENT_NEARBY_ROUTE_FETCHES)
        coroutineScope {
            val fetches = routes.map { route ->
                async { permits.withPermit { loadShapes(route) } }
            }
            // Awaited in request order, so the layer fills in deterministically rather than in
            // whatever order the shape fetches happen to return. Emitting per route is cheap for the
            // renderers to absorb: both the lines and the badges are reconciled against what's already
            // drawn, so one more route adds one line and one badge rather than rebuilding the layer.
            for (fetch in fetches) {
                val route = fetch.await() ?: continue
                drawn += route
                emit(assembleNearbyRoutesPresentation(drawn, palette))
            }
        }
        // Every selected route failed to load a shape: clear, rather than stranding the previous
        // survey's routes on screen.
        if (drawn.isEmpty()) emit(NO_NEARBY_ROUTES)
    }.flowOn(Dispatchers.Default)

    /**
     * The routes nearest [center] and the radius that gathers them, or null when the query failed /
     * there is no endpoint yet — which leaves the previous survey on screen rather than clearing the
     * layer over one dropped request. Failures are logged and swallowed: this is ambient context, not
     * something to toast about.
     *
     * Searches outward rather than over one fixed box, so the dense case — where the ring will end up
     * closing in on the nearest [TARGET_NEARBY_ROUTES] anyway — is answered by a small query.
     * Starting small and growing only when short also keeps the ranking *exact* where it matters — stops-for-location truncates a wide box
     * (and says so via `limitExceeded`), but a wide box is only ever needed where stops are sparse
     * enough to come back whole. The dense case, where truncation would bite, is answered by the first
     * and smallest query.
     */
    private suspend fun nearestRoutes(center: GeoPoint): NearestRoutes? {
        var searchRadius = INITIAL_SEARCH_RADIUS_METERS
        var ranked = emptyList<RankedRoute>()
        while (true) {
            ranked = rankNearby(center, searchRadius) ?: return null
            // Enough found, or the search has reached the ceiling — past which nothing would be drawn
            // anyway, so widening further would only cost a query.
            if (ranked.size >= TARGET_NEARBY_ROUTES || searchRadius >= MAX_HOOP_RADIUS_METERS) break
            searchRadius = (searchRadius * SEARCH_EXPANSION).coerceAtMost(MAX_HOOP_RADIUS_METERS)
        }
        val radius = hoopRadiusForTarget(ranked, TARGET_NEARBY_ROUTES, MAX_HOOP_RADIUS_METERS)
        return NearestRoutes(
            radiusMeters = radius,
            // Everything inside the radius, ties included, so no route is cut arbitrarily.
            routes = ranked.filter { it.meters <= radius }.map(RankedRoute::route)
        )
    }

    /** One outward step: the routes served by the stops within [searchRadius], nearest first. */
    private suspend fun rankNearby(center: GeoPoint, searchRadius: Double): List<RankedRoute>? {
        val (latSpan, lonSpan) = NearbyRoutesHoop(center, searchRadius).spanDegrees()
        val nearby = mapDataSource
            .nearbyStops(center.latitude, center.longitude, latSpan, lonSpan, SEARCH_STOP_LIMIT)
            .onFailure { Log.w(TAG, "Nearby-routes stop query failed", it) }
            .getOrNull()
            ?: return null
        if (nearby.limitExceeded) {
            // The ranking is off a partial sample, so a route's "nearest" stop may not be its nearest.
            Log.d(TAG, "Nearby-routes ranking truncated at ${searchRadius.toInt()}m from $center")
        }
        return rankRoutesByNearestStop(center, nearby.stops, nearby.routes)
    }

    /**
     * One route's whole-route (all-directions) shape plus the label its badge shows, or null when the
     * route can't be drawn: no endpoint / a failed fetch, no shape on the wire, or no display name. A
     * failure is logged and skipped — one unreachable route must not blank the whole ambient layer.
     *
     * Asks for the shape alone ([StopsForRouteRepository.routeShapes]), not the whole route map: the
     * badge's label is already on the stop query's route reference this was called with, so the stops,
     * directions and reference pool that come back with a route map would be fetched, decoded and
     * cached only to be thrown away — at the scale of every route through a downtown block.
     */
    private suspend fun loadShapes(route: ObaRoute): NearbyRouteShapes? {
        val name = getRouteDisplayName(route).takeIf(String::isNotBlank) ?: return null
        val shapes = stopsForRoute.routeShapes(route.id)
            .onFailure { Log.w(TAG, "Nearby route ${route.id} shape fetch failed", it) }
            .getOrNull()
            ?.filter { it.size >= 2 }
            ?: return null
        return if (shapes.isEmpty()) null else NearbyRouteShapes(route.id, name, shapes)
    }

    private companion object {
        const val TAG = "NearbyRoutesController"
    }
}

/**
 * Below this zoom the layer hides. Set well past a citywide view (zoom 11 is roughly a city across a
 * phone screen) so you can pull back and see where the routes running past you actually go; below it
 * the survey is a half mile in a viewport tens of kilometres wide, which stops meaning anything. A
 * display decision, tunable to taste.
 */
private const val NEARBY_ROUTES_MIN_ZOOM = 11.0

/**
 * What the hoop asks routes-for-location for. Not a display budget — the layer draws whatever comes
 * back — but a deliberate ask past every server's own ceiling, because the endpoint's *default* is 10
 * and a downtown block silently answers with an arbitrary handful of the routes running through it.
 * Each server clamps this to its own hard limit (50 on the Puget Sound deployment), so a denser
 * downtown than that still comes back truncated, flagged only by the response's `limitExceeded`.
 */
private const val NEARBY_ROUTES_MAX_COUNT = 200

/** One survey's answer: the routes to draw, and how far the hoop had to reach to gather them. */
private data class NearestRoutes(val radiusMeters: Double, val routes: List<ObaRoute>)

/**
 * The most routes the hoop tries to show at once. Not a target to be met — where the search radius holds
 * fewer, the ring stays at full reach and shows what is there — but the point at which a dense
 * neighbourhood stops being readable, and so the point at which the ring starts closing in on the
 * nearest routes instead (see [hoopRadiusForTarget]).
 *
 * The drawn count lands on or a little above this: routes sharing a stop share a distance, and the radius
 * takes them all rather than cutting arbitrarily between equals.
 */
private const val TARGET_NEARBY_ROUTES = 15

/**
 * Where the outward search starts. Small on purpose: in the dense places, where a wide stop query would
 * be truncated by the server, the answer is already inside this radius, so the ranking is exact.
 */
private const val INITIAL_SEARCH_RADIUS_METERS = 800.0

/** How much an unsuccessful step widens the search, before the ceiling clamps it. */
private const val SEARCH_EXPANSION = 3.0

/**
 * How far the hoop will ever reach. The ring is a claim that these routes are near the rider, so it has
 * to stop at a distance where that stays true — an unbounded ring in a sparse suburb would gather its
 * target from kilometres away and present routes nobody there can reach. Past this the honest answer is
 * a half-empty ring, and that is what the layer draws.
 *
 * It also bounds the search: with the start radius above this is at most two queries per settle, the
 * first of which answers anywhere dense enough for truncation to matter.
 */
private const val MAX_HOOP_RADIUS_METERS = 2000.0

/**
 * The stop cap per search step. Generous — the server clamps to its own limit and reports
 * `limitExceeded` — because the ranking is only as good as its sample.
 */
private const val SEARCH_STOP_LIMIT = 500

/** The layer drawing nothing — stop focus, zoomed out past it, or a survey that found no routes. */
private val NO_NEARBY_ROUTES = NearbyRoutesPresentation(emptyList(), emptyList())

/** Matches the stop loader's settle window: one survey per pan, not one per intermediate idle. */
private const val NEARBY_ROUTES_DEBOUNCE_MS = 400L

/** Ambient context must not monopolize the connection; the same ceiling the focused-stop shapes use. */
private const val MAX_CONCURRENT_NEARBY_ROUTE_FETCHES = 2
