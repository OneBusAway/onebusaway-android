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
package org.onebusaway.android.ui.survey.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.R

/**
 * The external survey's open button takes its title from the question's `button_label` key. The
 * keys are an open set: anything this build doesn't know falls back to the default title.
 */
class SurveyButtonLabelTest {

    @Test
    fun mapsEachKnownKey() {
        assertEquals(R.string.survey_button_learn_more, SurveyUtils.externalSurveyButtonLabel("learn_more"))
        assertEquals(R.string.survey_button_share_feedback, SurveyUtils.externalSurveyButtonLabel("share_feedback"))
        assertEquals(R.string.survey_button_sign_up, SurveyUtils.externalSurveyButtonLabel("sign_up"))
    }

    @Test
    fun openSurveyIsTheDefaultTitle() {
        assertEquals(R.string.go, SurveyUtils.externalSurveyButtonLabel("open_survey"))
    }

    @Test
    fun missingKeyFallsBackToTheDefaultTitle() {
        assertEquals(R.string.go, SurveyUtils.externalSurveyButtonLabel(null))
    }

    @Test
    fun unknownKeyFallsBackToTheDefaultTitle() {
        assertEquals(R.string.go, SurveyUtils.externalSurveyButtonLabel("rsvp"))
        assertEquals(R.string.go, SurveyUtils.externalSurveyButtonLabel(""))
        assertEquals(R.string.go, SurveyUtils.externalSurveyButtonLabel("LEARN_MORE"))
    }
}
