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
import android.util.LruCache
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.onebusaway.android.map.render.STOP_ROUTE_LABEL_Z_INDEX
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.map.render.StopIconKind
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.map.render.StopRouteLabelBitmaps
import org.onebusaway.android.map.render.stopIconKind
import org.onebusaway.android.map.render.stopRouteLabel
import org.onebusaway.android.map.render.stopZIndex
import org.onebusaway.android.util.ThemeUtils

/** Owns non-route stop marker identity, icon reconciliation, tap lookup, and disposal. */
internal class GoogleStopMarkerLayer(
    private val map: GoogleMap,
    private val context: Context
) {
    private val markerByStopId = HashMap<String, Marker>()
    private val stopByMarker = HashMap<Marker, StopMarker>()
    private val kindByStopId = HashMap<String, StopIconKind>()

    // The transit-centre route label beside each stop (#2107), reconciled alongside its marker rather
    // than redrawn: at the zoom these appear the map still loads stops on every pan, and labels torn down
    // and re-added on each of those publishes would blink while the markers under them held still.
    private val labelByStopId = HashMap<String, Marker>()
    private val labelRoutesByStopId = HashMap<String, List<StopRoute>>()

    // One descriptor per distinct set of routes: a transit centre's bays repeat each other's routes, and a
    // label bitmap is mostly transparent lift, so sharing them is worth more here than for a stop icon.
    private val labelIcons = LruCache<String, BitmapDescriptor>(LABEL_ICON_CACHE_SIZE)

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
                removeLabel(entry.key)
            }
        }

        for (stop in markerStops) {
            val kind = stopIconKind(
                focused = stop.id == focusedStopId,
                band = band,
                favorite = stop.favorite
            )
            val existing = markerByStopId[stop.id]
            if (existing == null) {
                val (anchorX, anchorY) = anchor(stop, kind)
                val marker = map.addMarkerOrFail(
                    MarkerOptions()
                        .position(stop.point.toLatLng())
                        .icon(icon(stop, kind))
                        .flat(true)
                        .anchor(anchorX, anchorY)
                        .zIndex(stopZIndex(routeStop = false, favorite = stop.favorite))
                )
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

        // A second pass, after every marker exists: markers added in one pass with their labels would
        // leave each label under the icons of the stops added after it (the SDK breaks a z-index tie by
        // add order), which at these zooms is exactly the neighbour a rider is comparing against.
        for (stop in markerStops) renderLabel(stop, band)
    }

    fun stopForMarker(marker: Marker): StopMarker? = stopByMarker[marker]

    fun dispose() {
        markerByStopId.values.forEach(Marker::remove)
        markerByStopId.clear()
        labelByStopId.values.forEach(Marker::remove)
        labelByStopId.clear()
        labelRoutesByStopId.clear()
        labelIcons.evictAll()
        stopByMarker.clear()
        kindByStopId.clear()
    }

    /**
     * Add, re-stamp or drop [stop]'s route label so it matches what the stop names at [band]. Its marker
     * is registered in [stopByMarker] like the stop's own, so tapping a label focuses the stop it labels
     * — it reads as part of that marker, and a label that swallowed the tap would read as a dead one.
     */
    private fun renderLabel(stop: StopMarker, band: StopBand) {
        val routes = stopRouteLabel(stop, band)
        if (routes.isEmpty()) {
            removeLabel(stop.id)
            return
        }
        val existing = labelByStopId[stop.id]
        if (existing == null) {
            val marker = map.addMarkerOrFail(
                MarkerOptions()
                    .position(stop.point.toLatLng())
                    .icon(labelIcon(routes))
                    // The lift above the stop is baked into the bitmap, so the label is centered on the
                    // stop's own point exactly as a route label is on its line (see StopRouteLabelBitmaps).
                    .anchor(0.5f, 0.5f)
                    .zIndex(STOP_ROUTE_LABEL_Z_INDEX)
            )
            labelByStopId[stop.id] = marker
            stopByMarker[marker] = stop
        } else {
            if (labelRoutesByStopId[stop.id] != routes) existing.setIcon(labelIcon(routes))
            if (stopByMarker[existing]?.point != stop.point) existing.position = stop.point.toLatLng()
            stopByMarker[existing] = stop
        }
        labelRoutesByStopId[stop.id] = routes
    }

    private fun removeLabel(stopId: String) {
        labelByStopId.remove(stopId)?.let {
            stopByMarker.remove(it)
            it.remove()
        }
        labelRoutesByStopId.remove(stopId)
    }

    private fun labelIcon(routes: List<StopRoute>): BitmapDescriptor {
        val darkMode = ThemeUtils.isInDarkMode(context)
        val key = StopRouteLabelBitmaps.labelKey(routes, darkMode)
        return labelIcons.get(key) ?: BitmapDescriptorFactory
            .fromBitmap(StopRouteLabelBitmaps.label(context, routes, darkMode))
            .also { labelIcons.put(key, it) }
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

    private companion object {
        /** Comfortably more distinct route sets than a viewport at transit-centre zoom holds stops. */
        const val LABEL_ICON_CACHE_SIZE = 64
    }
}
