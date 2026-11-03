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
package org.onebusaway.android.ui.survey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.R
import org.onebusaway.android.models.Survey
import org.onebusaway.android.models.SurveyContent
import org.onebusaway.android.models.SurveyQuestion
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.survey.utils.SurveyUtils

/** The external survey's hero card titles its open button from the question's `button_label`. */
@RunWith(AndroidJUnit4::class)
class SurveyButtonLabelRenderTest {

    @get:Rule
    val compose = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsTheChosenTitleAndOpensTheSurvey() {
        var opened = 0
        showExternalSurvey(buttonLabel = "learn_more", onOpen = { opened++ })

        compose.onNodeWithText(context.getString(R.string.survey_button_learn_more)).assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun keepsTheDefaultTitleWithoutAButtonLabel() {
        showExternalSurvey(buttonLabel = null)

        compose.onNodeWithText(context.getString(R.string.go)).assertIsDisplayed()
    }

    private fun showExternalSurvey(buttonLabel: String?, onOpen: () -> Unit = {}) {
        val question = SurveyQuestion(
            id = 1,
            position = 1,
            isRequired = false,
            content = SurveyContent(
                labelText = "Riding transit this week?",
                options = null,
                url = "https://example.org/s",
                embeddedDataFields = null,
                surveyProvider = "external",
                type = SurveyUtils.EXTERNAL_SURVEY,
                buttonLabel = buttonLabel
            )
        )
        val survey = Survey(
            study = null,
            name = "Rider survey",
            id = 1,
            showOnMap = true,
            showOnStops = false,
            allowsMultipleResponses = false,
            alwaysVisible = true,
            visibleStopList = null,
            visibleRouteList = null,
            questions = listOf(question)
        )
        val state = SurveyUiState(
            survey = survey,
            heroQuestion = question,
            heroMode = SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION
        )
        val callbacks = SurveyCallbacks(
            onTextOrRadio = { _, _ -> },
            onToggleCheckbox = { _, _, _ -> },
            onSubmitHero = {},
            onOpenExternalWithoutHero = onOpen,
            onSubmitSheet = {},
            onRequestDismiss = {},
            onSkip = {},
            onRemindLater = {},
            onCancelDismiss = {}
        )
        compose.setContent { SurveyOverlay(state, callbacks) }
    }
}
