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
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.product
import org.maplibre.android.style.expressions.Expression.stop
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
import org.onebusaway.android.map.render.RouteStopCircles
import org.onebusaway.android.map.render.DETAIL_RAMP_END_ZOOM
import org.onebusaway.android.map.render.DETAIL_RAMP_START_ZOOM
import org.onebusaway.android.map.render.STOP_FOCUS_ROUTE_MIN_SCALE
import org.onebusaway.android.map.render.StopMarker

/**
 * Owns MapLibre's GPU route-stop circle source, style expressions, and rendered-feature tap lookup.
 * Zoom interpolation stays entirely in the style layer; renderer snapshots update only stop data/focus.
 */
internal class MapLibreRouteStopCircleLayer(
    private val map: MapLibreMap,
    private val style: Style,
    private val density: Float,
) {
    private val source = GeoJsonSource(
        SOURCE_ID,
        FeatureCollection.fromFeatures(emptyList<Feature>()),
    )
    private var stopById: Map<String, StopMarker> = emptyMap()
    private var renderedStops: List<StopMarker> = emptyList()
    private var renderedFocusedStopId: String? = null

    init {
        val radius = radiusExpression()
        style.addSource(source)
        style.addLayer(
            CircleLayer(OUTER_LAYER_ID, SOURCE_ID).withProperties(
                circleRadius(radius),
                circleColor(RouteStopCircles.FILL_COLOR),
                circleStrokeColor(RouteStopCircles.STROKE_COLOR),
                circleStrokeWidth(
                    product(
                        radius,
                        literal(RouteStopCircles.STROKE_WIDTH_PX / RouteStopCircles.RADIUS_PX),
                    )
                ),
                circleSortKey(get(MAX_RADIUS_PROPERTY)),
            )
        )
        style.addLayer(
            CircleLayer(INNER_LAYER_ID, SOURCE_ID)
                .withFilter(eq(get(SELECTED_PROPERTY), true))
                .withProperties(
                    circleRadius(
                        product(
                            radius,
                            literal(RouteStopCircles.INNER_RADIUS_SCALE),
                        )
                    ),
                    circleColor(RouteStopCircles.STROKE_COLOR),
                    circleSortKey(get(MAX_RADIUS_PROPERTY)),
                )
        )
    }

    fun render(stops: List<StopMarker>, focusedStopId: String?) {
        val routeStops = stops.filter(StopMarker::routeStop)
        if (routeStops == renderedStops && focusedStopId == renderedFocusedStopId) return
        renderedStops = routeStops
        renderedFocusedStopId = focusedStopId
        stopById = routeStops.associateBy(StopMarker::id)

        val stopFocusMinScale = if (focusedStopId == null) 1f else STOP_FOCUS_ROUTE_MIN_SCALE
        val features = routeStops.map { stop ->
            val selectedScale = if (stop.id == focusedStopId) RouteStopCircles.FOCUSED_SCALE else 1f
            Feature.fromGeometry(Point.fromLngLat(stop.point.longitude, stop.point.latitude)).apply {
                addStringProperty(STOP_ID_PROPERTY, stop.id)
                addBooleanProperty(SELECTED_PROPERTY, stop.id == focusedStopId)
                addNumberProperty(
                    MIN_RADIUS_PROPERTY,
                    RouteStopCircles.RADIUS_PX * stopFocusMinScale * selectedScale,
                )
                addNumberProperty(MAX_RADIUS_PROPERTY, RouteStopCircles.RADIUS_PX * selectedScale)
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
            screen.y + tapRadius,
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
    }

    private fun radiusExpression(): Expression = interpolate(
        linear(),
        zoom(),
        stop(DETAIL_RAMP_START_ZOOM, get(MIN_RADIUS_PROPERTY)),
        stop(DETAIL_RAMP_END_ZOOM, get(MAX_RADIUS_PROPERTY)),
    )

    private companion object {
        const val SOURCE_ID = "oba-route-stops"
        const val OUTER_LAYER_ID = "oba-route-stops-outer"
        const val INNER_LAYER_ID = "oba-route-stops-inner"
        const val STOP_ID_PROPERTY = "stopId"
        const val SELECTED_PROPERTY = "selected"
        const val MIN_RADIUS_PROPERTY = "minRadius"
        const val MAX_RADIUS_PROPERTY = "maxRadius"
        const val TAP_RADIUS_DP = 12f
    }
}
