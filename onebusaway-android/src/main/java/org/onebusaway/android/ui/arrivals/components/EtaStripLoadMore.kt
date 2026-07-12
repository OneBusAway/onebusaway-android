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
package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.onebusaway.android.ui.arrivals.LoadMoreState

/**
 * No active load-more request on this strip. Real tokens start at 1, so 0 is safely "none" (and
 * survives rememberSaveable without a nullable).
 */
internal const val NO_LOAD_REQUEST = 0

/** The stop-shared [LoadMoreState] interpreted from one strip's point of view. */
internal sealed interface LoadMoreOutcome {
    /** Our request is still in flight. */
    data object Pending : LoadMoreOutcome

    /** The shared slot no longer tracks our request — no live request, another strip re-fired, or a
     *  restored token from a dead process — terminal; tear down without animating. */
    data object Superseded : LoadMoreOutcome

    /** Our request completed; the strip settles once its layout reflects [dataVersion]. Success vs
     *  failure isn't distinguished here — both settle wherever layout puts the new end; the fresh-vs-
     *  stale outcome lives on [LoadMoreState.Finished.success] as a VM concern. */
    data class Landed(val dataVersion: Long) : LoadMoreOutcome
}

/** How the shared [state] reads for the strip holding [token] ([NO_LOAD_REQUEST] reads as
 *  [LoadMoreOutcome.Superseded] — it can never match a live token). */
internal fun loadMoreOutcome(state: LoadMoreState, token: Int): LoadMoreOutcome = when (state) {
    LoadMoreState.Idle -> LoadMoreOutcome.Superseded
    is LoadMoreState.Loading ->
        if (state.token == token) LoadMoreOutcome.Pending else LoadMoreOutcome.Superseded
    is LoadMoreState.Finished ->
        if (state.token == token) LoadMoreOutcome.Landed(state.dataVersion)
        else LoadMoreOutcome.Superseded
}

/**
 * Whether the inline tail spinner shows: from fire until the composition that carries the completing
 * data's version — so the spinner and the new pills swap in the SAME composition — or immediately on
 * supersession/teardown.
 */
internal fun spinnerVisible(outcome: LoadMoreOutcome, renderedDataVersion: Long): Boolean =
    when (outcome) {
        LoadMoreOutcome.Superseded -> false
        LoadMoreOutcome.Pending -> true
        is LoadMoreOutcome.Landed -> renderedDataVersion < outcome.dataVersion
    }

/**
 * Acknowledges, in the LAYOUT phase, the data [version] this strip's content has been MEASURED for.
 * Place BEFORE `horizontalScroll` in the chain so measuring the child runs the scroll node's measure
 * (refreshing its `maxValue`) in the same pass; [into] is then set to [version]'s current value, so
 * `into >= V` guarantees `maxValue` is consistent with data version V — the composition-vs-layout
 * bridge the reveal transaction's settle predicate waits on. [version] is read as snapshot [State]
 * *inside* the measure block, so a version-only change (identical pills, bumped revision) re-runs the
 * ack without recomposing.
 */
internal fun Modifier.acknowledgeVersion(version: State<Long>, into: MutableLongState): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        into.longValue = version.value
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * Repeatedly glides toward whatever [target] currently evaluates to (null means "nothing to chase
 * right now"), re-deriving it fresh each iteration rather than animating once toward a value captured
 * before the glide started — so a moving target (a growing max, a newly-pinned pill) is chased rather
 * than over/undershot. [target] is read inside `snapshotFlow`, so it must be a plain synchronous read
 * of snapshot state, not a suspend function. Never contests a real user drag: losing the scrollable
 * mutex to one just waits for the strip to go idle before re-deriving the target on the next iteration
 * (the caller's own nested-scroll hook is what ends the chase for good, by cancelling this coroutine).
 *
 * Clamped to `[0, maxValue]` before comparing OR animating — `animateScrollTo` clamps internally, but
 * comparing against the raw (unclamped) target would keep re-firing forever once `value` settles at
 * `maxValue` for a target that's past the reachable end (e.g. the pinned pill sitting near the end of
 * a short strip).
 */
internal suspend fun ScrollState.glideTo(target: () -> Int?) {
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
