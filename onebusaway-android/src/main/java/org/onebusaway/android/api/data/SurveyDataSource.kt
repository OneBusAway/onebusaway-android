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
package org.onebusaway.android.api.data

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.api.contract.StudyResponse
import org.onebusaway.android.api.contract.SurveyWebService
import org.onebusaway.android.models.Survey
import org.onebusaway.android.models.SurveyContent
import org.onebusaway.android.models.SurveyQuestion
import org.onebusaway.android.models.SurveyStudy
import org.onebusaway.android.models.SurveySubmitResult

/**
 * Fetches/submits surveys via [SurveyWebService], adapting the wire [StudyResponse] /
 * [SubmitSurveyResponse] to the [Survey] / [SurveySubmitResult] model so the survey feature never
 * sees the DTOs. The caller passes the fully-resolved sidecar URL (built from the region) + the
 * JSON-encoded answer body, exactly as the web service expects. A transport/decode failure maps to
 * [Result.failure] (consistent with the other io.client data sources).
 */
interface SurveyDataSource {

    suspend fun studies(url: String, userId: String?): Result<List<Survey>>

    suspend fun submit(
        url: String,
        userIdentifier: String?,
        surveyId: Int,
        stopIdentifier: String?,
        stopLatitude: Double,
        stopLongitude: Double,
        responses: String,
    ): Result<SurveySubmitResult>
}

class DefaultSurveyDataSource @Inject constructor(
    private val service: SurveyWebService,
) : SurveyDataSource {

    override suspend fun studies(url: String, userId: String?): Result<List<Survey>> = runCatching {
        service.getStudy(url, userId).toSurveys()
    }.onFailure { Log.e(TAG, "studies failed", it) }

    override suspend fun submit(
        url: String,
        userIdentifier: String?,
        surveyId: Int,
        stopIdentifier: String?,
        stopLatitude: Double,
        stopLongitude: Double,
        responses: String,
    ): Result<SurveySubmitResult> = runCatching {
        service.submitSurvey(
            url = url,
            userIdentifier = userIdentifier,
            surveyId = surveyId,
            stopIdentifier = stopIdentifier,
            stopLatitude = stopLatitude,
            stopLongitude = stopLongitude,
            responses = responses,
        ).let { SurveySubmitResult(it.surveyResponse?.id) }
    }.onFailure { Log.e(TAG, "submit failed", it) }

    private companion object {
        const val TAG = "SurveyDataSource"
    }
}

/** Maps the wire study list to the [Survey] model. */
internal fun StudyResponse.toSurveys(): List<Survey> = surveys.map { s ->
    Survey(
        study = s.study?.let { SurveyStudy(it.name, it.description, it.id) },
        name = s.name,
        id = s.id,
        showOnMap = s.show_on_map,
        showOnStops = s.show_on_stops,
        allowsMultipleResponses = s.allows_multiple_responses,
        alwaysVisible = s.always_visible,
        visibleStopList = s.visible_stop_list,
        visibleRouteList = s.visible_route_list,
        questions = s.questions.map { q ->
            SurveyQuestion(
                id = q.id,
                position = q.position,
                isRequired = q.isRequired,
                content = SurveyContent(
                    labelText = q.content.label_text,
                    options = q.content.options,
                    url = q.content.url,
                    embeddedDataFields = q.content.embedded_data_fields,
                    surveyProvider = q.content.survey_provider,
                    type = q.content.type,
                ),
            )
        },
    )
}
