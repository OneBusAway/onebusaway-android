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
package org.onebusaway.android.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * What the host can do on the scripted tour's behalf — one lambda per [TutorialAction], supplied by
 * the screen that owns the view models each one drives.
 *
 * A bundle of lambdas rather than an interface the host implements, matching how the rest of `HomeScreen`
 * hands capabilities to its feature composables, and so that the director itself stays free of every
 * view model in the app: it knows the *script*, not the wiring.
 *
 * They suspend because some of them are a *sequence* — unwinding a focus and then re-aiming the camera
 * has to happen in that order, and the unwind travels through a map directive the host consumes a frame
 * or two later. The director already runs them in a coroutine, so this costs nothing.
 */
@Stable
data class ScriptedTutorialActions(
    val focusDemoStop: suspend () -> Unit,
    val showDemoRoute: suspend () -> Unit,
    val selectDemoTrip: suspend () -> Unit,
    val setDrawerOpen: suspend (Boolean) -> Unit,
    val resetMap: suspend () -> Unit,
    val showRentals: suspend () -> Unit,
    val planDemoTrip: suspend () -> Unit,
    val showOtherItinerary: suspend () -> Unit
)

/**
 * The index of the action that establishes the app state step [index] describes — the last action at or
 * before it — or null when nothing has run yet.
 *
 * This is what makes stepping *backwards* through a tour that drives the app forwards work. A caption is
 * about whatever the most recent action put on screen, not necessarily about its own step: "live
 * arrivals" and "what the colours mean" both describe the stop that an earlier step focused. So
 * returning to a step means re-establishing that step's governing action, which for most back presses is
 * the one already in effect — and then nothing needs to happen at all.
 *
 * Pure, so the tour's navigation is JVM-unit-testable without composing anything.
 */
internal fun governingActionIndex(steps: List<TutorialStep>, index: Int): Int? = (index.coerceAtMost(steps.size - 1) downTo 0)
    .firstOrNull { steps[it].action != null }

/**
 * Runs the scripted tour's side of the bargain: when a step of [ScriptedTutorial] opens, take the app to
 * the place that step is about (#2164).
 *
 * The user only ever presses **Next**; the app moves itself. That is what lets a caption promise what
 * the screen shows — the alternative, waiting for the user to find the right control, is how the old
 * tutorial ended up narrating whatever the user happened to tap instead.
 *
 * Inert unless a scripted step is current, so it costs nothing while another tutorial (or none) is
 * running.
 */
@Composable
fun ScriptedTutorialDirector(state: TutorialState, actions: ScriptedTutorialActions) {
    val current by rememberUpdatedState(actions)
    val step = state.current
    val stepIndex = state.index
    val steps = state.steps
    // Which action is currently in effect. Re-running one costs a camera flight or a drawer animation,
    // so a step whose governing action is already the applied one — every step without an action of its
    // own, and most back presses — leaves the app alone.
    var appliedActionIndex by remember(steps) { mutableStateOf<Int?>(null) }

    LaunchedEffect(step?.id) {
        step ?: return@LaunchedEffect
        val governing = governingActionIndex(steps, stepIndex) ?: return@LaunchedEffect
        if (governing == appliedActionIndex) return@LaunchedEffect
        val action = steps[governing].action ?: return@LaunchedEffect
        appliedActionIndex = governing
        // The overlay opens each step with a ~300ms shrink-and-spring transition. Letting that start
        // first means the app's own change — a drawer sliding out, the camera flying to a stop — plays
        // *under* a spotlight that is already moving, instead of both competing on the first frame.
        delay(ACTION_LEAD_IN_MILLIS)
        when (action) {
            TutorialAction.FOCUS_DEMO_STOP -> current.focusDemoStop()
            TutorialAction.SHOW_DEMO_ROUTE -> current.showDemoRoute()
            TutorialAction.SELECT_DEMO_TRIP -> current.selectDemoTrip()
            TutorialAction.OPEN_DRAWER -> current.setDrawerOpen(true)
            TutorialAction.RESET_MAP -> {
                current.setDrawerOpen(false)
                current.resetMap()
            }
            TutorialAction.SHOW_RENTALS -> current.showRentals()
            TutorialAction.PLAN_DEMO_TRIP -> current.planDemoTrip()
            TutorialAction.SHOW_OTHER_ITINERARY -> current.showOtherItinerary()
        }
    }
}

/**
 * How long a step waits before performing its action, so the spotlight's own step transition leads
 * rather than races it. Shorter than the overlay's close+open pair (see SpotlightTutorial) so the app has
 * begun moving by the time the new cutout finishes springing open.
 */
private const val ACTION_LEAD_IN_MILLIS = 220L
