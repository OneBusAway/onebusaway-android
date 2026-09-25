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
 * It also owns the *case* pairing (#2082): a [RoutePolyline] that asks for a [RouteLineCase] draws as two
 * native lines — the case beneath its own stroke — and "create both, remove both, resize both, case underneath"
 * is the same species of bookkeeping as everything above, so it belongs here rather than in each flavor.
 *
 * The only genuinely platform-specific parts are supplied as callbacks over the opaque [NativeLine]
 * type: how to resolve a line's pixel width at a zoom ([widthOf]), how to create a native line
 * ([createLine]), how to remove a batch of them ([removeLines]), how to patch a live line's width
 * ([setWidth]), what colour a case draws in ([caseColorOf] — theme-dependent, so it needs the flavor's
 * `Context`), and how much wider a given line's case is stroked ([caseExtraWidth] — per line, since the two
 * case weights differ, in whatever width unit that flavor's [widthOf] returns). Everything else — the
 * bookkeeping the fixes were about — is shared.
 *
 * [createLine] and [setWidth] are both handed the stroke *and* the mark inset that goes with it (see
 * [markInset]), and [setWidth] the line's model besides: a mark whose size is not simply proportional to
 * the stroke has to be restated when the stroke changes, so a width patch is a mark patch too. A renderer
 * whose marks all scale with the line can ignore both and just set the width.
 *
 * Not thread-safe: every method mutates native map state and must run on the map's main thread, which
 * is where both renderers already call it.
 */
class RoutePolylineReconciler<NativeLine>(
    private val widthOf: (RoutePolyline, Float) -> Float,
    private val createLine: (RoutePolyline, Float, Float) -> NativeLine,
    private val removeLines: (List<NativeLine>) -> Unit,
    private val setWidth: (NativeLine, RoutePolyline, Float, Float) -> Unit,
    private val caseColorOf: (RoutePolyline) -> Int,
    private val caseExtraWidth: (RoutePolyline) -> Float
) {
    /** One drawn case: the native stroke and the model it was drawn from, paired so a width patch can
     *  restate the marks on it (see [markInset]) rather than only its width. */
    private class DrawnCase<NativeLine>(val native: NativeLine, val polyline: RoutePolyline)

    /**
     * One drawn line: its stroke, plus the wider case (halo) stroke beneath it when the line asked for one.
     * Held as a pair so a case cannot outlive its line or drift out of sync with its width.
     */
    private class Drawn<NativeLine>(
        val case: DrawnCase<NativeLine>?,
        val line: NativeLine,
        val polyline: RoutePolyline,
        val caseExtraWidth: Float
    ) {
        /** Both strokes, case first — the order they were added, and so the order they draw. */
        val natives: List<NativeLine> get() = listOfNotNull(case?.native, line)
    }

    // The lines currently drawn, positionally aligned with [renderedPolylines]/[renderedWidths].
    private val drawn = mutableListOf<Drawn<NativeLine>>()
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

        val previousDrawn = drawn.toList()
        val previousWidths = renderedWidths
        val reconciliation = reconcileEqualItems(renderedPolylines, next)
        val removed = reconciliation.removedPreviousIndices.flatMap { previousDrawn[it].natives }
        if (removed.isNotEmpty()) removeLines(removed)
        val nextWidths = next.map { widthOf(it, zoom) }
        val reconciled = next.mapIndexed { index, polyline ->
            val previousIndex = reconciliation.previousIndexForNext[index]
            previousIndex?.let(previousDrawn::get)
                ?.also {
                    if (previousWidths.getOrNull(previousIndex) != nextWidths[index]) {
                        it.applyWidth(nextWidths[index])
                    }
                }
                ?: create(polyline, nextWidths[index])
        }
        renderedPolylines = next
        renderedWidths = nextWidths
        drawn.clear()
        drawn.addAll(reconciled)
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
            for (index in drawn.indices) {
                drawn[index].applyWidth(nextWidths[index])
            }
        }
    }

    /** Remove every drawn line and drop all state — the renderer's dispose path. */
    fun clear() {
        val natives = drawn.flatMap { it.natives }
        if (natives.isNotEmpty()) removeLines(natives)
        drawn.clear()
        renderedPolylines = emptyList()
        renderedWidths = emptyList()
    }

    /**
     * Draw [polyline] and, when it asks to be cased, its case first — both map SDKs draw equal-z lines in the
     * order they were added, which is the same thing that layers the polyline list itself, so creating the case
     * ahead of its own stroke is what puts it underneath.
     */
    private fun create(polyline: RoutePolyline, width: Float): Drawn<NativeLine> {
        val extra = caseExtraWidth(polyline)
        val casePolyline = polyline.takeIf { it.case != RouteLineCase.NONE }?.asCase(caseColorOf(polyline))
        return Drawn(
            case = casePolyline?.let { DrawnCase(createLine(it, width + extra, markInset(extra)), it) },
            line = createLine(polyline, width, NO_MARK_INSET),
            polyline = polyline,
            // Retained with the pair: a width resync patches the case from the line's new width, and the
            // amount to add is a property of the case that was drawn, not of the reconcile doing the patching.
            caseExtraWidth = extra
        )
    }

    private fun Drawn<NativeLine>.applyWidth(width: Float) {
        setWidth(line, polyline, width, NO_MARK_INSET)
        case?.let { setWidth(it.native, it.polyline, width + caseExtraWidth, markInset(caseExtraWidth)) }
    }

    private companion object {
        /** A line's own marks are drawn at its full stroke — only a case insets them. */
        const val NO_MARK_INSET = 0f

        /**
         * How far a case's end marks are drawn *inside* its own stroke: half its extra width, which is the
         * weight the case shows along the line.
         *
         * Without it a bulb's halo comes out at twice the weight of the case beside it. A bulb is a circle
         * of the line's own stroke width, so the case's copy of it is a circle of the *case's* stroke width
         * — wider by the whole extra, while the case shows only half of that on each side of the line it
         * wraps. The halo is the one place a case's weight is set by the mark's size rather than by the
         * line's, so it is the one place that has to be told (#2241).
         */
        fun markInset(caseExtraWidth: Float) = caseExtraWidth / 2f
    }
}

/**
 * The line model a case is drawn from: a case is geometry, weight, dash and its own [color], and nothing
 * else. Built field by field rather than `copy()`-minus-a-list so that is true by construction — a new
 * [RoutePolyline] feature is then absent from a case unless someone adds it here, which is the safe default
 * (a feature that quietly rode along would draw twice, once at each width). Routed back through the
 * renderer's ordinary line creation so a case still inherits every *drawing* feature — its dash rhythm, and
 * on gms the cheap solid-colour path a non-directional line takes — rather than each flavor rebuilding a
 * parallel one that has to be kept in step.
 *
 * Travel chevrons are the clearest thing left out: a case is an outline, and stamping the arrows into it
 * would only double the ones its own line carries.
 */
private fun RoutePolyline.asCase(color: Int) = RoutePolyline(
    color = color,
    points = points,
    widthProfile = widthProfile,
    dash = dash,
    startMark = startMark.asCaseMark(),
    endMark = endMark.asCaseMark(),
    transforms = transforms
)

/**
 * What an end mark becomes on a case. A [RouteLineMark.BULB] stays — it is drawn in the line's own colour, so
 * casing it is a ring that reads exactly like the case around the line it caps. An interline cut doesn't: it
 * is *already* drawn in the case colour, so a wider copy of it underneath would only fringe the mark on the
 * line above with a bigger version of itself.
 */
private fun RouteLineMark.asCaseMark() = if (this == RouteLineMark.BULB) this else RouteLineMark.NONE
