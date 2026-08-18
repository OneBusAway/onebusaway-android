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
 * replaced by Compose onboarding: [TUTORIAL_WELCOME] gates the launch flow's "show the tutorial?"
 * prompt and [TUTORIAL_OPT_OUT_DIALOG] the help dialog. [resetAllTutorials] re-arms onboarding from
 * Settings.
 *
 * Five further flags used to live here — the arrival sort, Recent stops/routes, the two starred-stops
 * hints and the Open311 categories. Each was declared and dutifully reset, and none had been *read*
 * since the ShowcaseView tutorials they belonged to were deleted, so resetting them re-armed nothing.
 * A flag nobody asks about is not a feature that is off; it is a line that makes the reset below look
 * more thorough than it is.
 */
object TutorialPrefs {

    const val TUTORIAL_WELCOME = ".tutorial_welcome"

    const val TUTORIAL_OPT_OUT_DIALOG = ".tutorial_opt_out_dialog"

    /** Resets all tutorials so they are shown to the user again. */
    fun resetAllTutorials(context: Context) {
        val prefs = PreferencesEntryPoint.get(context)
        prefs.setBoolean(R.string.preference_key_show_tutorial_screens, true)

        prefs.setBoolean(TUTORIAL_WELCOME, false)

        // Re-arm the Compose arrivals-panel onboarding spotlight (its keys live with the sequence).
        for (key in ArrivalTutorial.resetKeys()) {
            prefs.setBoolean(key, false)
        }
    }
}
