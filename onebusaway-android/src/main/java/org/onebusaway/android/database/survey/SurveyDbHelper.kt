package org.onebusaway.android.database.survey

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.models.Survey as SurveyModel

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
        fun markSurveyAsCompletedOrSkipped(context: Context, curSurvey: SurveyModel, state: Int) {
            val surveyRepo = SurveyRepository(context)

            val study = curSurvey.study ?: return
            val newStudy = Study(
                study.id, study.name.orEmpty(), study.description.orEmpty(), true
            )
            val newSurvey = Survey(curSurvey.id, study.id, curSurvey.name.orEmpty(), state)

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
