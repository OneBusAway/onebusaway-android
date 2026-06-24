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
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * The arrivals-panel onboarding sequence — the Compose successor to the legacy ShowcaseView
 * "arrival header" tutorial chain (which spotlighted XML views removed in the Compose migration).
 * Spotlights, in order, the ETA pill, the slide-up chevron, and the favorite star of the focused
 * stop's peek (see the [Modifier.tutorialAnchor] call sites in `ArrivalsPanel`). Shown once the first
 * time a stop's arrivals load, gated by [pendingSteps]; "show tutorials again" re-arms it by clearing
 * the [resetKeys] (see `TutorialPrefs.resetAllTutorials`).
 *
 * Each step's [TutorialStep.id] is both its spotlight anchor key and its persisted "already shown"
 * preference key. These are fresh `.tutorial_compose_arrival_*` keys (not the legacy ShowcaseView
 * chain's): the old chain was removed in the Compose migration, so the restored series should show in
 * full for everyone rather than be silently truncated by a user's stale legacy shown-flags.
 */
object ArrivalTutorial {

    /** ETA pill — "how long until your bus arrives" + the deviation-color legend. */
    const val KEY_ETA = ".tutorial_compose_arrival_eta"

    /** Slide-up chevron — pull the panel up for the full arrivals list. */
    const val KEY_PANEL = ".tutorial_compose_arrival_panel"

    /** Favorite star — pin a route to the top of the panel. */
    const val KEY_STAR = ".tutorial_compose_arrival_star"

    /** Top-bar overflow (⋮) — find recently viewed stops and routes. */
    const val KEY_MORE_MENU = ".tutorial_compose_more_menu"

    /** The sequence, in display order. */
    @JvmField
    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = KEY_ETA,
            title = R.string.tutorial_arrival_header_arrival_info_title,
            body = R.string.tutorial_arrival_header_arrival_info_text,
        ),
        TutorialStep(
            id = KEY_PANEL,
            title = R.string.tutorial_arrival_header_sliding_panel_title,
            body = R.string.tutorial_arrival_header_sliding_panel_text,
        ),
        TutorialStep(
            id = KEY_STAR,
            title = R.string.tutorial_arrival_header_star_route_title,
            body = R.string.tutorial_arrival_header_star_route_text,
        ),
        TutorialStep(
            id = KEY_MORE_MENU,
            title = R.string.tutorial_recent_stops_routes_title,
            body = R.string.tutorial_recent_stops_routes_text,
            bodyIcon = R.drawable.ic_navigation_more_vert,
        ),
    )

    /**
     * The steps still owed to the user: empty when tutorials are turned off, otherwise the steps not yet
     * marked shown. Pure (only reads [prefs]) so the gating is JVM-unit-testable.
     */
    @JvmStatic
    fun pendingSteps(prefs: PreferencesRepository): List<TutorialStep> {
        if (!prefs.getBoolean(R.string.preference_key_show_tutorial_screens, true)) return emptyList()
        return steps.filter { !prefs.getBoolean(it.id, false) }
    }

    /** Records [shown] steps so they don't re-appear (mirrors the legacy `doNotShowTutorial`-on-show). */
    @JvmStatic
    fun markShown(prefs: PreferencesRepository, shown: List<TutorialStep>) {
        shown.forEach { prefs.setBoolean(it.id, true) }
    }

    /** The preference keys "show tutorials again" clears to re-arm the sequence. */
    @JvmStatic
    fun resetKeys(): List<String> = steps.map { it.id }
}
