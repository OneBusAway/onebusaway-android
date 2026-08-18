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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * The arrivals-panel onboarding sequence — the Compose successor to the legacy ShowcaseView
 * "arrival header" tutorial chain (which spotlighted XML views removed in the Compose migration).
 * Spotlights, in order, the ETA pill, the slide-up chevron, and the hamburger menu — which now holds
 * "Recent stops/routes" (see the [Modifier.tutorialAnchor] call sites in `ArrivalsPanel` / the sheet
 * host). Shown once the first
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

    /**
     * Hamburger menu (☰) — "Recent stops/routes" moved out of the retired overflow into the drawer, so
     * this now spotlights the nav icon that opens it. The persisted pref value stays the legacy
     * `.tutorial_compose_more_menu` so users who already saw the step aren't re-shown it.
     */
    const val KEY_MORE_MENU = ".tutorial_compose_more_menu"

    /** The sequence, in display order. */
    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = KEY_ETA,
            title = R.string.tutorial_arrival_header_arrival_info_title,
            body = R.string.tutorial_arrival_header_arrival_info_text
        ),
        TutorialStep(
            id = KEY_PANEL,
            title = R.string.tutorial_arrival_header_sliding_panel_title,
            body = R.string.tutorial_arrival_header_sliding_panel_text
        ),
        TutorialStep(
            id = KEY_MORE_MENU,
            title = R.string.tutorial_recent_stops_routes_title,
            body = R.string.tutorial_recent_stops_routes_text,
            bodyIcon = R.drawable.history_24
        )
    )

    /**
     * The steps still owed to the user: empty when tutorials are turned off, otherwise the steps not yet
     * marked shown. Pure (only reads [prefs]) so the gating is JVM-unit-testable.
     */
    fun pendingSteps(prefs: PreferencesRepository): List<TutorialStep> {
        if (!prefs.getBoolean(R.string.preference_key_show_tutorial_screens, true)) return emptyList()
        return steps.filter { !prefs.getBoolean(it.id, false) }
    }

    /** Records [shown] steps so they don't re-appear (mirrors the legacy `doNotShowTutorial`-on-show). */
    fun markShown(prefs: PreferencesRepository, shown: List<TutorialStep>) {
        shown.forEach { prefs.setBoolean(it.id, true) }
    }

    /**
     * True when [key] is one of this sequence's spotlight anchors — which the scripted tour (#2164)
     * reuses for the arrivals controls it teaches, so a step is matched by *anchor* rather than by its
     * own id. Pure, so the gating stays JVM-unit-testable.
     */
    fun isSpotlightAnchor(key: String): Boolean = steps.any { it.id == key }

    /** The preference keys "show tutorials again" clears to re-arm the sequence. */
    fun resetKeys(): List<String> = steps.map { it.id }
}

/**
 * Records each arrivals spotlight as shown the moment it is actually on screen, for whichever tutorial
 * is running. Renders nothing; host it once, alongside the [TutorialState] it watches.
 *
 * A step is recorded *as it is displayed* rather than the whole sequence being marked when one starts,
 * because either tutorial can end early — the corner "X", or system Back, which the overlay treats the
 * same way. Marking up front meant a rider who pressed Back meaning to collapse the arrivals sheet
 * silently retired all three spotlights before seeing two of them, with no scrim to hint that anything
 * had been dismissed; what is left unmarked simply stays owed, and comes back at the next stop.
 *
 * Keyed on [TutorialStep.anchorId], so the scripted tour (#2164) counts too: its ETA-pill steps ring
 * [ArrivalTutorial.KEY_ETA] to teach the same control, and re-ambushing the rider with the opportunistic
 * version of a lesson they just had would be a worse answer than either tutorial acting alone. Steps
 * anchored anywhere else are ignored, so nothing here fires for the rest of the tour.
 */
@Composable
fun RecordArrivalSpotlightsShown(state: TutorialState) {
    val app = LocalContext.current.applicationContext
    val prefs = remember(app) { PreferencesEntryPoint.get(app) }
    LaunchedEffect(state, prefs) {
        snapshotFlow { state.current?.anchorId }.collect { anchor ->
            if (anchor != null && ArrivalTutorial.isSpotlightAnchor(anchor)) prefs.setBoolean(anchor, true)
        }
    }
}
