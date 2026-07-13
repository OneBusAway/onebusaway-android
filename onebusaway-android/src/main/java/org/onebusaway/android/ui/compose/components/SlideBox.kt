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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    // Bridges a recomposition-varying lambda into the long-lived effect below, which would
    // otherwise close over the stale first-composition snapshot.
    val currentAnchorPx by rememberUpdatedState(anchorPx)

    // THE scroll owner: the only coroutine that ever moves `scroll` programmatically.
    LaunchedEffect(scroll) {
        // First alignment: snap (don't animate) to the anchor's first resolution, so the box never
        // visibly slides on first display.
        val initialAnchor = snapshotFlow { currentAnchorPx() }.filterNotNull().first()
        scroll.scrollTo(initialAnchor)
        // Every later anchor change glides.
        scroll.glideTo(currentAnchorPx)
    }

    Row(
        modifier = modifier.horizontalScroll(scroll),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
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
