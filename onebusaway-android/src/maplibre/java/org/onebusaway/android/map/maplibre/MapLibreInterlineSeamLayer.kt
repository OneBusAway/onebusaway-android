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

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.onebusaway.android.map.render.InterlineSeamMark
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.leadingBearing

/**
 * Draws the interline cutover mark (#2127) across every line end that asks for one
 * ([RouteLineMark.INTERLINE_CUT]) — the maplibre counterpart of the gms flavor's `CustomCap`, which the
 * classic polyline annotation has no equivalent of. The same gap [MapLibreRouteEndpointBulbLayer] fills for
 * the endpoint bulbs, and rendered on the same schedule: features are rebuilt from the reconciled line list
 * and re-sized on each camera settle, since the mark is sized against the line's current stroke width.
 *
 * A symbol rather than a circle, because unlike a bulb this mark has an orientation: it is rotated to the
 * line's own direction at that end ([leadingBearing]) and rotates with the map, so the slash always crosses
 * the corridor rather than lying along it.
 *
 * [caseColorOf] resolves a line's case colour (theme-dependent, so it is read at draw time rather than
 * carried on the line) and [density] converts maplibre's dp line widths into the screen pixels the mark's
 * bitmap is measured in.
 */
internal class MapLibreInterlineSeamLayer(
    private val style: Style,
    private val density: Float,
    private val caseColorOf: (RoutePolyline) -> Int
) {
    private val source = GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList<Feature>()))

    // The colours whose slash bitmap is registered with the style. A style image can't be given per feature,
    // only named by one, so each case colour that turns up gets its own — a handful per session (a directions
    // plan has few rides, and a ride's colour survives a redraw), all released in [dispose].
    private val registeredImages = HashSet<String>()

    init {
        style.addSource(source)
        style.addLayer(
            SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                iconImage(get(IMAGE_PROPERTY)),
                iconSize(get(SIZE_PROPERTY)),
                iconRotate(get(BEARING_PROPERTY)),
                // Rotated with the map, not with the viewport: the mark's angle means "across this line".
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                // The mark belongs to its line, so it is drawn wherever that line is — never dropped, and
                // never displacing a label, the way an ordinary symbol's collision handling would.
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
            )
        )
    }

    fun render(polylines: List<RoutePolyline>, widthOf: (RoutePolyline) -> Float) {
        val features = polylines.flatMap { line ->
            buildList {
                // Read from whichever end is cut: the line's points run away from its start and *into* its
                // end, so the far end's direction is read down the reversed list. That the two disagree by a
                // half turn doesn't matter — the slash is symmetric under one (see [InterlineSeamMark]).
                if (line.startMark == RouteLineMark.INTERLINE_CUT) add(line.points)
                if (line.endMark == RouteLineMark.INTERLINE_CUT) add(line.points.asReversed())
            }.mapNotNull { fromCutEnd ->
                val point = fromCutEnd.firstOrNull() ?: return@mapNotNull null
                // A line with no direction at that end has nothing to cross — see [leadingBearing].
                val bearing = leadingBearing(fromCutEnd) ?: return@mapNotNull null
                Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                    addStringProperty(IMAGE_PROPERTY, imageFor(caseColorOf(line)))
                    // The bitmap is drawn for a line [InterlineSeamMark.REFERENCE_WIDTH_PX] wide, so this
                    // puts the mark's own corridor on this line's actual stroke at the current zoom.
                    addNumberProperty(SIZE_PROPERTY, widthOf(line) * density / InterlineSeamMark.REFERENCE_WIDTH_PX)
                    addNumberProperty(BEARING_PROPERTY, bearing)
                }
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun dispose() {
        style.removeLayer(LAYER_ID)
        style.removeSource(SOURCE_ID)
        registeredImages.forEach { style.removeImage(it) }
        registeredImages.clear()
    }

    private fun imageFor(color: Int): String {
        val id = "$IMAGE_ID_PREFIX$color"
        if (registeredImages.add(id)) style.addImage(id, InterlineSeamMark.bitmap(color))
        return id
    }

    private companion object {
        const val SOURCE_ID = "oba-interline-seams-source"
        const val LAYER_ID = "oba-interline-seams-layer"
        const val IMAGE_ID_PREFIX = "oba-interline-seam-"
        const val IMAGE_PROPERTY = "image"
        const val SIZE_PROPERTY = "size"
        const val BEARING_PROPERTY = "bearing"
    }
}
