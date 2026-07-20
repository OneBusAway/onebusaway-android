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
package org.onebusaway.android.ui.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

// SlideBox: a horizontally scrollable row that glides its content toward a declared REST target
// (an anchor offset) rather than being driven imperatively. Callers declare where the box should
// rest; they never issue scroll commands. Extracted from the arrivals ETA strip (issue #1801) so its
// pinned-pill glide (the strip's leading pill tracking the soonest upcoming trip) has a single scroll
// owner — the widget itself is the only coroutine that ever mutates the ScrollState, which is what
// makes a moving anchor converge instead of racing a second mutator.

/**
 * A horizontally scrollable row that owns every programmatic mutation of [scroll]:
 *
 * - **Declare targets, not commands.** [anchorPx] is the content-space offset the leading edge
 *   should rest on (null while there's nothing to chase yet). The box glides itself; callers never
 *   call animateScrollTo. Driving [scroll] directly (`scrollTo`/`animateScrollTo`) from outside the
 *   box would contest its single glide owner — callers may hold onto it to OBSERVE (edge fades,
 *   chevrons, previews, tests), never to drive it.
 * - **Anchors are always reachable.** `horizontalScroll` caps its scroll range at
 *   `contentWidth - viewport`, so an anchor deeper than that (short content that doesn't overflow)
 *   is otherwise unreachable and the glide silently stalls short of it. [minReachablePx] names an
 *   offset the box must be able to scroll to regardless: the content is floored to that offset plus
 *   one viewport, so `maxValue` always reaches it. It's `max(children, floor)`, so genuinely wider
 *   content is untouched — no trailing void, no phantom forward-scroll.
 * - **One scroll owner.** A single effect chases the anchor for the lifetime of the box, so two
 *   programmatic mutations can never contest the ScrollState and livelock the main thread
 *   (issue #1801).
 * - **First alignment snaps, later motion glides.** The first time the anchor resolves the box
 *   jumps there instantly (no entry animation); every later anchor change animates.
 * - **Real user input always wins.** A drag steals the scrollable mutex from the glide, which just
 *   waits for idle before re-deriving its target.
 */
@Composable
internal fun SlideBox(
    scroll: ScrollState,
    anchorPx: () -> Int?,
    modifier: Modifier = Modifier,
    // An offset the box must always be able to scroll to (null = no floor); the content is widened to
    // this offset + one viewport so short content can't cap maxValue below it. See the KDoc above.
    minReachablePx: () -> Int? = { null },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    // Bridges a recomposition-varying lambda into the long-lived effect below, which would
    // otherwise close over the stale first-composition snapshot.
    val currentAnchorPx by rememberUpdatedState(anchorPx)

    // THE scroll owner: the only coroutine that ever moves `scroll` programmatically.
    LaunchedEffect(scroll) {
        // First alignment SNAPS to the anchor — never animates — so the strip appears already
        // justified on first display and when a row re-enters a LazyColumn (which composes it fresh).
        // A minReachablePx floor can lift maxValue a frame or two after the anchor first resolves (the
        // freshly measured pinned offset and viewport widen the content only on later layout passes),
        // so a single snap would land on the clamped-short position and leave the real alignment to
        // the gliding loop below — replaying the slide on every re-entry. Re-snap (all instantaneous
        // scrollTo) as the floor lands, until the box sits on the true anchor or maxValue stops
        // climbing toward it. Precondition: a non-null anchor is expected to become reachable (via
        // content width or minReachablePx); otherwise the box simply rests at the clamped edge.
        snapshotFlow { currentAnchorPx() }.filterNotNull().first()
        var snappedMax = -1
        while (true) {
            val anchor = currentAnchorPx() ?: break
            scroll.scrollTo(anchor.coerceAtMost(scroll.maxValue))
            // Stop re-snapping — and let the glide loop below take over at the clamped edge — once the
            // anchor is reachable, maxValue has stopped climbing, or the floor that would lift maxValue
            // has fully landed (or was never declared). The floor guarantees maxValue reaches at least
            // minReachablePx; when maxValue already meets it, no further climb is coming. Without this
            // last guard the default minReachablePx = { null } would leave a non-overflowing anchor
            // waiting forever for a maxValue climb that never arrives.
            val floorReach = minReachablePx() ?: 0
            if (anchor <= scroll.maxValue || scroll.maxValue <= snappedMax || floorReach <= scroll.maxValue) break
            snappedMax = scroll.maxValue
            snapshotFlow { currentAnchorPx() != anchor || scroll.maxValue != snappedMax }.first { it }
        }
        // Every later anchor change glides.
        scroll.glideTo(currentAnchorPx)
    }

    // The content-width floor for widthIn below: the reachable offset plus one viewport, so
    // horizontalScroll's maxValue (= contentWidth - viewport) can reach that offset. Recomputed in
    // composition — reads minReachablePx() and viewportSize as snapshot state — so the floor tracks
    // both the pinned offset and viewport-size changes. 0 (no floor) when nothing must be reachable.
    val minContentWidth = with(LocalDensity.current) {
        (minReachablePx()?.let { it + scroll.viewportSize } ?: 0).toDp()
    }

    Row(
        modifier = modifier
            .horizontalScroll(scroll)
            // Floor the content width so a declared minReachablePx offset stays scroll-reachable even
            // when the real content would fit the viewport. Applied INSIDE horizontalScroll (where the
            // width constraint is infinite), so it only ever RAISES the min — content already wider
            // than the floor is measured unchanged, adding no trailing void.
            .widthIn(min = minContentWidth),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}

/**
 * Repeatedly glides toward whatever [target] currently evaluates to (null means "nothing to chase
 * right now"), re-deriving it fresh each iteration rather than animating once toward a value captured
 * before the glide started — so a moving target (the pinned pill advancing as trips depart) is
 * chased rather than over/undershot. [target] is read inside `snapshotFlow`, so it must be a plain
 * synchronous read of snapshot state, not a suspend function. Never contests a real user drag: losing
 * the scrollable mutex to one just waits for the box to go idle before re-deriving the target on the
 * next iteration.
 *
 * Clamped to `[0, maxValue]` before comparing OR animating — `animateScrollTo` clamps internally, but
 * comparing against the raw (unclamped) target would keep re-firing forever once `value` settles at
 * `maxValue` for a target that's past the reachable end (e.g. an anchor sitting near the end of
 * short content).
 */
private suspend fun ScrollState.glideTo(target: () -> Int?) {
    while (true) {
        snapshotFlow { target()?.coerceIn(0, maxValue) }.filterNotNull().first { it != value }
        val next = target()?.coerceIn(0, maxValue) ?: continue
        try {
            animateScrollTo(next)
        } catch (cause: CancellationException) {
            currentCoroutineContext().ensureActive() // real teardown propagates
            snapshotFlow { isScrollInProgress }.first { !it }
        }
    }
}
