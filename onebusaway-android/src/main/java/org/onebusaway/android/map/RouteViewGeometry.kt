/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.onebusaway.android.map

import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.RouteBadgeRequest
import org.onebusaway.android.map.layout.placeRouteBadges
import org.onebusaway.android.map.render.ADJACENT_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.BadgedRoute
import org.onebusaway.android.map.render.DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.FOCUSED_ROUTE_LINE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_APPROACH_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_CONTEXT_WIDTH_PROFILE
import org.onebusaway.android.map.render.PINNED_TRIP_GHOST_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RouteBadgeTap
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.getRouteDisplayName

internal val ROUTE_VIEW_TRANSFORMS = setOf(
    RoutePolylineTransform.VIEWPORT_CLIP,
    RoutePolylineTransform.ZOOM_SIMPLIFY
)

/** The line presentation shared by single-route view and a selected route in focused-stop mode. */
internal fun focusedRoutePolyline(
    color: Int?,
    points: List<GeoPoint>,
    directional: Boolean
) = RoutePolyline(
    color = color,
    points = points,
    widthProfile = FOCUSED_ROUTE_LINE_WIDTH_PROFILE,
    directional = directional,
    transforms = ROUTE_VIEW_TRANSFORMS
)

/** The active route's broader geometry retained beneath an exact selected-trip line. */
internal fun List<RoutePolyline>.asDeemphasizedRouteUnderlay(): List<RoutePolyline> = map { line ->
    line.copy(
        widthProfile = DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE,
        directional = false
    )
}

/**
 * The line the rider has selected, wrapped in the heavier [RouteLineCase.SELECTION] case — the map's one way
 * of saying "this is the one you're looking at" (#2082). Selection deliberately changes nothing else: a leg
 * keeps the weight, colour and dash that say what *kind* of line it is, so drilling into it doesn't restyle
 * the trip around it.
 *
 * It overwrites whatever case the line already carried, which for a directions ride is the hairline
 * [RouteLineCase.OUTLINE] every ride wears: selection is the *step up* in edge weight, so a line that already
 * has an edge simply gets a heavier one. The case's colour is the renderer's to resolve, since it depends on
 * the current theme (see [mapRouteLineCaseColor]).
 */
internal fun RoutePolyline.withCase(): RoutePolyline = copy(case = RouteLineCase.SELECTION)

/**
 * The selected transit route upstream of the boarding point — where the vehicle is coming from — drawn as
 * part of the selected line rather than as background: solid, cased like the ride it leads into, at its own
 * thinnest itinerary weight ([ITINERARY_APPROACH_WIDTH_PROFILE]).
 *
 * It was previously the map's faintest dashed line, a hair thinner than the receded itinerary legs beside
 * it, so the rider read two near-identical thin strokes meaning quite different things (#2082). Chevrons
 * stay off: the approach is where the vehicle comes from, not a span the rider travels.
 */
internal fun List<RoutePolyline>.asSelectedRouteApproach(): List<RoutePolyline> = map { line ->
    line.copy(
        widthProfile = ITINERARY_APPROACH_WIDTH_PROFILE,
        directional = false,
        dash = RouteLineDash.NONE
    ).withCase()
}

/**
 * The rider's committed journey retained around a focused transit leg. It keeps each leg's mode/route
 * colour and dash, but drops chevrons and takes a middle weight: stronger than unused route geometry,
 * weaker than the selected ridden segment.
 */
internal fun List<RoutePolyline>.asItineraryContext(): List<RoutePolyline> = map { line ->
    line.copy(
        widthProfile = ITINERARY_CONTEXT_WIDTH_PROFILE,
        directional = false
    )
}

/**
 * The rider's parked trip as the thin ghost drawn under an exploring map (#2053).
 *
 * Keeps each leg's colour — the one thing the ghost is *for* is saying which trip is waiting — and drops
 * everything that competes for attention: the case that would halo it above the basemap, the terminus
 * bulbs and interline cuts that are details of a trip being read, and any chevrons. What is left is a
 * thin coloured trace of the journey.
 */
internal fun List<RoutePolyline>.asPinnedTripGhost(): List<RoutePolyline> = map { line ->
    line.copy(
        widthProfile = PINNED_TRIP_GHOST_WIDTH_PROFILE,
        directional = false,
        case = RouteLineCase.NONE,
        startMark = RouteLineMark.NONE,
        endMark = RouteLineMark.NONE
    )
}

/**
 * Convert exact trip shapes into route lines. When [emphasizedRoute] is null (stop focus, no route
 * selected) every shape is a thin, plain adjacency line with no direction chevrons. When it's set,
 * that route-direction uses a 1.5x directional stroke while siblings use a thin, plain stroke and
 * render first, keeping the emphasized variant visually on top at shared segments.
 */
internal fun FocusedTripGeometry.toRoutePolylines(
    emphasizedRoute: RouteDirectionKey? = null,
    routeColors: Map<RouteDirectionKey, Int> = emptyMap()
): List<RoutePolyline> = buildList {
    val orderedShapes = if (emphasizedRoute == null) {
        shapes
    } else {
        shapes.sortedBy { if (it.routeDirection == emphasizedRoute) 1 else 0 }
    }
    orderedShapes.forEach { shape ->
        if (shape.points.size < 2) return@forEach
        val emphasized = emphasizedRoute == shape.routeDirection
        val polyline = if (emphasized) {
            focusedRoutePolyline(
                routeColors[shape.routeDirection] ?: mapRouteLineColorOrNull(shape.routeColor),
                shape.points,
                directional = true
            )
        } else {
            val widthProfile = if (emphasizedRoute == null) {
                ADJACENT_ROUTE_LINE_WIDTH_PROFILE
            } else {
                DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE
            }
            RoutePolyline(
                routeColors[shape.routeDirection] ?: mapRouteLineColorOrNull(shape.routeColor),
                shape.points,
                widthProfile,
                // Adjacent routes in stop focus are plain thin lines — no direction chevrons — so the
                // mode reads as "these routes pass here", reserving chevrons for a selected route (#1985).
                directional = false,
                transforms = ROUTE_VIEW_TRANSFORMS
            )
        }
        add(polyline)
    }
}

/** Sibling routes, then the selected route's thin underlay, then its exact trip shape. */
internal fun FocusedTripGeometry.toTripFocusedRoutePolylines(
    selectedRoute: RouteDirectionKey,
    routeColors: Map<RouteDirectionKey, Int>,
    selectedRouteUnderlay: List<RoutePolyline>,
    selectedTrip: RoutePolyline
): List<RoutePolyline> = FocusedTripGeometry(shapes.filterNot { it.routeDirection == selectedRoute })
    .toRoutePolylines(selectedRoute, routeColors) +
    selectedRouteUnderlay +
    selectedTrip

/**
 * One badge model per successfully drawn route-direction, preserving the focused-trip order that
 * mirrors the arrivals drawer. The shared layout chooses stable geographic line-center anchors;
 * flavor renderers only draw them.
 *
 * These take the map's label schedule ([RouteBadge.scale]) rather than a fixed pixel size (#2195): a
 * focused stop's routes fan out from one point, so at an overview zoom their labels are both oversized
 * against the lines they name and packed tightly enough to hide them.
 */
internal fun FocusedTripGeometry.toRouteBadges(
    routes: List<ObaRoute>,
    routeColors: Map<RouteDirectionKey, Int> = emptyMap()
): List<RouteBadge> {
    val metadata = routes.associateBy(ObaRoute::id)
    val specs = shapes.groupBy(FocusedTripShape::routeDirection).mapNotNull { (key, routeShapes) ->
        val route = metadata[key.routeId] ?: return@mapNotNull null
        val name = getRouteDisplayName(route).takeIf(String::isNotBlank) ?: return@mapNotNull null
        RouteBadgeSpec(key, route, name, routeShapes)
    }
    return placeRouteBadges(
        specs.map { spec ->
            // A badge takes its line's colour, so it goes through the same policy — an adjacency colour is
            // already in it, an agency's own has to be put through.
            val color = routeColors[spec.key]
                ?: mapRouteLineColorOrNull(spec.shapes.firstNotNullOfOrNull(FocusedTripShape::routeColor) ?: spec.route.color)
                ?: DEFAULT_ROUTE_LINE_COLOR
            RouteBadgeRequest(
                // An adjacency label names the one route whose line it sits on; only a directions ride the
                // rider may board any of several routes for stacks more than one name (#2083).
                routes = listOf(BadgedRoute(spec.name, color)),
                paths = spec.shapes.map { shape -> RouteBadgePath(shape.points) },
                // An adjacency label is the way into its route: it names a route the rider hasn't opened.
                tap = RouteBadgeTap.ShowRoute(spec.key)
            )
        }
    )
}

private data class RouteBadgeSpec(
    val key: RouteDirectionKey,
    val route: ObaRoute,
    val name: String,
    val shapes: List<FocusedTripShape>
)

/** The selected-trip line's color and whether the generic same-direction underlay stays beneath it. */
internal data class SelectedTripStyle(val color: Int, val includeUnderlay: Boolean)

/**
 * [stopFocusActive] alone gates the underlay: inside stop focus the focused-stop's own siblings
 * already carry the route's other geometry, so the underlay is dropped even when [selectedRouteDirection]
 * isn't among the focused stop's own trips and [routeColors] carries no adjacency entry for it — that
 * combination used to fall back to a whole-route-style underlay (the #1899 regression fixed by #1902),
 * because the underlay decision was proxied off the color lookup instead of the real stop-focus state.
 * A color miss still falls back to [routeColorFallback]; only the underlay must not follow it. Both
 * inputs are already in the map's route-line colour policy ([mapRouteLineColor]) — this picks between
 * them, it doesn't render either.
 */
internal fun selectedTripStyle(
    stopFocusActive: Boolean,
    selectedRouteDirection: RouteDirectionKey,
    routeColors: Map<RouteDirectionKey, Int>,
    routeColorFallback: Int
): SelectedTripStyle = SelectedTripStyle(
    color = routeColors[selectedRouteDirection] ?: routeColorFallback,
    includeUnderlay = !stopFocusActive
)

/** Presented route-direction identities at each scheduled stop, optionally narrowed to [route]. */
internal fun FocusedTripStops.routeDirectionsByStopId(
    trips: Set<FocusedTrip>,
    route: RouteDirectionKey? = null
): Map<String, Set<RouteDirectionKey>> {
    val routesByTripId = trips.associate { it.tripId to it.routeDirection }
    val result = LinkedHashMap<String, MutableSet<RouteDirectionKey>>()
    for ((tripId, stopIds) in stopIdsByTripId) {
        val tripRoute = routesByTripId[tripId] ?: continue
        if (route != null && tripRoute != route) continue
        stopIds.forEach { stopId -> result.getOrPut(stopId, ::linkedSetOf).add(tripRoute) }
    }
    return result
}
