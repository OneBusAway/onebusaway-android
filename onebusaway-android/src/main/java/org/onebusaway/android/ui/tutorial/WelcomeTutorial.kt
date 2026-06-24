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

import org.onebusaway.android.R

/**
 * The welcome onboarding sequence, shown on demand (help "Show tutorials", the what's-new opt-out's
 * "yes", or the first-run TUTORIAL_WELCOME launch extra). Replaces the legacy full-screen ShowcaseView
 * welcome: step 1 is the green intro card (no spotlight), step 2 spotlights a real stop marker on the
 * map to point the user at where to tap.
 *
 * The map-stop step has no persisted "shown" flag — the whole sequence is gated by its trigger, not by
 * [pendingSteps] like the arrivals series — so its [TutorialStep.id] is purely the spotlight anchor key
 * the map adapter reports the stop's projected screen bounds under ([KEY_MAP_STOP]).
 */
object WelcomeTutorial {

    /** The map-stop step's anchor key: the Google map adapter reports the nearest stop's screen bounds here. */
    const val KEY_MAP_STOP = "tutorial_welcome_map_stop"

    /** The intro step has no spotlight target; this id never resolves to bounds (full-screen overlay). */
    private const val KEY_INTRO = "tutorial_welcome_intro"

    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = KEY_INTRO,
            title = R.string.tutorial_welcome_title,
            body = R.string.tutorial_welcome_text,
        ),
        TutorialStep(
            id = KEY_MAP_STOP,
            title = R.string.tutorial_welcome_map_stop_title,
            body = R.string.tutorial_welcome_map_stop_text,
            // Last welcome step, but completing it focuses the stop and continues into the arrivals tour,
            // so its button stays "Next" rather than "Finish".
            continuesAfter = true,
        ),
    )
}
