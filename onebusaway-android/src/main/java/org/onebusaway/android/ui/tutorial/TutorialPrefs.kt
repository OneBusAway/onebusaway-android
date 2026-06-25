/*
 * Copyright (C) 2015-2017 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.tutorial

import android.content.Context
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint

/**
 * Tutorial preference-flag constants and reset, kept after the ShowcaseView-based tutorials were
 * replaced by Compose onboarding. The constants are still referenced by the launch flow
 * ([TUTORIAL_WELCOME]) and the help dialog ([TUTORIAL_OPT_OUT_DIALOG]); [resetAllTutorials] re-arms
 * onboarding from Settings.
 */
object TutorialPrefs {

    const val TUTORIAL_WELCOME = ".tutorial_welcome"

    const val TUTORIAL_OPT_OUT_DIALOG = ".tutorial_opt_out_dialog"

    const val TUTORIAL_ARRIVAL_SORT = ".tutorial_arrival_sort"

    const val TUTORIAL_RECENT_STOPS_ROUTES = ".tutorial_recent_stops_routes"

    const val TUTORIAL_STARRED_STOPS_SORT = ".tutorial_starred_stops_sort"

    const val TUTORIAL_STARRED_STOPS_SHORTCUT = ".tutorial_starred stops_shortcut"

    const val TUTORIAL_SEND_FEEDBACK_OPEN311_CATEGORIES = ".tutorial_send_feedback_open311_categories"

    /** Resets all tutorials so they are shown to the user again. */
    fun resetAllTutorials(context: Context) {
        val prefs = PreferencesEntryPoint.get(context)
        prefs.setBoolean(R.string.preference_key_show_tutorial_screens, true)

        prefs.setBoolean(TUTORIAL_WELCOME, false)
        prefs.setBoolean(TUTORIAL_ARRIVAL_SORT, false)
        prefs.setBoolean(TUTORIAL_RECENT_STOPS_ROUTES, false)
        prefs.setBoolean(TUTORIAL_STARRED_STOPS_SORT, false)
        prefs.setBoolean(TUTORIAL_STARRED_STOPS_SHORTCUT, false)
        prefs.setBoolean(TUTORIAL_SEND_FEEDBACK_OPEN311_CATEGORIES, false)

        // Re-arm the Compose arrivals-panel onboarding spotlight (its keys live with the sequence).
        for (key in ArrivalTutorial.resetKeys()) {
            prefs.setBoolean(key, false)
        }
    }
}
