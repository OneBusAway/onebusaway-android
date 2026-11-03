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
package org.onebusaway.android.api.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Surveys ("studies") response models for [SurveyWebService]. Unlike the OBA `where` API these come
 * from the region's sidecar host and are NOT wrapped in an [ObaEnvelope] — the study list and the
 * submit ack are the top-level JSON objects, so they decode directly.
 *
 * Property names deliberately mirror the wire's snake_case so they map without [SerialName] and the
 * survey UI/DB/util consumers keep their existing field accessors; the nested `Surveys`/`Questions`/
 * `Content`/`Region` shape is preserved for the same reason.
 */
@Serializable
data class StudyResponse(
    val surveys: List<Surveys> = emptyList(),
    val region: Region? = null
) {
    @Serializable
    data class Surveys(
        val study: Study? = null,
        val name: String? = null,
        val questions: List<Questions> = emptyList(),
        val created_at: String? = null,
        val id: Int = 0,
        val show_on_map: Boolean? = null,
        val show_on_stops: Boolean? = null,
        val allows_multiple_responses: Boolean? = null,
        val always_visible: Boolean? = null,
        val visible_stop_list: List<String>? = null,
        val visible_route_list: List<String>? = null
    ) {
        @Serializable
        data class Study(
            val name: String? = null,
            val description: String? = null,
            val id: Int = 0
        )

        @Serializable
        data class Questions(
            val id: Int = 0,
            val position: Int? = null,
            @SerialName("required") val isRequired: Boolean = false,
            val content: Content = Content()
        ) {
            /** Answer held by the survey UI before submit; not part of the wire response. */
            @Transient
            var questionAnswer: String? = null

            /** Checkbox answers held by the survey UI before submit; not part of the wire response. */
            @Transient
            var multipleAnswer: List<String>? = null

            @Serializable
            data class Content(
                val label_text: String? = null,
                val options: List<String>? = null,
                val url: String? = null,
                val embedded_data_fields: ArrayList<String>? = null,
                val survey_provider: String? = null,
                val type: String? = null,
                /** `external_survey` only: which canned title its open button shows; an open set of keys. */
                @Serializable(with = OptionalKeySerializer::class)
                val button_label: String? = null
            )
        }
    }

    @Serializable
    data class Region(
        val name: String? = null,
        val id: Int = 0
    )
}

/**
 * Decodes an optional key that only means something as a JSON string; any other shape decodes as
 * null. Keeps a malformed `button_label` from failing the whole study list, so the rider still gets
 * the survey with its default button title.
 */
private object OptionalKeySerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OptionalKey", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? = ((decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive)?.takeIf { it.isString }?.content

    override fun serialize(encoder: Encoder, value: String?) = encoder.encodeString(value.orEmpty())
}

/** The ack returned after POSTing survey answers; carries the response id used to update follow-ups. */
@Serializable
data class SubmitSurveyResponse(
    @SerialName("survey_response") val surveyResponse: SurveyResponse? = null
) {
    @Serializable
    data class SurveyResponse(
        val id: String? = null,
        @SerialName("update_path") val updatePath: String? = null,
        @SerialName("user_identifier") val userIdentifier: String? = null
    )
}
