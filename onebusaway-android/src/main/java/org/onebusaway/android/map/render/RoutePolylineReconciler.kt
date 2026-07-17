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
 * Reconciles the independently collected route layer against a native map, retaining equal native
 * lines and keeping their widths in sync with the camera zoom. This is the flavor-neutral half of the
 * route-polyline draw that the Google and MapLibre renderers previously duplicated verbatim (#1906):
 * the identity/equality early-exit, the [reconcileEqualItems] diff, the per-index width comparison
 * against the last-drawn widths, the native width patch, the three-field state update, and the
 * camera-settle width resync all live here once.
 *
 * The only genuinely platform-specific parts are supplied as callbacks over the opaque [NativeLine]
 * type: how to resolve a line's pixel width at a zoom ([widthOf]), how to create a native line
 * ([createLine]), how to remove a batch of them ([removeLines]), and how to patch a live line's width
 * ([setWidth]). Everything else — the bookkeeping the fix was about — is shared.
 *
 * Not thread-safe: every method mutates native map state and must run on the map's main thread, which
 * is where both renderers already call it.
 */
class RoutePolylineReconciler<NativeLine>(
    private val widthOf: (RoutePolyline, Float) -> Float,
    private val createLine: (RoutePolyline, Float) -> NativeLine,
    private val removeLines: (List<NativeLine>) -> Unit,
    private val setWidth: (NativeLine, Float) -> Unit,
) {
    // The native lines currently drawn, positionally aligned with [renderedPolylines]/[renderedWidths].
    private val lines = mutableListOf<NativeLine>()
    private var renderedPolylines: List<RoutePolyline> = emptyList()
    private var renderedWidths: List<Float> = emptyList()

    /**
     * Reconcile the drawn lines to [next], computing widths at [zoom]: equal lines are retained (their
     * width patched only when it actually changed), disappearing lines removed, and new lines created.
     * Snapshot copies keep the same list instance, so the common stop-only update is an O(1) identity
     * check; an equal republished value is retained too.
     */
    fun reconcile(next: List<RoutePolyline>, zoom: Float) {
        if (renderedPolylines === next || renderedPolylines == next) return

        val previousNative = lines.toList()
        val previousWidths = renderedWidths
        val reconciliation = reconcileEqualItems(renderedPolylines, next)
        val removed = reconciliation.removedPreviousIndices.map(previousNative::get)
        if (removed.isNotEmpty()) removeLines(removed)
        val nextWidths = next.map { widthOf(it, zoom) }
        val reconciled = next.mapIndexed { index, polyline ->
            val previousIndex = reconciliation.previousIndexForNext[index]
            previousIndex?.let(previousNative::get)
                ?.also {
                    if (previousWidths.getOrNull(previousIndex) != nextWidths[index]) {
                        setWidth(it, nextWidths[index])
                    }
                }
                ?: createLine(polyline, nextWidths[index])
        }
        renderedPolylines = next
        renderedWidths = nextWidths
        lines.clear()
        lines.addAll(reconciled)
    }

    /**
     * On a camera settle, recompute every retained line's width at [zoom] and patch the ones that
     * changed. A no-op when nothing changed, so a settle that doesn't cross a width breakpoint touches
     * no native state.
     */
    fun resyncWidths(zoom: Float) {
        val nextWidths = renderedPolylines.map { widthOf(it, zoom) }
        if (nextWidths != renderedWidths) {
            renderedWidths = nextWidths
            for (index in lines.indices) {
                setWidth(lines[index], nextWidths[index])
            }
        }
    }

    /** Remove every drawn line and drop all state — the renderer's dispose path. */
    fun clear() {
        if (lines.isNotEmpty()) removeLines(lines.toList())
        lines.clear()
        renderedPolylines = emptyList()
        renderedWidths = emptyList()
    }
}
