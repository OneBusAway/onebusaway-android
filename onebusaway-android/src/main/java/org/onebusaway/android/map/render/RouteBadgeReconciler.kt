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

/**
 * Keeps a flavor's native route-badge markers in sync with the model list, retaining the markers whose
 * badge is unchanged — the badge counterpart to [RoutePolylineReconciler], and the reason the badges
 * can be redrawn incrementally instead of via the static layer's clear-and-rebuild.
 *
 * Badges used to live in the renderers' static annotation layer, which every [MapRenderSnapshot]
 * change tears down and re-adds wholesale. That was survivable while the layer drew a focused stop's
 * handful of adjacent routes, but the nearby-routes hoop (#2004) publishes its routes progressively as
 * each shape resolves, so a downtown survey re-created every annotation on the map dozens of times over
 * and read as a sustained blink. Reconciling means an emission that adds one badge adds exactly one
 * marker.
 *
 * A badge is matched by value, so *any* change to it — position, colour, label — is a remove-and-add
 * for that badge alone. Duplicates are handled as a multiset by [reconcileEqualItems]. The badge model
 * carries no zoom-dependent state, so unlike the line layer there are no widths to resync on a camera
 * settle: a pure pan or zoom touches no badge state at all.
 *
 * Not thread-safe: every method mutates native map state and must run on the map's main thread, which
 * is where both renderers already call it.
 */
class RouteBadgeReconciler<NativeMarker>(
    private val createMarker: (RouteBadge) -> NativeMarker,
    private val removeMarkers: (List<NativeMarker>) -> Unit
) {
    // The native markers currently drawn, positionally aligned with [rendered].
    private val markers = mutableListOf<NativeMarker>()
    private var rendered: List<RouteBadge> = emptyList()

    /**
     * Reconcile the drawn badges to [next]: unchanged badges keep their marker, disappearing ones are
     * removed, new ones created. Snapshot copies keep the same list instance, so an unrelated update
     * is an O(1) identity check; an equal republished value is retained too.
     */
    fun reconcile(next: List<RouteBadge>) {
        if (rendered === next || rendered == next) return

        val previousNative = markers.toList()
        val reconciliation = reconcileEqualItems(rendered, next)
        val removed = reconciliation.removedPreviousIndices.map(previousNative::get)
        if (removed.isNotEmpty()) removeMarkers(removed)
        val reconciled = next.mapIndexed { index, badge ->
            reconciliation.previousIndexForNext[index]?.let(previousNative::get) ?: createMarker(badge)
        }
        rendered = next
        markers.clear()
        markers.addAll(reconciled)
    }

    /** Remove every drawn badge and drop all state — the renderer's dispose path. */
    fun clear() {
        if (markers.isNotEmpty()) removeMarkers(markers.toList())
        markers.clear()
        rendered = emptyList()
    }
}
