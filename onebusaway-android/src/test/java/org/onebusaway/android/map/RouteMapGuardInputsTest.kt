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

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripDetails
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.util.GeoPoint

/**
 * Tripwires for [RouteMapController]'s two bundled change guards (#2149), plus the equality they need.
 *
 * Neither guard can be exercised through the controller from a JVM test — both sit behind a map host and
 * a live poll — but both failure modes this file guards against are structural, and reachable here:
 *
 *  - **An input the guard forgot.** A cache or early-return guard that hand-compares several separately
 *    stored inputs, while the work it guards reads them from somewhere else, drifts silently: the symptom
 *    is a *stale* value, not a crash. That shipped twice in `RideSelectionController` (see
 *    [RideSelectionControllerTest]) before the inputs were bundled. Bundling makes the guard and the pass
 *    one field set — provided every input is actually *in* the bundle, which is what the field pins below
 *    assert.
 *  - **A field declared but left out of `equals`.** A bundle field written outside the primary constructor
 *    is invisible to a data class's generated `equals`, so the guard would hold across a change to it —
 *    exactly the bug the bundle exists to prevent, reintroduced quietly. Each field is checked to move the
 *    comparison on its own.
 *
 * The field pins are deliberately written as "the set of fields I have a case for" vs "the set the class
 * declares", so a new input fails here until it is covered rather than merely renaming a constant.
 */
class RouteMapGuardInputsTest {

    /**
     * A data class's declared *instance* fields — its properties. Statics are skipped: a companion object
     * and Compose's synthetic `$stable` both land in `declaredFields` and neither is an input.
     */
    private fun declaredFields(type: Class<*>): List<String> = type.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { it.name }
        .filterNot { it.startsWith("$") }
        .sorted()

    // -- reframe's change-detect: the drawn ride ([RouteFocusSpec]) --

    /**
     * [RouteMapController.reframe] honours a [ShowRouteRequest] in two ways: the fields that describe the
     * *ride drawn on the route* are compared as one [RouteFocusSpec] (a change redraws the ride), and the
     * rest are applied by name in the tail. A new request field belongs to exactly one of those, and this
     * fails until it's in one — the #1797 shape (two places that must list the same fields, nothing making
     * them agree) is the whole reason this type exists.
     */
    @Test
    fun `every ShowRouteRequest field is either part of the drawn ride or applied by name`() {
        // Applied by name in reframe: the route + direction anchor decide reframe-vs-re-enter (MapViewModel),
        // initialDirectionId goes to selectDirection, focusTripId to requestFocus.
        val appliedByName = listOf("routeId", "directionStopId", "initialDirectionId", "focusTripId")
        assertEquals(
            "A new ShowRouteRequest field must either join RouteFocusSpec (so a reframe redraws the ride) " +
                "or be applied by name in RouteMapController.reframe",
            declaredFields(ShowRouteRequest::class.java),
            (appliedByName + declaredFields(RouteFocusSpec::class.java)).sorted()
        )
    }

    @Test
    fun `every field of the drawn ride moves the reframe guard on its own`() {
        val base = RouteFocusSpec.None
        val changed = mapOf(
            "riddenSpans" to base.copy(riddenSpans = listOf(RiddenSpan(listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0))))),
            "extraSegments" to base.copy(extraSegments = listOf(RouteFocusSegment("route_b", anchorStopId = "seam"))),
            "alightStopId" to base.copy(alightStopId = "alight"),
            "directionHeadsign" to base.copy(directionHeadsign = "Downtown")
        )
        assertEquals(
            "A new RouteFocusSpec field needs a case here proving reframe reacts to it",
            declaredFields(RouteFocusSpec::class.java),
            changed.keys.sorted()
        )
        changed.forEach { (field, spec) ->
            assertNotEquals("changing $field must reopen the reframe guard", base, spec)
        }
    }

    /**
     * Value equality, not identity — the detail that turned out load-bearing when this pattern was applied
     * to the ride selection. Every launcher builds its spans and segments fresh, so an identity compare
     * would report a change on every re-tap and redraw (and reset the vehicle selection for) the same ride.
     */
    @Test
    fun `two requests describing the same ride compare equal`() {
        val request = ShowRouteRequest(
            routeId = "route_a",
            riddenSpans = listOf(RiddenSpan(listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0)))),
            extraSegments = listOf(RouteFocusSegment("route_b", anchorStopId = "seam")),
            alightStopId = "alight",
            directionHeadsign = "Downtown"
        )
        val rebuilt = request.copy(
            riddenSpans = listOf(RiddenSpan(listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0)))),
            extraSegments = listOf(RouteFocusSegment("route_b", anchorStopId = "seam"))
        )
        assertEquals(RouteFocusSpec.of(request), RouteFocusSpec.of(rebuilt))
    }

    /** The focus fields a request carries reach the spec — [RouteFocusSpec.of] is where both entries read them. */
    @Test
    fun `the spec is taken from the request's own fields`() {
        val spans = listOf(RiddenSpan(listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0))))
        val segments = listOf(RouteFocusSegment("route_b", anchorStopId = "seam"))
        val spec = RouteFocusSpec.of(
            ShowRouteRequest(
                routeId = "route_a",
                riddenSpans = spans,
                extraSegments = segments,
                alightStopId = "alight",
                directionHeadsign = "Downtown"
            )
        )
        assertEquals(RouteFocusSpec(spans, segments, "alight", "Downtown"), spec)
    }

    // -- the vehicle colour memo ([RouteColorInputs]) --

    @Test
    fun `every route colour input moves the memo guard on its own`() {
        val base = RouteColorInputs(
            poll = poll(),
            extras = emptyMap(),
            assignment = emptyMap(),
            palette = BASEMAP_ROUTE_LINE_PALETTE
        )
        val changed = mapOf(
            "poll" to base.copy(poll = poll()),
            "extras" to base.copy(extras = mapOf("route_b" to poll())),
            "assignment" to base.copy(assignment = mapOf(RouteDirectionKey("route_a", 0) to DEFAULT_ROUTE_LINE_COLOR)),
            "palette" to base.copy(palette = RouteLinePalette { it })
        )
        assertEquals(
            "A new colour input needs a case here proving a change to it drops the memo",
            declaredFields(RouteColorInputs::class.java),
            changed.keys.sorted()
        )
        changed.forEach { (field, inputs) ->
            assertNotEquals("changing $field must drop the route colour memo", base, inputs)
        }
    }

    /**
     * The complementary half: the guard must also *hold* between polls, since it runs on the per-frame
     * vehicle sampler. Re-gathering the same inputs — which every frame does — must compare equal, or the
     * memo would be cleared 20 times a second and the resolution it exists to avoid would run every frame.
     */
    @Test
    fun `re-gathering the same colour inputs compares equal`() {
        val poll = poll()
        val extras = mapOf("route_b" to poll())
        val assignment = mapOf(RouteDirectionKey("route_a", 0) to DEFAULT_ROUTE_LINE_COLOR)
        assertEquals(
            RouteColorInputs(poll, extras, assignment, BASEMAP_ROUTE_LINE_PALETTE),
            RouteColorInputs(poll, extras.toMap(), assignment.toMap(), BASEMAP_ROUTE_LINE_PALETTE)
        )
    }

    /** A distinct poll instance per call — a landed poll replaces the response wholesale. */
    private fun poll(): VehiclePoll = VehiclePoll(
        response = object : RouteTrips {
            override val trips: List<ObaTripDetails> = emptyList()

            override fun trip(tripId: String?): ObaTrip? = null

            override fun route(routeId: String): ObaRoute? = null

            override val currentTimeMs: Long = 0L
        },
        loadTime = ElapsedTime(0L)
    )
}
