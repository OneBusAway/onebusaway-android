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
@file:Suppress("DEPRECATION") // classic Marker/Icon annotation API; SymbolManager migration tracked in #1728

package org.onebusaway.android.map.maplibre

import android.content.Context
import android.util.LruCache
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.maps.MapLibreMap
import org.onebusaway.android.map.render.StopBand
import org.onebusaway.android.map.render.StopIconKind
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.render.StopRoute
import org.onebusaway.android.map.render.StopRouteLabelBitmaps
import org.onebusaway.android.map.render.stopIconKind
import org.onebusaway.android.map.render.stopRouteLabel
import org.onebusaway.android.util.ThemeUtils

/** Owns non-route stop marker identity, icon reconciliation, tap lookup, and disposal. */
internal class MapLibreStopMarkerLayer(
    private val map: MapLibreMap,
    private val context: Context
) {
    private val markerByStopId = HashMap<String, Marker>()
    private val stopByMarker = HashMap<Marker, StopMarker>()
    private val kindByStopId = HashMap<String, StopIconKind>()

    // The transit-centre route label beside each stop (#2107), reconciled alongside its marker rather
    // than redrawn: at the zoom these appear the map still loads stops on every pan, and labels torn down
    // and re-added on each of those publishes would blink while the markers under them held still.
    private val labelByStopId = HashMap<String, Marker>()

    // One Icon per distinct set of routes: a transit centre's bays repeat each other's routes, and a label
    // bitmap is mostly transparent lift, so sharing them is worth more here than for a stop icon. Bounded
    // for the reason the renderer's badge cache is — an Icon holds its bitmap for the lifetime of the map.
    private val labelIcons = LruCache<String, Icon>(LABEL_ICON_CACHE_SIZE)

    private val iconFactory = IconFactory.getInstance(context)

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

        // A second pass, after every marker exists: classic maplibre annotations have no z-index and draw
        // in add order, so labels added with their markers would each sit under the icons of the stops
        // added after them — at these zooms exactly the neighbour a rider is comparing against.
        //
        // Only within one render; a stop that first appears in a later one still draws over earlier
        // labels. Re-adding every live label whenever a render adds a marker would order those too, and
        // is deliberately not done: at this zoom a pan publishes new stops continuously, so it would tear
        // down and re-add the whole viewport's labels over and over — the churn this reconcile exists to
        // avoid — to correct an overlap that costs a stop circle's worth of one label. The fix that costs
        // nothing is explicit layer ordering, which is the SymbolManager migration (#1728).
        for (stop in markerStops) renderLabel(stop, band)
    }

    fun stopForMarker(marker: Marker): StopMarker? = stopByMarker[marker]

    fun dispose() {
        val annotations = markerByStopId.values + labelByStopId.values
        if (annotations.isNotEmpty()) map.removeAnnotations(annotations)
        markerByStopId.clear()
        labelByStopId.clear()
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
            // The classic Marker always centres its icon on the point, which is the placement the label
            // bitmap is built for — its lift above the stop is baked in (see StopRouteLabelBitmaps).
            val marker = map.addMarker(
                MarkerOptions().position(stop.point.toLatLng()).icon(labelIcon(routes))
            )
            labelByStopId[stop.id] = marker
            stopByMarker[marker] = stop
        } else {
            // The label's own previous stop, which is what it was drawn from — a label exists only where
            // the last render was in the labelling band, so its routes are the ones on the pill.
            val previous = stopByMarker[existing]
            if (previous?.routes != routes) existing.icon = labelIcon(routes)
            if (previous?.point != stop.point) existing.position = stop.point.toLatLng()
            stopByMarker[existing] = stop
        }
    }

    private fun removeLabel(stopId: String) {
        labelByStopId.remove(stopId)?.let {
            stopByMarker.remove(it)
            map.removeAnnotation(it)
        }
    }

    /**
     * The shared icon for a label naming [routes], drawn in the current theme.
     *
     * A live label is re-iconed only when its routes change (see [renderLabel]) — the theme can't change
     * under one, since a light/dark switch recreates the activity and with it the map, this layer and this
     * cache. The label is drawn from the unrendered [StopRoute]s that survive that recreate in the view
     * model, which is what re-colours the labels rather than restoring the ones drawn before the switch.
     */
    private fun labelIcon(routes: List<StopRoute>): Icon {
        val darkMode = ThemeUtils.isInDarkMode(context)
        val key = StopRouteLabelBitmaps.labelKey(routes, darkMode)
        return labelIcons.get(key) ?: iconFactory
            .fromBitmap(StopRouteLabelBitmaps.label(context, routes, darkMode))
            .also { labelIcons.put(key, it) }
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

    private companion object {
        /** Comfortably more distinct route sets than a viewport at transit-centre zoom holds stops. */
        const val LABEL_ICON_CACHE_SIZE = 64
    }
}
