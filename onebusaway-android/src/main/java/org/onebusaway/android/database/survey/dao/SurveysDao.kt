/*
 * Copyright The OneBusAway Authors.
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

package org.onebusaway.android.database.survey.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.onebusaway.android.database.survey.entity.Survey

/**
 * DAO interface for managing database operations related to Survey entities
 * including queries by study and survey IDs.
*/
@Dao
interface SurveysDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: Survey): Long

    @Update
    suspend fun updateSurvey(survey: Survey)

    @Delete
    suspend fun deleteSurvey(survey: Survey)

    @Query("SELECT *FROM surveys")
    suspend fun getAllSurveys(): List<Survey>

    @Query("SELECT * FROM surveys WHERE survey_id = :surveyId")
    suspend fun getSurveyById(surveyId: Int): Survey?

    @Query("SELECT * FROM surveys WHERE study_id = :studyId")
    suspend fun getSurveysByStudyId(studyId: Int): List<Survey>

    @Query("SELECT COUNT(*) > 0 FROM surveys WHERE survey_id = :surveyId")
    suspend fun isSurveyIdExists(surveyId: Int): Boolean
}
