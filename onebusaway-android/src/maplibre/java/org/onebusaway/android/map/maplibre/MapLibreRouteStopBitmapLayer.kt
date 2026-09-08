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
import kotlin.math.roundToInt
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.onebusaway.android.map.render.DETAIL_RAMP_END_ZOOM
import org.onebusaway.android.map.render.DETAIL_RAMP_START_ZOOM
import org.onebusaway.android.map.render.RouteStopCircles
import org.onebusaway.android.map.render.RouteStopIconKey
import org.onebusaway.android.map.render.STOP_FOCUS_ROUTE_MIN_SCALE
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.drawRouteStopBitmap
import org.onebusaway.android.map.render.routeStopIconKey

/** Geographic boarding markers using the same artwork as Google, with GPU zoom interpolation. */
internal class MapLibreRouteStopBitmapLayer(
    private val map: MapLibreMap,
    private val style: Style,
    private val density: Float,
    private val fillColor: Int,
    private val outlineColor: Int
) {
    private val source = GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList<Feature>()))
    private val images = HashMap<RouteStopIconKey, String>()
    private var nextImageId = 0
    private var stopById: Map<String, StopMarker> = emptyMap()
    private var renderedStops: List<StopMarker> = emptyList()
    private var renderedScaleWithZoom = false
    private var renderedRecedeAdjacent = false

    init {
        style.addSource(source)
        style.addLayer(
            SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                iconImage(get(IMAGE_PROPERTY)),
                // zoom() must be the input of a top-level interpolate (#1927).
                iconSize(
                    interpolate(
                        linear(),
                        zoom(),
                        stop(DETAIL_RAMP_START_ZOOM, get(MIN_SIZE_PROPERTY)),
                        stop(DETAIL_RAMP_END_ZOOM, get(MAX_SIZE_PROPERTY))
                    )
                ),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )
    }

    fun render(stops: List<StopMarker>, focusedStopId: String?, scaleWithZoom: Boolean, recedeAdjacent: Boolean) {
        val routeStops = stops.filter { it.routeStop && it.id != focusedStopId }
        if (routeStops == renderedStops &&
            scaleWithZoom == renderedScaleWithZoom &&
            recedeAdjacent == renderedRecedeAdjacent
        ) {
            return
        }
        renderedStops = routeStops
        renderedScaleWithZoom = scaleWithZoom
        renderedRecedeAdjacent = recedeAdjacent
        stopById = routeStops.associateBy(StopMarker::id)
        val usedImages = HashSet<RouteStopIconKey>()
        val diameter = (2f * RouteStopCircles.RADIUS_DP * REFERENCE_DENSITY).roundToInt()
        val size = density / REFERENCE_DENSITY * if (recedeAdjacent) RouteStopCircles.ADJACENT_SCALE else 1f
        val minSize = size * if (scaleWithZoom) STOP_FOCUS_ROUTE_MIN_SCALE else 1f
        val features = routeStops.map { stop ->
            val key = routeStopIconKey(stop, diameter, outlineColor)
            usedImages.add(key)
            val image = images.getOrPut(key) {
                val id = "oba-route-stop-${nextImageId++}"
                style.addImage(id, drawRouteStopBitmap(key, fillColor))
                id
            }
            Feature.fromGeometry(Point.fromLngLat(stop.point.longitude, stop.point.latitude)).apply {
                addStringProperty(STOP_ID_PROPERTY, stop.id)
                addStringProperty(IMAGE_PROPERTY, image)
                addNumberProperty(MAX_SIZE_PROPERTY, size)
                addNumberProperty(MIN_SIZE_PROPERTY, minSize)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        // Keep image memory bounded by the current presentation, not every route visited in a session.
        val old = images.iterator()
        while (old.hasNext()) {
            val entry = old.next()
            if (entry.key !in usedImages) {
                style.removeImage(entry.value)
                old.remove()
            }
        }
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
        val feature = map.queryRenderedFeatures(hitBox, LAYER_ID).firstOrNull()
        return feature?.getStringProperty(STOP_ID_PROPERTY)?.let(stopById::get)
    }

    fun dispose() {
        style.removeLayer(LAYER_ID)
        style.removeSource(SOURCE_ID)
        images.values.forEach { style.removeImage(it) }
        images.clear()
        stopById = emptyMap()
        renderedStops = emptyList()
    }

    private companion object {
        const val SOURCE_ID = "oba-route-stops"
        const val LAYER_ID = "oba-route-stops-markers"
        const val STOP_ID_PROPERTY = "stopId"
        const val IMAGE_PROPERTY = "image"
        const val MIN_SIZE_PROPERTY = "minSize"
        const val MAX_SIZE_PROPERTY = "maxSize"
        const val TAP_RADIUS_DP = 12f
        const val REFERENCE_DENSITY = 3f
    }
}
