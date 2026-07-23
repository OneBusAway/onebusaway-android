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
package org.onebusaway.android.map.maplibre

import android.graphics.RectF
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.product
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleSortKey
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.onebusaway.android.map.render.DETAIL_RAMP_END_ZOOM
import org.onebusaway.android.map.render.DETAIL_RAMP_START_ZOOM
import org.onebusaway.android.map.render.RouteStopCircles
import org.onebusaway.android.map.render.STOP_FOCUS_ROUTE_MIN_SCALE
import org.onebusaway.android.map.render.StopMarker

/**
 * Owns MapLibre's GPU route-stop circle source, style expressions, and rendered-feature tap lookup.
 * Zoom interpolation stays entirely in the style layer; renderer snapshots update stop data, selection,
 * and whether the focused-route scale is active.
 */
internal class MapLibreRouteStopCircleLayer(
    private val map: MapLibreMap,
    private val style: Style,
    private val density: Float,
    private val fillColor: Int,
    private val selectedFillColor: Int,
    private val outlineColor: Int
) {
    private val source = GeoJsonSource(
        SOURCE_ID,
        FeatureCollection.fromFeatures(emptyList<Feature>())
    )
    private var stopById: Map<String, StopMarker> = emptyMap()
    private var renderedStops: List<StopMarker> = emptyList()
    private var renderedFocusedStopId: String? = null
    private var renderedScaleWithZoom = false
    private var renderedRecedeAdjacent = false

    init {
        style.addSource(source)
        style.addLayer(
            CircleLayer(OUTER_LAYER_ID, SOURCE_ID).withProperties(
                circleRadius(radiusExpression()),
                circleColor(
                    switchCase(
                        eq(get(SELECTED_PROPERTY), true),
                        color(selectedFillColor),
                        color(fillColor)
                    )
                ),
                circleStrokeColor(outlineColor),
                // Stroke width rides the base (non-selection-scaled) radius so the selected circle
                // keeps the same ring weight as every other stop even though it's drawn larger.
                circleStrokeWidth(
                    radiusExpression(
                        STROKE_MIN_RADIUS_PROPERTY,
                        STROKE_MAX_RADIUS_PROPERTY,
                        RouteStopCircles.STROKE_WIDTH_PX / RouteStopCircles.RADIUS_PX
                    )
                ),
                circleSortKey(get(MAX_RADIUS_PROPERTY))
            )
        )
        style.addLayer(
            CircleLayer(INNER_LAYER_ID, SOURCE_ID)
                .withFilter(eq(get(SELECTED_PROPERTY), true))
                .withProperties(
                    circleRadius(radiusExpression(scale = RouteStopCircles.INNER_RADIUS_SCALE)),
                    circleColor(outlineColor),
                    circleSortKey(get(MAX_RADIUS_PROPERTY))
                )
        )
    }

    fun render(
        stops: List<StopMarker>,
        focusedStopId: String?,
        scaleWithZoom: Boolean,
        recedeAdjacent: Boolean
    ) {
        val routeStops = stops.filter(StopMarker::routeStop)
        if (
            routeStops == renderedStops &&
            focusedStopId == renderedFocusedStopId &&
            scaleWithZoom == renderedScaleWithZoom &&
            recedeAdjacent == renderedRecedeAdjacent
        ) {
            return
        }
        renderedStops = routeStops
        renderedFocusedStopId = focusedStopId
        renderedScaleWithZoom = scaleWithZoom
        renderedRecedeAdjacent = recedeAdjacent
        stopById = routeStops.associateBy(StopMarker::id)

        val stopFocusMinScale = if (scaleWithZoom) STOP_FOCUS_ROUTE_MIN_SCALE else 1f
        val features = routeStops.map { stop ->
            val focused = stop.id == focusedStopId
            // The focused stop grows; adjacent stops recede in stop focus. The stroke rides the
            // selection-free radius so the focused ring keeps constant weight, but adjacent circles
            // thin in proportion with their smaller radius.
            val selectionScale = if (focused) RouteStopCircles.FOCUSED_SCALE else 1f
            val adjacentScale = if (recedeAdjacent && !focused) RouteStopCircles.ADJACENT_SCALE else 1f
            Feature.fromGeometry(Point.fromLngLat(stop.point.longitude, stop.point.latitude)).apply {
                addStringProperty(STOP_ID_PROPERTY, stop.id)
                addBooleanProperty(SELECTED_PROPERTY, focused)
                addNumberProperty(
                    MIN_RADIUS_PROPERTY,
                    RouteStopCircles.RADIUS_PX * stopFocusMinScale * selectionScale * adjacentScale
                )
                addNumberProperty(
                    MAX_RADIUS_PROPERTY,
                    RouteStopCircles.RADIUS_PX * selectionScale * adjacentScale
                )
                // Base radii without the selection scale, so the stroke-width ramp stays constant weight
                // for the focused stop; the adjacent scale rides along so receded circles thin to match.
                addNumberProperty(
                    STROKE_MIN_RADIUS_PROPERTY,
                    RouteStopCircles.RADIUS_PX * stopFocusMinScale * adjacentScale
                )
                addNumberProperty(
                    STROKE_MAX_RADIUS_PROPERTY,
                    RouteStopCircles.RADIUS_PX * adjacentScale
                )
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun stopAt(point: LatLng): StopMarker? {
        val screen = map.projection.toScreenLocation(point)
        val tapRadius = TAP_RADIUS_DP * density
        val hitBox = RectF(
            screen.x - tapRadius,
            screen.y - tapRadius,
            screen.x + tapRadius,
            screen.y + tapRadius
        )
        val feature = map.queryRenderedFeatures(hitBox, OUTER_LAYER_ID).firstOrNull()
        return feature?.getStringProperty(STOP_ID_PROPERTY)?.let(stopById::get)
    }

    fun dispose() {
        style.removeLayer(INNER_LAYER_ID)
        style.removeLayer(OUTER_LAYER_ID)
        style.removeSource(SOURCE_ID)
        stopById = emptyMap()
        renderedStops = emptyList()
        renderedFocusedStopId = null
        renderedScaleWithZoom = false
        renderedRecedeAdjacent = false
    }

    /**
     * A `zoom()`-driven radius interpolation, optionally scaled by [scale].
     *
     * The scale is folded into each interpolation stop's output rather than wrapping the whole
     * interpolation in a `product()`. MapLibre 13 rejects a `zoom()` expression nested inside
     * anything but a top-level step/interpolate ("zoom expression may only be used as input to a
     * top-level step or interpolate expression"), so keeping `zoom()` at the top level is required;
     * scaling the stop outputs is mathematically equivalent for a linear interpolation. (#1927)
     */
    private fun radiusExpression(
        minProperty: String = MIN_RADIUS_PROPERTY,
        maxProperty: String = MAX_RADIUS_PROPERTY,
        scale: Float = 1f
    ): Expression = interpolate(
        linear(),
        zoom(),
        stop(DETAIL_RAMP_START_ZOOM, scaledRadius(minProperty, scale)),
        stop(DETAIL_RAMP_END_ZOOM, scaledRadius(maxProperty, scale))
    )

    private fun scaledRadius(property: String, scale: Float): Expression = if (scale == 1f) get(property) else product(get(property), literal(scale))

    private companion object {
        const val SOURCE_ID = "oba-route-stops"
        const val OUTER_LAYER_ID = "oba-route-stops-outer"
        const val INNER_LAYER_ID = "oba-route-stops-inner"
        const val STOP_ID_PROPERTY = "stopId"
        const val SELECTED_PROPERTY = "selected"
        const val MIN_RADIUS_PROPERTY = "minRadius"
        const val MAX_RADIUS_PROPERTY = "maxRadius"
        const val STROKE_MIN_RADIUS_PROPERTY = "strokeMinRadius"
        const val STROKE_MAX_RADIUS_PROPERTY = "strokeMaxRadius"
        const val TAP_RADIUS_DP = 12f
    }
}
