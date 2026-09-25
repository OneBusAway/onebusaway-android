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
package org.onebusaway.android.map.render

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The on-demand zones as their own change boundary, the zone counterpart of [routePolylineRenderFlow]:
 * a stop refresh, a rental toggle or a badge update leaves this list untouched, so it neither re-emits
 * nor makes the renderer touch a zone polygon.
 */
internal fun onDemandZoneRenderFlow(snapshot: StateFlow<MapRenderSnapshot>): Flow<List<ZonePolygon>> = snapshot.map { it.onDemandZones }.distinctUntilChanged()

/**
 * Keeps a map's native zone polygons in step with [MapRenderSnapshot.onDemandZones], shared by the
 * Google and MapLibre renderers. Zones are matched by value, not by service id: a MultiPolygon area is
 * several zones under one id. An equal zone keeps its native polygon, a gone one is removed and a new
 * one created, so a viewport fetch that returns the same services redraws nothing.
 */
class ZonePolygonReconciler<NativePolygon>(
    /** Draws one zone, or returns null when it has no exterior ring to draw. */
    private val createPolygon: (ZonePolygon) -> NativePolygon?,
    private val removePolygons: (List<NativePolygon>) -> Unit
) {
    // Positionally aligned: drawn[i] is the native polygon for rendered[i], or null when it drew nothing.
    private var rendered: List<ZonePolygon> = emptyList()
    private var drawn: List<NativePolygon?> = emptyList()
    private val zoneByPolygon = HashMap<NativePolygon, ZonePolygon>()

    /** Reconcile the drawn polygons to [next]; true when at least one polygon was created. */
    fun reconcile(next: List<ZonePolygon>): Boolean {
        if (rendered == next) return false
        val reconciliation = reconcileEqualItems(rendered, next)
        val removed = reconciliation.removedPreviousIndices.mapNotNull { drawn[it] }
        if (removed.isNotEmpty()) {
            removePolygons(removed)
            removed.forEach(zoneByPolygon::remove)
        }
        var created = false
        drawn = next.mapIndexed { index, zone ->
            val previousIndex = reconciliation.previousIndexForNext[index]
            if (previousIndex != null) {
                drawn[previousIndex]
            } else {
                createPolygon(zone)?.also {
                    zoneByPolygon[it] = zone
                    created = true
                }
            }
        }
        rendered = next
        return created
    }

    /** The zone [polygon] draws, for a flavour whose polygons report their own clicks. */
    fun zoneFor(polygon: NativePolygon): ZonePolygon? = zoneByPolygon[polygon]

    /** Remove every drawn polygon and drop all state — the renderer's dispose path. */
    fun clear() {
        val natives = drawn.filterNotNull()
        if (natives.isNotEmpty()) removePolygons(natives)
        zoneByPolygon.clear()
        drawn = emptyList()
        rendered = emptyList()
    }
}
