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
package org.onebusaway.android.map.googlemapsv2

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.map.render.StopIconKind
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.stopIconKind
import org.onebusaway.android.map.render.stopZIndex

/** Owns non-route stop marker identity, icon reconciliation, tap lookup, and disposal. */
internal class GoogleStopMarkerLayer(
    private val map: GoogleMap,
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
                stopByMarker.remove(entry.value)
                kindByStopId.remove(entry.key)
                entry.value.remove()
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
                val (anchorX, anchorY) = anchor(stop, kind)
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(stop.point.toLatLng())
                        .icon(icon(stop, kind))
                        .flat(true)
                        .anchor(anchorX, anchorY)
                        .zIndex(stopZIndex(routeStop = false, favorite = stop.favorite))
                )!!
                markerByStopId[stop.id] = marker
                stopByMarker[marker] = stop
            } else {
                if (kindByStopId[stop.id] != kind) {
                    existing.setIcon(icon(stop, kind))
                    val (anchorX, anchorY) = anchor(stop, kind)
                    existing.setAnchor(anchorX, anchorY)
                }
                val previous = stopByMarker[existing]
                if (previous?.point != stop.point) existing.position = stop.point.toLatLng()
                if (previous?.favorite != stop.favorite) {
                    existing.zIndex = stopZIndex(routeStop = false, favorite = stop.favorite)
                }
                stopByMarker[existing] = stop
            }
            kindByStopId[stop.id] = kind
        }
    }

    fun stopForMarker(marker: Marker): StopMarker? = stopByMarker[marker]

    fun dispose() {
        markerByStopId.values.forEach(Marker::remove)
        markerByStopId.clear()
        stopByMarker.clear()
        kindByStopId.clear()
    }

    private fun icon(stop: StopMarker, kind: StopIconKind): BitmapDescriptor = when (kind) {
        StopIconKind.FULL -> StopIconFactory.stopIcon(context, stop.direction, stop.routeType)
        StopIconKind.FULL_FOCUSED -> StopIconFactory.focusedStopIcon(context, stop.direction, stop.routeType)
        StopIconKind.DOT -> StopIconFactory.dotStopIcon(context)
        StopIconKind.DOT_FOCUSED -> StopIconFactory.focusedDotStopIcon(context)
        StopIconKind.FAVORITE -> StopIconFactory.favoriteStopIcon(context, stop.direction)
        StopIconKind.FAVORITE_FOCUSED -> StopIconFactory.focusedFavoriteStopIcon(context, stop.direction)
        StopIconKind.FAVORITE_DOT -> StopIconFactory.favoriteDotStopIcon(context)
        StopIconKind.FAVORITE_DOT_FOCUSED -> StopIconFactory.focusedFavoriteDotStopIcon(context)
    }

    private fun anchor(stop: StopMarker, kind: StopIconKind): Pair<Float, Float> = when (kind) {
        StopIconKind.FULL, StopIconKind.FULL_FOCUSED ->
            StopIconFactory.anchorX(context, stop.direction) to
                StopIconFactory.anchorY(context, stop.direction)
        else -> 0.5f to 0.5f
    }
}
