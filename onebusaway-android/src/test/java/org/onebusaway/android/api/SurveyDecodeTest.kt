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
package org.onebusaway.android.api

import org.onebusaway.android.api.contract.StudyResponse
import org.onebusaway.android.api.contract.SubmitSurveyResponse

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the surveys client decode path. Surveys come from the region's sidecar host as bare JSON
 * (no [ObaEnvelope]), so [StudyResponse]/[SubmitSurveyResponse] decode directly. Asserts the
 * snake_case wire fields map onto the model, the `required`/`survey_response`/`update_path` aliases
 * resolve, and the transient answer holders default to null (not part of the wire).
 */
class SurveyDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesStudyResponse() {
        val body = """
            {
              "region": { "id": 1, "name": "Puget Sound" },
              "surveys": [
                {
                  "id": 42,
                  "name": "Rider survey",
                  "created_at": "2026-01-02",
                  "show_on_map": true,
                  "show_on_stops": false,
                  "allows_multiple_responses": false,
                  "always_visible": true,
                  "visible_stop_list": ["1_75403"],
                  "visible_route_list": ["1_100"],
                  "study": { "id": 7, "name": "Equity study", "description": "desc" },
                  "questions": [
                    {
                      "id": 100, "position": 1, "required": true,
                      "content": { "type": "radio", "label_text": "How often?", "options": ["A", "B"] }
                    },
                    {
                      "id": 101, "position": 2, "required": false,
                      "content": {
                        "type": "external_survey", "url": "https://example.org/s",
                        "embedded_data_fields": ["user_id", "region_id"],
                        "survey_provider": "external"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StudyResponse>(body)

        assertEquals(1, response.surveys.size)
        val survey = response.surveys[0]
        assertEquals(42, survey.id)
        assertEquals(7, survey.study?.id)
        assertEquals(true, survey.show_on_map)
        assertEquals(false, survey.show_on_stops)
        assertEquals(true, survey.always_visible)
        assertEquals(listOf("1_75403"), survey.visible_stop_list)
        assertEquals(listOf("1_100"), survey.visible_route_list)

        assertEquals(2, survey.questions.size)
        val q0 = survey.questions[0]
        assertEquals(100, q0.id)
        assertTrue(q0.isRequired) // mapped from "required"
        assertEquals("radio", q0.content.type)
        assertEquals(listOf("A", "B"), q0.content.options)
        // Transient answer holders are not part of the wire.
        assertNull(q0.questionAnswer)
        assertNull(q0.multipleAnswer)

        val q1 = survey.questions[1]
        assertEquals("external_survey", q1.content.type)
        assertEquals(arrayListOf("user_id", "region_id"), q1.content.embedded_data_fields)
        assertEquals("external", q1.content.survey_provider)
    }

    @Test
    fun decodesLiveServerShape() {
        // Shaped exactly like the live sidecar payload (Puget Sound /api/v1/regions/1/surveys.json):
        // a stops-only external_survey, and surveys carry start_date/end_date/updated_at fields the
        // model does NOT declare — they must be ignored, not rejected.
        val body = """
            {
              "surveys": [
                {
                  "id": 7,
                  "start_date": "2026-06-24T00:00:00.000Z",
                  "end_date": "2026-06-30T23:59:00.000Z",
                  "created_at": "2026-06-12T23:36:45.587Z",
                  "updated_at": "2026-06-16T23:31:49.167Z",
                  "name": "World Cup Survey - 6/24",
                  "show_on_map": false,
                  "show_on_stops": true,
                  "always_visible": true,
                  "allows_multiple_responses": false,
                  "visible_stop_list": null,
                  "visible_route_list": null,
                  "study": { "id": 4, "name": "World Cup", "description": "Qualtrics study from Sound Transit" },
                  "questions": [
                    {
                      "id": 11, "position": 1, "required": false,
                      "content": {
                        "type": "external_survey",
                        "label_text": "Riding Transit During the World Cup?",
                        "url": "https://soundtransit.sjc1.qualtrics.com/jfe/form/SV_e5kB9DCbQ42BE0e?source=OBA",
                        "embedded_data_fields": [],
                        "survey_provider": "qualtrics"
                      }
                    }
                  ]
                }
              ],
              "region": { "id": 1, "name": "Puget Sound" }
            }
        """.trimIndent()

        val response = json.decodeFromString<StudyResponse>(body)

        val survey = response.surveys.single()
        assertEquals(7, survey.id)
        assertEquals(false, survey.show_on_map) // why this survey doesn't show on the map path
        assertEquals(true, survey.show_on_stops)
        assertEquals("World Cup", survey.study?.name)
        val q = survey.questions.single()
        assertEquals("external_survey", q.content.type)
        assertEquals("qualtrics", q.content.survey_provider)
        assertEquals(arrayListOf<String>(), q.content.embedded_data_fields)
    }

    @Test
    fun decodesSubmitSurveyResponse() {
        val body = """
            {
              "survey_response": {
                "id": "abc123",
                "update_path": "/api/v1/survey_responses/abc123",
                "user_identifier": "uuid-1"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<SubmitSurveyResponse>(body)

        assertEquals("abc123", response.surveyResponse?.id)
        assertEquals("/api/v1/survey_responses/abc123", response.surveyResponse?.updatePath)
        assertEquals("uuid-1", response.surveyResponse?.userIdentifier)
    }
}
