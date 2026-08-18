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

import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.FOCUSED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_APPROACH_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_CONTEXT_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.util.EARTH_RADIUS_METERS
import org.onebusaway.android.util.GeoPoint

/**
 * JVM tests for the pure trip-plan-leg segment highlighting helpers ([onSegment],
 * [routePolylinesWithSegment], [riddenSpanColorSource]).
 */
class RouteSegmentHighlightTest {

    // A straight segment running north along a meridian.
    private val segment = listOf(GeoPoint(47.60, -122.33), GeoPoint(47.62, -122.33))

    private fun stop(id: String, lat: Double, lon: Double) = ObaStopElement(id = id, lat = lat, lon = lon)

    /** An ordinary ride: one route, so one span, cut nowhere. */
    private fun ride(points: List<GeoPoint>) = listOf(RiddenSpan(points))

    /**
     * A point [meters] due east of ([lat], [lon]), so a tolerance test can state the distance it means
     * instead of hiding it in a magic decimal. Uses the same earth radius as the haversine helper the
     * filter measures with; at these distances the parallel and the great circle through the two points
     * diverge far below a millimetre, so the offset a test asks for is the distance the filter sees.
     */
    private fun eastOf(lat: Double, lon: Double, meters: Double) = GeoPoint(
        lat,
        lon + Math.toDegrees(meters / (EARTH_RADIUS_METERS * cos(Math.toRadians(lat))))
    )

    @Test
    fun onSegment_keepsStopsOnThePath_dropsFarOnes() {
        val stops = listOf(
            stop("on", 47.61, -122.3300), // right on the line
            stop("near", 47.61, -122.3302), // ~15 m off — within tolerance
            stop("off", 47.61, -122.3200) // ~750 m off — excluded
        )
        assertEquals(listOf("on", "near"), stops.onSegment(segment).map { it.id })
    }

    @Test
    fun onSegment_noSegment_keepsEveryStop() {
        val stops = listOf(stop("a", 47.6, -122.3), stop("b", 40.0, -120.0))
        assertEquals(stops, stops.onSegment(emptyList()))
    }

    @Test
    fun routePolylinesWithSegment_noSegment_returnsBaseUnchanged() {
        val base = listOf(RoutePolyline(color = 0xFF0000FF.toInt(), points = segment))
        assertEquals(base, routePolylinesWithSegment(base, emptyList(), colorOf = { 0xFF00FF00.toInt() }))
    }

    @Test
    fun routePolylinesWithSegment_casesTheApproach_andKeepsTheRideAtItsItineraryWeight() {
        val base = listOf(
            RoutePolyline(
                color = null,
                points = segment,
                widthProfile = FOCUSED_ROUTE_LINE_WIDTH_PROFILE,
                directional = true
            )
        )
        val result = routePolylinesWithSegment(base, ride(segment), colorOf = { 0xFF00FF00.toInt() })

        assertEquals(2, result.size)
        // The approach steps down to its own thinnest itinerary weight, loses its arrows, and is solid rather
        // than the faint dashed line that used to compete with the legs beside it (#2082).
        val approach = result.first()
        assertEquals(ITINERARY_APPROACH_WIDTH_PROFILE, approach.widthProfile)
        assertEquals(RouteLineDash.NONE, approach.dash)
        assertFalse(approach.directional)
        // The ridden span rides on top at the weight it had as an itinerary leg, in the route colour,
        // and — like every itinerary line — without direction chevrons (#2129).
        val overlay = result.last()
        assertEquals(ITINERARY_RIDE_WIDTH_PROFILE, overlay.widthProfile)
        assertEquals(0xFF00FF00.toInt(), overlay.color)
        assertFalse(overlay.directional)
        // Both halves of the selected route carry a case in the selection colour, so the approach and the
        // ride read as one line rather than as two things that happen to meet. The approach's is the lighter
        // weight, because at 3.5dp it is thinner than a full selection case is wide.
        assertEquals(RouteLineCase.APPROACH, approach.case)
        assertEquals(RouteLineCase.SELECTION, overlay.case)
    }

    @Test
    fun routePolylinesWithSegment_layersApproach_thenJourneyContext_thenSelectedRide() {
        val base = listOf(RoutePolyline(color = 1, points = segment, directional = true))
        val journey = listOf(
            RoutePolyline(
                color = 2,
                points = segment.reversed(),
                widthProfile = ITINERARY_CONTEXT_WIDTH_PROFILE,
                dash = RouteLineDash.TRAIL
            )
        )

        val result = routePolylinesWithSegment(base, ride(segment), colorOf = { 3 }, itineraryContext = journey)

        assertEquals(listOf(1, 2, 3), result.map { it.color })
        assertEquals(ITINERARY_APPROACH_WIDTH_PROFILE, result[0].widthProfile)
        assertEquals(RouteLineDash.NONE, result[0].dash)
        assertEquals(ITINERARY_CONTEXT_WIDTH_PROFILE, result[1].widthProfile)
        assertEquals(RouteLineDash.TRAIL, result[1].dash)
        assertEquals(ITINERARY_RIDE_WIDTH_PROFILE, result[2].widthProfile)
        // Only the selected route is cased: the rest of the rider's journey is context, not selection.
        assertEquals(RouteLineCase.NONE, result[1].case)
        assertEquals(RouteLineCase.APPROACH, result[0].case)
        assertEquals(RouteLineCase.SELECTION, result[2].case)
    }

    @Test
    fun routePolylinesWithSegment_drawsAnInterlinedRideAsOneSpanPerRoute_cutAtEachCutover() {
        // #2127: one ride, two routes. Drawn as a single line it had to pick one route's colour for the
        // whole thing and had no interior to mark, so the cutover the itinerary map shows disappeared the
        // moment the rider tapped in. Each span now takes its own colour, and the seam is an end to cut.
        val north = listOf(GeoPoint(47.60, -122.33), GeoPoint(47.62, -122.33))
        val east = listOf(GeoPoint(47.62, -122.33), GeoPoint(47.62, -122.30))
        val spans = listOf(
            RiddenSpan(north, routeId = "45"),
            RiddenSpan(east, routeId = "75", startsCutover = true)
        )

        val result = routePolylinesWithSegment(
            base = emptyList(),
            spans = spans,
            colorOf = { if (it.routeId == "45") 45 else 75 }
        )

        assertEquals(listOf(45, 75), result.map { it.color })
        // The cut goes on the span the vehicle *changed route onto*, and only there. The ride's own ends
        // are a boarding and an alighting, and take the bulbs the itinerary map gives them ([itineraryLegCaps]);
        // the seam between the spans takes neither, because the rider sits through it.
        assertEquals(listOf(RouteLineMark.BULB, RouteLineMark.INTERLINE_CUT), result.map { it.startMark })
        assertEquals(listOf(RouteLineMark.NONE, RouteLineMark.BULB), result.map { it.endMark })
        // Both spans are still the selected ride: same weight, same case.
        result.forEach {
            assertEquals(ITINERARY_RIDE_WIDTH_PROFILE, it.widthProfile)
            assertEquals(RouteLineCase.SELECTION, it.case)
        }
    }

    @Test
    fun routePolylinesWithSegment_drawsTheRideWithTheStripesItHadAsALeg() {
        // #2241: a ride the rider may board either route for is striped on the itinerary map (#2100), and
        // drilling in used to draw it as a plain line — so tapping a shared ride to look at it closer was
        // exactly when the map stopped saying it was shared. The stripes are the ride's, not the route
        // session's, so they travel on the span and are rendered by the caller's palette.
        val span = RiddenSpan(segment, routeId = "1_100479", interchangeableColors = listOf(0xFF00A94F.toInt()))

        val result = routePolylinesWithSegment(
            base = emptyList(),
            spans = listOf(span),
            colorOf = { 0xFF0072BC.toInt() },
            stripeColorsOf = { span, _ -> span.interchangeableColors.filterNotNull() }
        )

        assertEquals(listOf(0xFF00A94F.toInt()), result.single().stripeColors)
    }

    @Test
    fun routePolylinesWithSegment_marksTheRidesOwnEndsWhereverItStartsAndStops() {
        // A plain single-route ride: the rider gets on at one end and off at the other, which is what a
        // bulb pair says (#2084) — the same thing it said on the itinerary map they tapped it from.
        val result = routePolylinesWithSegment(emptyList(), ride(segment), colorOf = { 1 })

        assertEquals(RouteLineMark.BULB, result.single().startMark)
        assertEquals(RouteLineMark.BULB, result.single().endMark)
    }

    @Test
    fun routePolylinesWithSegment_selfInterlineSpansAreNotCut() {
        // The same route reversing onto itself: two spans, but nothing changed under the rider, so the
        // boundary is silent — exactly as the drawer announces no transition for one.
        val spans = listOf(
            RiddenSpan(segment, routeId = "12"),
            RiddenSpan(segment.reversed(), routeId = "12")
        )

        val result = routePolylinesWithSegment(emptyList(), spans, colorOf = { 12 })

        assertTrue(result.none { it.startMark == RouteLineMark.INTERLINE_CUT })
        // The seam itself is unmarked at both of its sides — it is interior to one ride, so neither a cut
        // nor the bulb pair that would read as getting off and back on.
        assertEquals(RouteLineMark.NONE, result.first().endMark)
        assertEquals(RouteLineMark.NONE, result.last().startMark)
    }

    @Test
    fun routePolylinesWithSegment_skipsASpanWithNoDrawableGeometry() {
        // A leg that carried no shape draws nothing, and must not take the whole ride's overlay with it.
        val spans = listOf(RiddenSpan(emptyList(), routeId = "45"), RiddenSpan(segment, routeId = "75"))

        val result = routePolylinesWithSegment(emptyList(), spans, colorOf = { 7 })

        assertEquals(listOf(segment), result.map { it.points })
        // And the ride's ends are the ends of what is actually drawn: the dropped span must not take the
        // boarding bulb down with it, leaving the drawn remainder looking like the middle of a ride.
        assertEquals(RouteLineMark.BULB, result.single().startMark)
        assertEquals(RouteLineMark.BULB, result.single().endMark)
    }

    @Test
    fun riddenSpanColorSource_beforeItsRouteLoads_takesThePlannedColour() {
        // #2186: the load is a network round trip the rider spends looking at the map, and the span had
        // nothing to draw from until it landed — leaving it on the caller's fallback, a pure blue.
        val span = RiddenSpan(segment, routeId = "45", plannedColor = 0xFF00A94F.toInt())

        assertEquals(0xFF00A94F.toInt(), riddenSpanColorSource(span, loaded = null))
        // Nothing to stand in with either: the colour stays unstated and the renderer's default draws it.
        assertEquals(null, riddenSpanColorSource(span.copy(plannedColor = null), loaded = null))
    }

    @Test
    fun riddenSpanColorSource_onceItsRouteLoads_takesTheRoutesOwnColour() {
        val span = RiddenSpan(segment, routeId = "45", plannedColor = 0xFF00A94F.toInt())

        assertEquals(0xFFD22630.toInt(), riddenSpanColorSource(span, LoadedSpanRoute(0xFFD22630.toInt())))
    }

    @Test
    fun riddenSpanColorSource_aLandedLoadWithNoUsableColour_doesNotFallBackToThePlannedColour() {
        // The corridor beneath the span is drawn from the same landed load, and states no colour for it
        // either, so a span that kept a planned colour here would be a line its own approach couldn't match:
        // both leave the colour unstated and take the renderer's default together. A load that landed
        // carrying no route at all is this case, not the pre-load one — it has nothing left to wait for.
        val span = RiddenSpan(segment, routeId = "45", plannedColor = 0xFF00A94F.toInt())

        assertEquals(null, riddenSpanColorSource(span, LoadedSpanRoute(publishedColor = null)))
    }

    @Test
    fun riddenSpanColorSource_aSpanNamingNoRoute_keepsThePlannedColourForGood() {
        // An interline leg whose route didn't resolve to an OBA id: nothing will ever load for it, and the
        // caller hands it no load rather than the ride's shown route — which is a route it isn't ridden as.
        // So the plan is its colour permanently, not for a load window, and the mid-ride change of route
        // the span exists to show survives the load instead of flattening into the leader's colour.
        val span = RiddenSpan(segment, routeId = null, plannedColor = 0xFF00A94F.toInt())

        assertEquals(0xFF00A94F.toInt(), riddenSpanColorSource(span, loaded = null))
    }

    @Test
    fun upstreamTo_clipsEveryVariantAtTheBoardingPoint_dropsVariantsThatNeverReachIt() {
        val trunk = RoutePolyline(color = 1, points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.66, -122.33)))
        // Reaches the boarding point up a western street instead, then branches east — a second valid
        // approach variant, and a different one (a variant sharing the trunk's approach would dedupe).
        val branch = RoutePolyline(
            color = 1,
            points = listOf(
                GeoPoint(47.58, -122.36),
                GeoPoint(47.62, -122.36),
                GeoPoint(47.62, -122.33),
                GeoPoint(47.62, -122.31)
            )
        )
        // Short-turn variant ending ~2 km before the boarding point: not an approach to it.
        val shortTurn = RoutePolyline(color = 1, points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.60, -122.33)))

        val upstream = listOf(trunk, branch, shortTurn).upstreamTo(GeoPoint(47.62, -122.33))

        assertEquals(2, upstream.size)
        upstream.forEach { line ->
            assertEquals(47.62, line.points.last().latitude, 0.000001)
            assertFalse(line.points.any { it.latitude > 47.62 || it.longitude > -122.33 })
        }
    }

    @Test
    fun upstreamTo_boardingPointOffEveryVariant_stillClipsTheNearestOne() {
        val near = RoutePolyline(color = 1, points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.66, -122.33)))
        val far = RoutePolyline(color = 1, points = listOf(GeoPoint(47.58, -122.43), GeoPoint(47.66, -122.43)))

        // ~150 m east of the near variant — off both, but the approach should still draw.
        val upstream = listOf(near, far).upstreamTo(GeoPoint(47.62, -122.328))

        assertEquals(1, upstream.size)
        assertEquals(47.62, upstream.single().points.last().latitude, 0.001)
        assertEquals(-122.33, upstream.single().points.last().longitude, 0.000001)
    }

    @Test
    fun upstreamTo_variantsSharingTheApproach_drawTheTrunkOnce() {
        // Two variants that run the same street to the boarding point and only diverge after it: their
        // clipped approaches are the same line, and the trunk must not be drawn (and cased) twice.
        val north = RoutePolyline(
            color = 1,
            points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.62, -122.33), GeoPoint(47.66, -122.33))
        )
        val east = RoutePolyline(
            color = 1,
            points = listOf(GeoPoint(47.58, -122.33), GeoPoint(47.62, -122.33), GeoPoint(47.62, -122.31))
        )

        val upstream = listOf(north, east).upstreamTo(GeoPoint(47.62, -122.33))

        assertEquals(1, upstream.size)
        assertEquals(listOf(GeoPoint(47.58, -122.33), GeoPoint(47.62, -122.33)), upstream.single().points)
    }
}
