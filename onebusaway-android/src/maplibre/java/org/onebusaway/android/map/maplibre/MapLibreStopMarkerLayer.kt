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
@file:Suppress("DEPRECATION")

package org.onebusaway.android.map.maplibre

import android.content.Context
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.map.render.StopIconKind
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.stopIconKind

/** Owns non-route stop marker identity, icon reconciliation, tap lookup, and disposal. */
internal class MapLibreStopMarkerLayer(
    private val map: MapLibreMap,
    private val context: Context,
) {
    private val markerByStopId = HashMap<String, Marker>()
    private val stopByMarker = HashMap<Marker, StopMarker>()
    private val kindByStopId = HashMap<String, StopIconKind>()

    fun render(stops: List<StopMarker>, focusedStopId: String?, band: StopBand) {
        val markerStops = stops.filterNot(StopMarker::routeStop)
        val liveIds = markerStops.mapTo(HashSet(), StopMarker::id)
        val gone = markerByStopId.iterator()
        while (gone.hasNext()) {
            val entry = gone.next()
            if (entry.key !in liveIds) {
                map.removeAnnotation(entry.value)
                stopByMarker.remove(entry.value)
                kindByStopId.remove(entry.key)
                gone.remove()
            }
        }

        for (stop in markerStops) {
            val kind = stopIconKind(
                focused = stop.id == focusedStopId,
                band = band,
                favorite = stop.favorite,
            )
            val existing = markerByStopId[stop.id]
            if (existing == null) {
                val marker = map.addMarker(
                    MarkerOptions().position(stop.point.toLatLng()).icon(icon(stop, kind))
                )
                markerByStopId[stop.id] = marker
                stopByMarker[marker] = stop
            } else {
                if (kindByStopId[stop.id] != kind) existing.icon = icon(stop, kind)
                if (stopByMarker[existing]?.point != stop.point) {
                    existing.position = stop.point.toLatLng()
                }
                stopByMarker[existing] = stop
            }
            kindByStopId[stop.id] = kind
        }
    }

    fun stopForMarker(marker: Marker): StopMarker? = stopByMarker[marker]

    fun dispose() {
        if (markerByStopId.isNotEmpty()) map.removeAnnotations(markerByStopId.values.toList())
        markerByStopId.clear()
        stopByMarker.clear()
        kindByStopId.clear()
    }

    private fun icon(stop: StopMarker, kind: StopIconKind): Icon = when (kind) {
        StopIconKind.FULL -> MapLibreStopIcons.iconForDirection(context, stop.direction)
        StopIconKind.FULL_FOCUSED -> MapLibreStopIcons.focusedIconForDirection(context, stop.direction)
        StopIconKind.DOT -> MapLibreStopIcons.dotIcon(context)
        StopIconKind.DOT_FOCUSED -> MapLibreStopIcons.focusedDotIcon(context)
        StopIconKind.FAVORITE -> MapLibreStopIcons.favoriteIcon(context, stop.direction)
        StopIconKind.FAVORITE_FOCUSED -> MapLibreStopIcons.focusedFavoriteIcon(context, stop.direction)
        StopIconKind.FAVORITE_DOT -> MapLibreStopIcons.favoriteDotIcon(context)
        StopIconKind.FAVORITE_DOT_FOCUSED -> MapLibreStopIcons.focusedFavoriteDotIcon(context)
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
