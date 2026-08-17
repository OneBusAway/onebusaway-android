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
package org.onebusaway.android.demo

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.onebusaway.android.R
import org.onebusaway.android.util.GeoPoint

/**
 * Whether the app is currently on the demo transit system — the read-only half of
 * [DemoModeController], for the consumers that only need to *ask*.
 *
 * Separated so that a collaborator which merely reacts to demo mode (the map chrome, which has to show
 * the rentals button while the tour is running) cannot enter or leave it, and so that such a consumer
 * stays constructible in a plain JVM test: the controller itself needs a `Context` to read its bundled
 * fixture, which is nothing a chrome-gate test should have to supply.
 */
interface DemoModeState {

    /** True while the app is answering from the demo transit system rather than the network. */
    val active: StateFlow<Boolean>

    /** The same fact, for the non-reactive call sites that just need to branch right now. */
    val isActive: Boolean
}

/**
 * The switch that puts the app on the demo transit system (#2164), and the single place that knows
 * whether it is on.
 *
 * While it is [active], the OBA seam ([org.onebusaway.android.api.net.ObaApiProvider]), the trip
 * planner ([DemoTripPlanRepository]) and the rental layer ([DemoRentalPlacesRepository]) answer from
 * the bundled demo deployment instead of the network. Nothing else in the app changes — which is what
 * lets the scripted tutorial demonstrate the real UI, on data that is always there and always in the
 * state the script describes, with no dependence on where the user is, whether their region resolved,
 * or whether the transit system happens to be running anything right now.
 *
 * **Deliberately not user-reachable.** It is turned on by the scripted tutorial starting and off by
 * that tutorial ending — see `ScriptedTutorial` and its host — so there is no way to be left stranded
 * looking at a city you aren't in. Any future debug toggle should go through [enter] / [exit] rather
 * than flipping the flag, so entering and leaving stay symmetric.
 */
@Singleton
class DemoModeController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DemoModeState {
    private val _active = MutableStateFlow(false)

    override val active: StateFlow<Boolean> = _active.asStateFlow()

    override val isActive: Boolean get() = _active.value

    /**
     * The demo deployment, parsed from the bundled fixture on first use and kept for the process.
     *
     * Loading is lazy so a user who never starts the tutorial never pays for it, and it is `by lazy`
     * rather than re-read per entry because the fixture is immutable — the *scenario* is what varies
     * with time, and that's computed per call.
     */
    val fixture: DemoTransitFixture by lazy { load() }

    /** The demo OBA deployment the API seam routes to while [active]. */
    val obaService: DemoObaWebService by lazy { DemoObaWebService(fixture) }

    /** The route the tour drills into from the anchor stop, or null if the fixture didn't load. */
    val featuredRoute: DemoFeaturedRoute?
        get() {
            val route = fixture.routeById[FEATURED_ROUTE_ID] ?: return null
            val geometry = fixture.routeStops[FEATURED_ROUTE_ID] ?: return null
            return DemoFeaturedRoute(
                routeId = route.id,
                shortName = route.shortName.orEmpty().ifBlank { route.id },
                directionId = geometry.directionId.toIntOrNull()
            )
        }

    /**
     * The trip the tour's "tap an arrival" step drills into: the next departure of [FEATURED_ROUTE_ID]
     * from the anchor stop that has a bus actually out on the road (only those can be shown on the map).
     *
     * Resolved at the moment the step runs rather than fixed in advance, because the demo timetable
     * moves with the clock — the run that was next when the tour started may have left by the time the
     * rider gets here. Null when no run of that route is live, in which case the step simply narrates
     * without a vehicle selected instead of pointing at one that isn't there.
     */
    fun featuredTripId(): String? {
        val now = DemoClock.nowMs()
        return DemoScenario.arrivalsAt(fixture, ANCHOR_STOP_ID, now)
            .firstOrNull { it.run.routeId == FEATURED_ROUTE_ID && it.run.isOnRoad(now) }
            ?.run
            ?.tripId
    }

    /** Turn the demo system on. Idempotent. */
    fun enter() {
        _active.value = true
    }

    /** Turn the demo system off and hand the app back to the real region. Idempotent. */
    fun exit() {
        _active.value = false
    }

    private fun load(): DemoTransitFixture = runCatching {
        val body = context.resources.openRawResource(R.raw.demo_transit)
            .bufferedReader()
            .use { it.readText() }
        JSON.decodeFromString<DemoTransitFixture>(body)
    }.getOrElse {
        // An unreadable fixture leaves an empty demo system: the tour still runs its captions, it just
        // has nothing to point at. Better than taking the app down over onboarding content.
        Log.e(TAG, "Failed to parse the bundled demo transit fixture", it)
        DemoTransitFixture()
    }

    companion object {
        private const val TAG = "DemoModeController"

        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Where the map opens the tour: the demo anchor stop, E Pine St & Summit Ave on Capitol Hill.
         * Held here rather than read from the fixture so the camera can be aimed before the fixture has
         * been parsed, and so the framing stays fixed if the fixture's stop set is ever re-cut.
         */
        val CAMERA_TARGET: GeoPoint = GeoPoint(47.615158, -122.324638)

        /**
         * The opening framing: wide enough that the demo system reads as a *network* — the three routes
         * running out of Capitol Hill along Broadway and Pine — rather than a handful of markers on one
         * street, which is not what the tour's first caption is about.
         *
         * Deliberately below [org.onebusaway.android.map.render.STOP_DOT_ZOOM_THRESHOLD], where stops
         * collapse from their directional icons to plain dots. At this altitude that is the right
         * rendering: a chain of dots along a street reads as a *line*, which is the thing the opening
         * step is pointing at. [DETAIL_ZOOM] brings the icons back one step later.
         */
        const val CAMERA_ZOOM: Float = 14.5f

        /**
         * Where the camera settles once the tour focuses its stop — close enough for a stop to look
         * like a stop again, and for the arrivals steps that follow to be about one bay rather than a
         * neighbourhood. The tour opens wide ([CAMERA_ZOOM]) and comes in to here, which is also what
         * puts the zoom back after stepping *out* of the route view.
         */
        const val DETAIL_ZOOM: Float = 16f

        /** The stop the tour focuses — the one with three routes the script narrates. */
        const val ANCHOR_STOP_ID: String = "1_11140"

        /** The route the tour opens on the map when it demonstrates route focus (the 49). */
        const val FEATURED_ROUTE_ID: String = "1_100447"

        /** Where the demo trip plan runs to — U-District Station, which the 49 serves. */
        val TRIP_PLAN_DESTINATION: GeoPoint = GeoPoint(47.660847, -122.313823)

        /** The destination's rider-facing name, used in the trip planner's "to" field. */
        const val TRIP_PLAN_DESTINATION_NAME: String = "U District Station"
    }
}

/** The identity the tour needs to open its featured route on the map, as the arrivals row would. */
data class DemoFeaturedRoute(
    val routeId: String,
    val shortName: String,
    /** The GTFS direction the demo system runs, or null if the fixture didn't state a numeric one. */
    val directionId: Int?
)
