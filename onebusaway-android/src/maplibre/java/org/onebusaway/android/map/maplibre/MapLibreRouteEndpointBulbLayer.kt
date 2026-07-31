/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.maplibre

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline

/** Draws screen-sized circular caps that MapLibre's classic polyline annotation cannot configure. */
internal class MapLibreRouteEndpointBulbLayer(private val style: Style) {
    private val source = GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList<Feature>()))

    init {
        style.addSource(source)
        style.addLayer(
            CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
                circleRadius(get(RADIUS_PROPERTY)),
                circleColor(toColor(get(COLOR_PROPERTY)))
            )
        )
    }

    fun render(polylines: List<RoutePolyline>, widthOf: (RoutePolyline) -> Float) {
        val features = polylines.flatMap { line ->
            if (line.points.isEmpty()) return@flatMap emptyList()
            buildList {
                if (line.startMark == RouteLineMark.BULB) add(line.points.first())
                if (line.endMark == RouteLineMark.BULB) add(line.points.last())
            }.distinct().map { point ->
                Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                    addNumberProperty(RADIUS_PROPERTY, widthOf(line) * ENDPOINT_BULB_RADIUS_SCALE)
                    addStringProperty(COLOR_PROPERTY, line.resolvedColor.toMapLibreColor())
                }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun dispose() {
        style.removeLayer(LAYER_ID)
        style.removeSource(SOURCE_ID)
    }

    private fun Int.toMapLibreColor(): String {
        val red = this shr 16 and 0xFF
        val green = this shr 8 and 0xFF
        val blue = this and 0xFF
        val alpha = (this ushr 24) / 255f
        return "rgba($red, $green, $blue, $alpha)"
    }

    private companion object {
        const val SOURCE_ID = "oba-route-endpoint-bulbs-source"
        const val LAYER_ID = "oba-route-endpoint-bulbs-layer"
        const val RADIUS_PROPERTY = "radius"
        const val COLOR_PROPERTY = "color"
        const val ENDPOINT_BULB_RADIUS_SCALE = 1f
    }
}
