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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.onebusaway.android.R
import org.onebusaway.android.io.request.survey.ObaStudyRequest
import org.onebusaway.android.io.request.survey.model.StudyResponse
import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse
import org.onebusaway.android.io.request.survey.submit.ObaSubmitSurveyRequest
import org.onebusaway.android.io.request.survey.submit.SubmitSurveyRequestListener
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.database.survey.SurveyDbHelper
import org.onebusaway.android.ui.survey.utils.SurveyUtils

/** The bottom-sheet state for the survey's remaining (non-hero) questions. */
data class SurveySheetState(
    val title: String,
    val description: String,
    val questions: List<StudyResponse.Surveys.Questions>,
    val submitting: Boolean = false,
)

/**
 * The map survey UI state. The hero card shows the first question over the map; once answered, the
 * remaining questions (if any) appear in [sheet]. Answers are held here (Compose-observable) keyed by
 * question id, and copied into the question models on submit so the existing [SurveyUtils] JSON
 * builder + validation can be reused.
 */
data class SurveyUiState(
    val survey: StudyResponse.Surveys? = null,
    val heroQuestion: StudyResponse.Surveys.Questions? = null,
    val heroMode: Int = SurveyUtils.DEFAULT_SURVEY,
    val sharedInfo: String? = null,
    val heroSubmitting: Boolean = false,
    val showDismissDialog: Boolean = false,
    val sheet: SurveySheetState? = null,
    val textRadioAnswers: Map<Int, String> = emptyMap(),
    val checkboxAnswers: Map<Int, Set<String>> = emptyMap(),
)

/** One-shot effects the host carries out (launching the external-survey activity, showing a toast). */
sealed interface SurveyEffect {
    data class OpenExternalSurvey(val url: String, val embeddedData: ArrayList<String>?) : SurveyEffect
    data class ShowToast(val resId: Int) : SurveyEffect
}

/**
 * Drives the map survey: requesting the study, showing the hero question + remaining-questions sheet,
 * submitting answers, and persisting completion/skip/remind-later. Replaces the View-based
 * `SurveyManager` + `SurveyViewUtils` + `SurveyAdapter`; the network/JSON/DB/filtering logic is reused
 * from `ObaStudyRequest`/`ObaSubmitSurveyRequest`/`SurveyUtils`/`SurveyDbHelper`/`SurveyPreferences`.
 * Scoped to the map (the old `isVisibleOnStops = false` path).
 */
@HiltViewModel
class SurveyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SurveyUiState())
    val state: StateFlow<SurveyUiState> = _state.asStateFlow()

    // Whether a region has resolved — the survey needs one to build its study URL. Reads the shared
    // [RegionRepository.regionPresent] predicate; [SurveyFeature] uses it to re-trigger [maybeRequestSurvey]
    // once a region is present. Mirrors the manual collect-into-MutableStateFlow idiom used by the other
    // feature VMs (WeatherViewModel) rather than a SharingStarted.Eagerly stateIn, whose never-completing
    // collector leaks across JVM unit tests.
    private val _regionReady = MutableStateFlow(regionRepository.region.value != null)
    val regionReady: StateFlow<Boolean> = _regionReady.asStateFlow()

    init {
        viewModelScope.launch {
            regionRepository.regionPresent.collect { _regionReady.value = it }
        }
    }

    private val _effects = MutableSharedFlow<SurveyEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<SurveyEffect> = _effects.asSharedFlow()

    private var studyResponse: StudyResponse? = null
    private var surveyIndex: Int = -1
    private var updateSurveyPath: String? = null
    private var requested = false

    /**
     * Requests the survey once per the gating rules (a region is resolved, every Nth launch, no pending
     * reminder, donation UI not showing). Safe to call repeatedly; only the first eligible call fires
     * the request — until a region is present it no-ops without latching, so a later call (e.g. on
     * region resolve) retries.
     */
    fun maybeRequestSurvey() {
        if (requested) return
        // A region is required to build the study request URL; defer (without latching) until resolved.
        if (regionRepository.region.value == null) return
        val studiesEnabled = prefs.getBoolean(R.string.preference_key_show_available_studies, true)
        if (!studiesEnabled || !SurveyUtils.shouldShowSurveyView(context, false)) return
        requested = true
        viewModelScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { ObaStudyRequest.newRequest(context).call() }.getOrNull()
            }
            if (response != null) onStudyResponse(response)
        }
    }

    private fun onStudyResponse(response: StudyResponse) {
        studyResponse = response
        surveyIndex = SurveyUtils.getCurrentSurveyIndex(response, context, false, null)
        if (surveyIndex == -1) return
        val survey = response.surveys[surveyIndex]
        val questions = survey.questions
        if (questions.isNullOrEmpty()) return
        val hero = questions[0]
        val mode = SurveyUtils.checkExternalSurvey(questions)
        val sharedFields = when (mode) {
            SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION -> questions[0].content.embedded_data_fields
            SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION -> questions[1].content.embedded_data_fields
            else -> null
        }
        _state.update {
            it.copy(
                survey = survey,
                heroQuestion = hero,
                heroMode = mode,
                sharedInfo = buildSharedInfo(sharedFields),
            )
        }
    }

    // --- answer capture (Compose-observable) ---

    fun setTextOrRadioAnswer(questionId: Int, answer: String) {
        _state.update { it.copy(textRadioAnswers = it.textRadioAnswers + (questionId to answer)) }
    }

    fun toggleCheckbox(questionId: Int, option: String, checked: Boolean) {
        _state.update {
            val current = it.checkboxAnswers[questionId].orEmpty()
            val next = if (checked) current + option else current - option
            it.copy(checkboxAnswers = it.checkboxAnswers + (questionId to next))
        }
    }

    // --- hero question submit ---

    fun submitHero() {
        val survey = _state.value.survey ?: return
        val hero = _state.value.heroQuestion ?: return
        val body = heroAnswerBody(hero) ?: run {
            _effects.tryEmit(SurveyEffect.ShowToast(R.string.please_complete_required_questions))
            return
        }
        SurveyUtils.launchesUntilSurveyShown = Integer.MAX_VALUE
        _state.update { it.copy(heroSubmitting = true) }
        viewModelScope.launch {
            val response = submit(submitUrl(hero = true), survey.id, body)
            _state.update { it.copy(heroSubmitting = false) }
            handleCompleted(survey)
            if (_state.value.heroMode == SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION) {
                survey.questions.getOrNull(1)?.content?.url?.let { openExternal(it, externalQuestionIndex = 1) }
                dismissAll()
                return@launch
            }
            val remaining = survey.questions.drop(1)
            if (remaining.isEmpty() || response == null) {
                dismissAll()
                return@launch
            }
            updateSurveyPath = response.surveyResponse?.id
            _state.update {
                it.copy(
                    sheet = SurveySheetState(
                        title = survey.study?.name.orEmpty(),
                        description = survey.study?.description.orEmpty(),
                        questions = remaining,
                    ),
                )
            }
        }
    }

    // --- remaining-questions sheet submit ---

    fun submitSheet() {
        val survey = _state.value.survey ?: return
        val sheet = _state.value.sheet ?: return
        // Copy the Compose answers into the question models so SurveyUtils can read them.
        for (q in sheet.questions) {
            val id = q.id
            when (q.content.type) {
                SurveyUtils.CHECK_BOX_QUESTION ->
                    q.multipleAnswer = _state.value.checkboxAnswers[id]?.toList()
                SurveyUtils.TEXT_QUESTION, SurveyUtils.RADIO_BUTTON_QUESTION ->
                    q.questionAnswer = _state.value.textRadioAnswers[id]
            }
        }
        val body = SurveyUtils.getSurveyAnswersRequestBody(sheet.questions) ?: run {
            _effects.tryEmit(SurveyEffect.ShowToast(R.string.please_complete_required_questions))
            return
        }
        _state.update { it.copy(sheet = it.sheet?.copy(submitting = true)) }
        viewModelScope.launch {
            submit(submitUrl(hero = false), survey.id, body)
            dismissAll()
        }
    }

    // --- external survey (no hero) "Go" button ---

    fun openExternalWithoutHero() {
        val survey = _state.value.survey ?: return
        survey.questions.getOrNull(0)?.content?.url?.let { openExternal(it, externalQuestionIndex = 0) }
        handleCompleted(survey)
        dismissAll()
    }

    // --- dismiss / skip / remind ---

    fun requestDismiss() = _state.update { it.copy(showDismissDialog = true) }
    fun cancelDismiss() = _state.update { it.copy(showDismissDialog = false) }

    fun skipSurvey() {
        _state.value.survey?.let {
            SurveyDbHelper.markSurveyAsCompletedOrSkipped(context, it, SurveyDbHelper.SURVEY_SKIPPED)
        }
        dismissAll()
    }

    fun remindMeLater() {
        SurveyUtils.remindUserLater(context)
        dismissAll()
    }

    // --- helpers ---

    private fun handleCompleted(survey: StudyResponse.Surveys) {
        SurveyDbHelper.markSurveyAsCompletedOrSkipped(context, survey, SurveyDbHelper.SURVEY_COMPLETED)
        SurveyUtils.launchesUntilSurveyShown = Integer.MAX_VALUE
    }

    private fun dismissAll() {
        _state.value = SurveyUiState()
    }

    private fun openExternal(url: String, externalQuestionIndex: Int) {
        val survey = _state.value.survey ?: return
        val embedded = survey.questions.getOrNull(externalQuestionIndex)?.content?.embedded_data_fields
        _effects.tryEmit(SurveyEffect.OpenExternalSurvey(url, embedded))
    }

    private fun submitUrl(hero: Boolean): String {
        // A survey can only be loaded once a region is present, so region is non-null on this path.
        val base = regionRepository.region.value?.sidecarBaseUrl.orEmpty()
        var url = base + context.getString(R.string.submit_survey_api_endpoint)
        if (!hero && updateSurveyPath != null) url += updateSurveyPath
        return url
    }

    private suspend fun submit(apiUrl: String, surveyId: Int, body: JSONArray): SubmitSurveyResponse? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val request = ObaSubmitSurveyRequest.Builder(context, apiUrl)
                    .setUserIdentifier(SurveyPreferences.getUserUUID(context))
                    .setSurveyId(surveyId)
                    .setStopIdentifier(null)
                    .setStopLatitude(0.0)
                    .setStopLongitude(0.0)
                    .setResponses(body)
                    .setListener(object : SubmitSurveyRequestListener {
                        override fun onSubmitSurveyResponseReceived(response: SubmitSurveyResponse?) {
                            if (cont.isActive) cont.resumeWith(Result.success(response))
                        }

                        override fun onSubmitSurveyFail() {
                            if (cont.isActive) cont.resumeWith(Result.success(null))
                        }
                    })
                    .build()
                request.call()
            }
        }

    /** Builds the single-question JSON body for the hero question, or null if its answer is missing. */
    private fun heroAnswerBody(hero: StudyResponse.Surveys.Questions): JSONArray? {
        val answer: String = when (hero.content.type) {
            SurveyUtils.CHECK_BOX_QUESTION -> {
                val selected = _state.value.checkboxAnswers[hero.id].orEmpty().toList()
                if (selected.isEmpty()) return null
                selected.toString()
            }

            SurveyUtils.TEXT_QUESTION, SurveyUtils.RADIO_BUTTON_QUESTION -> {
                val a = _state.value.textRadioAnswers[hero.id].orEmpty()
                if (a.isEmpty()) return null
                a
            }

            else -> return null
        }
        return JSONArray().apply {
            put(
                JSONObject().apply {
                    put("question_id", hero.id)
                    put("question_type", hero.content.type)
                    put("question_label", hero.content.label_text)
                    put("answer", answer)
                }
            )
        }
    }

    private fun buildSharedInfo(fields: List<String>?): String? {
        val shared = fields.orEmpty().filter { it != SurveyUtils.USER_ID }
        if (shared.isEmpty()) return null
        return context.getString(R.string.sharing_survey_info_message) +
            shared.joinToString(", ") { it.replace("_id", "").replace("_", " ") }
    }
}
