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
package org.onebusaway.android.models

/**
 * A survey ("study") from the sidecar studies API, decoupled from the wire DTO (io.client adapts the
 * wire `StudyResponse` into these). The UI's in-progress answers live on [SurveyQuestion].
 */
data class Survey(
    val study: SurveyStudy?,
    val name: String?,
    val questions: List<SurveyQuestion>,
    val id: Int,
    val showOnMap: Boolean?,
    val showOnStops: Boolean?,
    val allowsMultipleResponses: Boolean?,
    val alwaysVisible: Boolean?,
    val visibleStopList: List<String>?,
    val visibleRouteList: List<String>?,
)

/** The study a [Survey] belongs to. */
data class SurveyStudy(
    val name: String?,
    val description: String?,
    val id: Int,
)

/** One survey question; holds the UI's in-progress answer ([answer] / [multipleAnswer]) before submit. */
class SurveyQuestion(
    val id: Int,
    val position: Int?,
    val isRequired: Boolean,
    val content: SurveyContent,
) {
    /** Free-text / radio answer captured by the UI before submit. */
    var answer: String? = null

    /** Checkbox answers captured by the UI before submit. */
    var multipleAnswer: List<String>? = null
}

/** A question's presentation content (label, options, external-survey url, type). */
data class SurveyContent(
    val labelText: String?,
    val options: List<String>?,
    val url: String?,
    val embeddedDataFields: ArrayList<String>?,
    val surveyProvider: String?,
    val type: String?,
)

/** The submit ack: the response id used to update follow-up answers. */
data class SurveySubmitResult(val id: String?)
