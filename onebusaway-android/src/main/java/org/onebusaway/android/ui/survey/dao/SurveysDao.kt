package org.onebusaway.android.ui.survey.dao

import androidx.room.*
import org.onebusaway.android.ui.survey.entity.Survey

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
    suspend fun getAllSurveys():List<Survey>

    @Query("SELECT * FROM surveys WHERE survey_id = :surveyId")
    suspend fun getSurveyById(surveyId: Int): Survey?

    @Query("SELECT * FROM surveys WHERE study_id = :studyId")
    suspend fun getSurveysByStudyId(studyId: Int): List<Survey>

    @Query("SELECT COUNT(*) > 0 FROM surveys WHERE survey_id = :surveyId")
    suspend fun isSurveyIdExists(surveyId: Int): Boolean

}
