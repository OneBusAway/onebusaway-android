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
package org.onebusaway.android.database.survey

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.database.survey.dao.StudiesDao
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.database.survey.dao.SurveysDao
import org.onebusaway.android.models.Survey as SurveyModel
import org.onebusaway.android.models.SurveyStudy

/**
 * JVM unit tests for the survey persistence logic — the completed/skipped upsert and the completed-id
 * projection — driven through hand fakes of the two DAOs (no Room, matching the FakePreferencesRepository
 * convention). This is the logic the modernization moved out of the untestable static SurveyDbHelper.
 */
class SurveyRepositoryTest {

    private val studies = FakeStudiesDao()
    private val surveys = FakeSurveysDao()
    private val repo = SurveyRepository(studies, surveys)

    private fun surveyModel(id: Int, study: SurveyStudy?) = SurveyModel(
        study = study,
        name = "Survey $id",
        questions = emptyList(),
        id = id,
        showOnMap = null,
        showOnStops = null,
        allowsMultipleResponses = null,
        alwaysVisible = null,
        visibleStopList = null,
        visibleRouteList = null,
    )

    @Test
    fun markCompletedOrSkipped_withoutStudy_writesNothing() = runTest {
        repo.markCompletedOrSkipped(surveyModel(1, study = null), SurveyRepository.SURVEY_COMPLETED)

        assertEquals(0, studies.rows.size)
        assertEquals(0, surveys.rows.size)
    }

    @Test
    fun markCompletedOrSkipped_insertsStudyThenSurvey() = runTest {
        val study = SurveyStudy(name = "Study", description = "Desc", id = 7)

        repo.markCompletedOrSkipped(surveyModel(3, study), SurveyRepository.SURVEY_SKIPPED)

        assertEquals(1, studies.inserts)
        assertEquals(0, studies.updates)
        assertEquals(Study(7, "Study", "Desc", true), studies.rows[7])
        assertEquals(1, surveys.rows.size)
        assertEquals(Survey(3, 7, "Survey 3", SurveyRepository.SURVEY_SKIPPED), surveys.rows[0])
    }

    @Test
    fun markCompletedOrSkipped_existingStudy_updatesRatherThanReinserts() = runTest {
        val study = SurveyStudy(name = "Study", description = "Desc", id = 7)
        repo.markCompletedOrSkipped(surveyModel(3, study), SurveyRepository.SURVEY_COMPLETED)

        // Second survey for the same study: the study is upserted via update, not a second insert.
        repo.markCompletedOrSkipped(surveyModel(4, study), SurveyRepository.SURVEY_COMPLETED)

        assertEquals(1, studies.inserts)
        assertEquals(1, studies.updates)
        assertEquals(2, surveys.rows.size)
    }

    @Test
    fun markCompletedOrSkipped_sameSurveyIdTwice_replacesRatherThanDuplicating() = runTest {
        val study = SurveyStudy(name = "Study", description = "Desc", id = 7)
        repo.markCompletedOrSkipped(surveyModel(3, study), SurveyRepository.SURVEY_SKIPPED)

        // Re-marking the same survey id (skip -> complete) replaces the row (survey_id PK), not appends.
        repo.markCompletedOrSkipped(surveyModel(3, study), SurveyRepository.SURVEY_COMPLETED)

        assertEquals(1, surveys.rows.size)
        assertEquals(SurveyRepository.SURVEY_COMPLETED, surveys.rows.single().state)
        assertEquals(setOf(3), repo.completedSurveyIds())
    }

    @Test
    fun completedSurveyIds_returnsEveryPersistedSurveyId() = runTest {
        val study = SurveyStudy(name = "Study", description = "Desc", id = 7)
        repo.markCompletedOrSkipped(surveyModel(3, study), SurveyRepository.SURVEY_COMPLETED)
        repo.markCompletedOrSkipped(surveyModel(9, study), SurveyRepository.SURVEY_SKIPPED)

        assertEquals(setOf(3, 9), repo.completedSurveyIds())
    }
}

private class FakeStudiesDao : StudiesDao {
    val rows = mutableMapOf<Int, Study>()
    var inserts = 0
    var updates = 0

    override suspend fun insertStudy(study: Study): Long {
        inserts++
        rows[study.study_id] = study
        return study.study_id.toLong()
    }

    override suspend fun updateStudy(study: Study) {
        updates++
        rows[study.study_id] = study
    }

    override suspend fun deleteStudy(study: Study) {
        rows.remove(study.study_id)
    }

    override suspend fun getStudyById(studyId: Int): Study? = rows[studyId]

    override suspend fun getAllStudies(): List<Study> = rows.values.toList()
}

private class FakeSurveysDao : SurveysDao {
    val rows = mutableListOf<Survey>()

    override suspend fun insertSurvey(survey: Survey): Long {
        // Model Room's OnConflictStrategy.REPLACE on the survey_id primary key.
        rows.removeAll { it.survey_id == survey.survey_id }
        rows.add(survey)
        return survey.survey_id.toLong()
    }

    override suspend fun updateSurvey(survey: Survey) {
        val i = rows.indexOfFirst { it.survey_id == survey.survey_id }
        if (i >= 0) rows[i] = survey
    }

    override suspend fun deleteSurvey(survey: Survey) {
        rows.removeAll { it.survey_id == survey.survey_id }
    }

    override suspend fun getAllSurveys(): List<Survey> = rows.toList()

    override suspend fun getSurveyById(surveyId: Int): Survey? = rows.firstOrNull { it.survey_id == surveyId }

    override suspend fun getSurveysByStudyId(studyId: Int): List<Survey> = rows.filter { it.study_id == studyId }

    override suspend fun isSurveyIdExists(surveyId: Int): Boolean = rows.any { it.survey_id == surveyId }
}
