package org.onebusaway.android.ui.survey.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.onebusaway.android.io.request.survey.model.StudyResponse
import org.onebusaway.android.ui.survey.entity.Study
import org.onebusaway.android.ui.survey.entity.Survey
import org.onebusaway.android.ui.survey.repository.SurveyRepository

/**
 * Utility class for handling operations related to surveys in the database.
 */
class SurveyDbHelper {

    companion object {
        // Coroutine scope for performing background operations.
        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Constants representing survey states.
        const val SURVEY_COMPLETED = 1
        const val SURVEY_SKIPPED = 2

        /**
         * Marks a survey as completed or skipped and updates the database.
         *
         * @param context
         * @param curSurvey The current survey data to be updated.
         * @param state The state of the survey (e.g., completed or skipped).
         */
        @JvmStatic
        fun markSurveyAsCompletedOrSkipped(context: Context, curSurvey: StudyResponse.Surveys, state: Int) {
            val surveyRepo = SurveyRepository(context)

            val newStudy = Study(
                curSurvey.study.id, curSurvey.study.name, curSurvey.study.description, true
            )
            val newSurvey = Survey(curSurvey.id, curSurvey.study.id, curSurvey.name, state)

            coroutineScope.launch {
                try {
                    surveyRepo.addOrUpdateStudy(newStudy)
                    surveyRepo.addSurvey(newSurvey)
                    Log.d("All Saved Surveys", surveyRepo.getAllSurveys().toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * Checks if a survey has been completed based on its ID.
         *
         * @param context
         * @param surveyId The ID of the survey to check.
         * @return True if the survey is completed, false otherwise.
         */
        @JvmStatic
        fun isSurveyCompleted(context: Context, surveyId: Int): Boolean {
            val surveyRepo = SurveyRepository(context)
            return runBlocking {
                try {
                    surveyRepo.checkSurveyCompleted(surveyId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }
}
