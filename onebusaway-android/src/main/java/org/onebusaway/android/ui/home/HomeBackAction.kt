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
package org.onebusaway.android.ui.home

/**
 * What one press of Back does on the HOME map, decided by [homeBackAction]. [None] means HOME has no
 * claim on the press and it passes to the system, which is what lets a Back from the bare map leave
 * the app, and what lets predictive back preview that exit.
 */
enum class HomeBackAction {
    /** Pop the map off the page that opened it (an arrivals board, route info, trip details). */
    RETURN_TO_SOURCE,

    /** Abandon an in-progress choose-on-map endpoint pick, staying in directions. */
    CANCEL_ENDPOINT_PICK,

    /** Walk one rung back inside directions — out of a drilled-into leg, or out of directions itself. */
    NAVIGATE_BACK_IN_DIRECTIONS,

    /** Collapse a full-height arrivals sheet to its peek. */
    COLLAPSE_SHEET,

    /** Undo the most recent semantic map action: restore the preceding focus and viewport. */
    UNDO_MAP_ACTION,

    /** Nothing on HOME claims the press. */
    NONE
}

/**
 * HOME's single Back ladder. The screen composes exactly one `BackHandler`, enabled iff this is not
 * [HomeBackAction.NONE], so the "is Back ours?" question and the "what does it do?" question are one
 * value and cannot drift apart.
 *
 * Rungs, top down:
 *
 * 0. **A map pushed above a page returns to that page** (#2310). The board, route info or trip view
 *    that opened this map is still on the navigation back stack beneath it, and the rider's Back means
 *    "back to where I was", not "undo what the map did while I looked" — every local rung yields.
 * 1. **Directions owns Back outright** while it has the focus. A choose-on-map pick in progress is
 *    abandoned first; otherwise the ViewModel's own ladder steps out of a drilled-into leg, stages the
 *    leave-the-trip confirmation, or exits (#2075, #2140). The arrivals sheet never shows in
 *    directions, and undo history is deliberately *not* consulted here — the directions ladder
 *    already walks it for a sub-focus, and reaching past it would let Back jump straight out of a
 *    focused leg.
 * 2. **An expanded arrivals sheet collapses first**, whatever it holds. This is what makes Back work
 *    for the nearby drawer (#2107), which shows with *no* focus and so has no undo history behind it:
 *    without it, Back from a full-height nearby list would leave the app instead of collapsing it.
 * 3. **Otherwise Back is undo**, when there is any. At peek the sheet consumes nothing on its own: a
 *    focused stop is a focus to step out of, and undo is exactly that step, while the nearby list is
 *    ambient — the thing that shows when nothing is focused — so with no history behind it the press
 *    reaches the system rather than stranding the rider on a screen they can't leave.
 *
 * Previously two `BackHandler`s, one per rung group, whose relative priority rode on composition
 * order (the later-composed one won), each wrapped in a third helper that put the source-page return
 * ahead of it. One function has no order to get wrong, and a new rung is a new line here rather than
 * a new handler whose place in the composition has to be reasoned about.
 */
internal fun homeBackAction(
    returnsToSource: Boolean,
    directionsActive: Boolean,
    pickingEndpoint: Boolean,
    sheet: ArrivalsSheetState,
    canUndoMapAction: Boolean
): HomeBackAction = when {
    returnsToSource -> HomeBackAction.RETURN_TO_SOURCE
    directionsActive && pickingEndpoint -> HomeBackAction.CANCEL_ENDPOINT_PICK
    directionsActive -> HomeBackAction.NAVIGATE_BACK_IN_DIRECTIONS
    sheet == ArrivalsSheetState.Expanded -> HomeBackAction.COLLAPSE_SHEET
    canUndoMapAction -> HomeBackAction.UNDO_MAP_ACTION
    else -> HomeBackAction.NONE
}
