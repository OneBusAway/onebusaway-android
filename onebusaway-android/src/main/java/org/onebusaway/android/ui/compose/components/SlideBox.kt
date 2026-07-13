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

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// SlideBox: a horizontally scrollable row that owns every gesture on its content — drag-to-scroll,
// programmatic glides toward declared rest targets, and a resistive pull-past-the-end that fires an
// action — behind one declarative surface. Callers declare where the box should REST (an anchor
// offset, or the trailing end while a reveal is in flight); they never issue scroll commands.
// Internally exactly one coroutine ever mutates the ScrollState, so the widget itself can never
// re-create the issue #1801 failure mode it was extracted (from the arrivals ETA strip) to kill:
// two Default-priority animateScrollTo mutations on one ScrollState cancelling each other at CPU
// speed until the main thread ANRs. The ScrollState stays hoistable for OBSERVATION (fades,
// chevrons, previews, tests); the single-owner guarantee holds as long as callers keep declaring
// targets instead of driving it themselves.

/** Finger travel maps to pull distance at this ratio, so the pull lags the finger for a rubber-band feel. */
private const val PULL_RESISTANCE = 0.5f
/** Pull distance (post-resistance) that arms the release-to-fire trigger. */
private val PULL_FIRE_THRESHOLD = 48.dp
/** Pull distance is clamped here so the box can't be dragged arbitrarily far off its end. */
private val PULL_MAX = 72.dp

/**
 * The observable half of a [SlideBox]: its [scroll] position plus the live pull-past-the-end
 * progress, hoisted so callers can draw their own adornments (edge fades, chevrons, a release-to-act
 * chip) from the same state the gesture writes. The gesture internals — the nested-scroll
 * connection, resistance math, haptics — stay inside [SlideBox]; the pull state here is read-only.
 */
@Stable
internal class SlideBoxState(
    /** The box's scroll position, exposed to OBSERVE (adornments, settle predicates, tests).
     *  Driving it directly (`scrollTo`/`animateScrollTo`) would contest the box's single glide
     *  owner — the exact two-mutator livelock [SlideBox] exists to prevent (issue #1801). */
    val scroll: ScrollState,
    internal val pullTriggerPx: Float,
    internal val maxPullPx: Float,
) {
    /** Current pull distance in px (post-resistance). Written from the nested-scroll callbacks and
     *  read in the layout/graphics phase, so a growing pull never recomposes the whole box. */
    internal val pullPx = mutableFloatStateOf(0f)

    /** How far the pull is toward arming, 0..1 — safe to read in the graphics phase. */
    val pullProgress: Float
        get() = (pullPx.floatValue / pullTriggerPx).coerceIn(0f, 1f)

    /** Whether the pull has crossed the release-to-fire threshold. A derivedState so only its
     *  readers (e.g. the caller's chip) recompose when it flips — never the whole box per frame. */
    val armed: Boolean by derivedStateOf { pullPx.floatValue >= pullTriggerPx }

    /** Whether the box is at rest at its true trailing end — the settled state of [SlideBox]'s
     *  followEnd regime, defined once here (by the thing that owns the gliding) so callers waiting
     *  for a reveal to finish don't re-derive it from raw scroll state and drift out of sync with
     *  the widget's glide semantics. */
    val isSettledAtEnd: Boolean by derivedStateOf {
        scroll.value == scroll.maxValue && !scroll.isScrollInProgress
    }
}

/** Remembers a [SlideBoxState] around [scroll], resolving the pull thresholds at current density. */
@Composable
internal fun rememberSlideBoxState(scroll: ScrollState = rememberScrollState()): SlideBoxState {
    val density = LocalDensity.current
    return remember(scroll, density) {
        with(density) {
            SlideBoxState(
                scroll = scroll,
                pullTriggerPx = PULL_FIRE_THRESHOLD.toPx(),
                maxPullPx = PULL_MAX.toPx(),
            )
        }
    }
}

/**
 * A horizontally scrollable row that owns every gesture on its one [SlideBoxState.scroll]:
 *
 * - **Declare targets, not commands.** [anchorPx] is the content-space offset the leading edge
 *   should rest on (null while there's nothing to chase); while [followEnd] is true the trailing
 *   end owns the scroll instead (a load-more reveal chasing its own growing content). The box
 *   glides itself; callers never call animateScrollTo.
 * - **Anchors are always reachable.** `horizontalScroll` caps its scroll range at
 *   `contentWidth - viewport`, so an anchor deeper than that (short content that doesn't overflow)
 *   is otherwise unreachable and the glide silently stalls short of it. [minReachablePx] names an
 *   offset the box must be able to scroll to regardless: the content is floored to that offset plus
 *   one viewport, so `maxValue` always reaches it. It's `max(children, floor)`, so genuinely wider
 *   content is untouched — no trailing void, no phantom forward-scroll. The floor is dropped while
 *   [followEnd] is true, so it never fights the end regime's chase of the real content end.
 * - **One scroll owner.** A single effect runs one glide regime at a time, strictly sequentially,
 *   so two programmatic mutations can never contest the ScrollState from inside the widget and
 *   livelock the main thread (issue #1801). Callers observe [SlideBoxState.scroll] but must not
 *   drive it.
 * - **First alignment snaps, later motion glides.** The first time the anchor resolves the box
 *   jumps there instantly (no entry animation); every later anchor/end change animates.
 * - **Hand-back is edge-triggered.** When [followEnd] flips off, the anchor re-engages only on its
 *   *next* change — not at its stale level — so whatever the end regime revealed stays on screen
 *   instead of immediately sliding away.
 * - **Real user input always wins.** A drag steals the scrollable mutex from any glide (which just
 *   waits for idle), and [onUserScroll] tells the caller so it can end a reveal it no longer owns.
 *
 * Dragging past the end (once there's nothing left to scroll) builds a resistive pull that
 * [SlideBoxState] exposes as [SlideBoxState.pullProgress]/[SlideBoxState.armed]; releasing while
 * armed fires [onPullFired]. [contentModifier] is spliced between the pull offset and
 * `horizontalScroll` for caller layout hooks that must observe the scroll content's measure pass
 * (e.g. a layout-acknowledgement modifier like EtaStrip's `acknowledgeVersion`); [overlay] draws
 * the caller's adornments over the scrolling content, clipped to the box.
 */
@Composable
internal fun SlideBox(
    state: SlideBoxState,
    anchorPx: () -> Int?,
    followEnd: Boolean,
    onPullFired: () -> Unit,
    onUserScroll: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    // An offset the box must always be able to scroll to (null = no floor); the content is widened to
    // this offset + one viewport so short content can't cap maxValue below it. Automatically
    // suppressed while followEnd is true. See the KDoc above.
    minReachablePx: () -> Int? = { null },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    // Bridge recomposition-varying values into the long-lived effect and connection below, which
    // would otherwise close over the stale first-composition snapshot.
    val currentAnchorPx by rememberUpdatedState(anchorPx)
    val follow by rememberUpdatedState(followEnd)
    val currentOnPullFired by rememberUpdatedState(onPullFired)
    val currentOnUserScroll by rememberUpdatedState(onUserScroll)

    // THE scroll owner: the only coroutine that ever moves state.scroll programmatically. Each
    // regime's glide job is cancelled (and joined, via coroutineScope) before the next regime
    // starts, so overlap is structurally impossible.
    LaunchedEffect(state) {
        val scroll = state.scroll
        // One glide regime at a time: launch the regime's glide, wait for `follow` to flip to
        // [untilFollowIs], then cancel — and coroutineScope JOINS the cancelled glide before
        // returning, so two regimes' mutations can never overlap. This is the single-owner
        // invariant the whole widget exists for.
        suspend fun glideRegime(untilFollowIs: Boolean, target: () -> Int?) = coroutineScope {
            val glide = launch { scroll.glideTo(target) }
            snapshotFlow { follow }.first { it == untilFollowIs }
            glide.cancel()
        }
        // First alignment SNAPS to the anchor — never animates — so the strip appears already
        // justified on first display and when a row re-enters a LazyColumn (which composes it fresh).
        // A minReachablePx floor can lift maxValue a frame or two after the anchor first resolves (the
        // freshly measured pinned offset and viewport widen the content only on later layout passes),
        // so a single snap would land on the clamped-short position and leave the real alignment to
        // the gliding loop below — replaying the slide on every re-entry. Re-snap (all instantaneous
        // scrollTo) as the floor lands, until the box sits on the true anchor or maxValue stops
        // climbing toward it. Precondition: a non-follow anchor is expected to become reachable (via
        // content width or minReachablePx); otherwise the box simply rests at the clamped edge.
        snapshotFlow { currentAnchorPx() != null || follow }.first { it }
        var snappedMax = -1
        while (!follow) {
            val anchor = currentAnchorPx() ?: break
            scroll.scrollTo(anchor.coerceAtMost(scroll.maxValue))
            if (anchor <= scroll.maxValue || scroll.maxValue <= snappedMax) break
            snappedMax = scroll.maxValue
            snapshotFlow { follow || currentAnchorPx() != anchor || scroll.maxValue != snappedMax }
                .first { it }
        }
        while (true) {
            // ANCHOR regime: chase the declared anchor (a moving target — see glideTo) until the
            // end takes over. The gate inside the target lambda parks the chase should this loop
            // re-enter while followEnd is already true.
            glideRegime(untilFollowIs = true) { if (follow) null else currentAnchorPx() }
            // END regime: chase the (growing) trailing end until the caller hands the scroll back.
            glideRegime(untilFollowIs = false) {
                if (follow) scroll.maxValue.takeIf { it > scroll.value } else null
            }
            // Hand-back: the end regime deliberately parked the box at whatever it revealed, so
            // re-engage the anchor only when it next MOVES — chasing its stale level here would
            // immediately slide the revealed content back off screen. (followEnd re-firing during
            // this wait short-circuits back into the regimes above.)
            val staleAnchor = currentAnchorPx()
            snapshotFlow { follow || currentAnchorPx() != staleAnchor }.first { it }
        }
    }

    val haptic = LocalHapticFeedback.current
    val connection = remember(state, haptic) {
        object : NestedScrollConnection {
            // Dragging back toward the content unwinds an active pull before the box itself scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Programmatic glides dispatch as SideEffect; only real user input drives the pull
                // and the caller's takeover hook (which fires for ANY real drag, pull or not).
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                currentOnUserScroll()
                // Allocation-free exit for the common case (an ordinary scroll, no active pull):
                // this runs per pointer delta of every drag, so the PullDelta below is only built
                // while a pull is genuinely being unwound.
                if (available.x <= 0f || state.pullPx.floatValue <= 0f) return Offset.Zero
                val delta = unwindPull(state.pullPx.floatValue, available.x, PULL_RESISTANCE)
                state.pullPx.floatValue = delta.pullPx
                return Offset(delta.consumedX, 0f)
            }

            // Past the box's end, a further left-drag (negative x) builds the resistive pull; a
            // light tick fires the instant it crosses the arm threshold (rising edge only).
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.x >= 0f ||
                    state.scroll.canScrollForward
                ) {
                    return Offset.Zero
                }
                val before = state.pullPx.floatValue
                val delta = buildPull(before, available.x, PULL_RESISTANCE, state.maxPullPx)
                state.pullPx.floatValue = delta.pullPx
                if (crossesArmThreshold(before, delta.pullPx, state.pullTriggerPx)) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                return Offset(delta.consumedX, 0f)
            }

            // Drag released: fire the caller's action if armed, then spring the pull back.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.pullPx.floatValue <= 0f) return Velocity.Zero
                if (state.pullPx.floatValue >= state.pullTriggerPx) currentOnPullFired()
                animate(state.pullPx.floatValue, 0f) { value, _ -> state.pullPx.floatValue = value }
                // Swallow the fling — there's nothing left to scroll to at the box's end.
                return available
            }
        }
    }

    // The content-width floor for widthIn below: the reachable offset plus one viewport, so
    // horizontalScroll's maxValue (= contentWidth - viewport) can reach that offset. Recomputed in
    // composition — reads minReachablePx() and viewportSize as snapshot state — so the floor tracks
    // both the pinned offset and viewport-size changes. 0 (no floor) when nothing must be reachable.
    // Suppressed entirely while followEnd owns the scroll: the end regime chases maxValue, and a floor
    // that inflated maxValue past the real content would let the reveal glide into a phantom void.
    val minContentWidth = with(LocalDensity.current) {
        if (followEnd) 0.dp else (minReachablePx()?.let { it + state.scroll.viewportSize } ?: 0).toDp()
    }

    Box(modifier.clipToBounds().nestedScroll(connection)) {
        Row(
            modifier = Modifier
                // Follow the pull so the content slides aside, opening the gap the caller's
                // trailing affordance fills.
                .offset { IntOffset(-state.pullPx.floatValue.roundToInt(), 0) }
                .then(contentModifier)
                .horizontalScroll(state.scroll)
                // Floor the content width so a declared minReachablePx offset stays scroll-reachable
                // even when the real content would fit the viewport. Applied INSIDE horizontalScroll
                // (where the width constraint is infinite), so it only ever RAISES the min — content
                // already wider than the floor is measured unchanged, adding no trailing void.
                .widthIn(min = minContentWidth),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
        overlay()
    }
}

/**
 * Repeatedly glides toward whatever [target] currently evaluates to (null means "nothing to chase
 * right now"), re-deriving it fresh each iteration rather than animating once toward a value captured
 * before the glide started — so a moving target (a growing max, a newly-declared anchor) is chased
 * rather than over/undershot. [target] is read inside `snapshotFlow`, so it must be a plain synchronous read
 * of snapshot state, not a suspend function. Never contests a real user drag: losing the scrollable
 * mutex to one just waits for the box to go idle before re-deriving the target on the next iteration
 * ([SlideBox]'s regime loop is what ends a chase for good, by cancelling this coroutine).
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

// ---------------------------------------------------------------------------------------------
// Pure pull arithmetic, separated from the NestedScrollConnection so it's JVM-testable
// (SlideBoxPullMathTest).

/** One nested-scroll delta's effect on the pull: the new pull distance and the finger travel consumed. */
internal data class PullDelta(val pullPx: Float, val consumedX: Float)

/**
 * A drag back toward the content (positive [availableX]) pays off an active pull before the box
 * itself scrolls: the pull shrinks at [resistance] per consumed finger px, and only the finger
 * travel the pull actually needs is consumed — the rest scrolls the content as usual.
 */
internal fun unwindPull(pullPx: Float, availableX: Float, resistance: Float): PullDelta {
    if (availableX <= 0f || pullPx <= 0f) return PullDelta(pullPx, 0f)
    val consumed = (pullPx / resistance).coerceAtMost(availableX)
    return PullDelta(pullPx - consumed * resistance, consumed)
}

/**
 * A drag past the end (negative [availableX], nothing left to scroll) grows the pull by
 * [resistance] × the finger travel, clamped to [maxPx]; the whole delta is consumed either way so
 * the overscroll never leaks to a parent scrollable.
 */
internal fun buildPull(pullPx: Float, availableX: Float, resistance: Float, maxPx: Float): PullDelta {
    if (availableX >= 0f) return PullDelta(pullPx, 0f)
    return PullDelta((pullPx - availableX * resistance).coerceAtMost(maxPx), availableX)
}

/** Whether this delta is the arming moment: [before] short of [triggerPx], [after] at or past it. */
internal fun crossesArmThreshold(before: Float, after: Float, triggerPx: Float): Boolean =
    before < triggerPx && after >= triggerPx
