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

class SurveyDbHelper {

    companion object {
        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        const val SURVEY_COMPLETED = 1
        const val SURVEY_SKIPPED = 2

        @JvmStatic
        fun markSurveyAsCompletedOrSkipped(context: Context, survey: StudyResponse.Surveys, state:Int) {
            val surveyRepo = SurveyRepository(context)

            val newStudy = Study(
                survey.study.id, survey.study.name, survey.study.description, true
            )
            val newSurvey = Survey(survey.id, survey.study.id, survey.name, state)
            coroutineScope.launch {
                try {
                    surveyRepo.addOrUpdateStudy(newStudy)
                    surveyRepo.addSurvey(newSurvey)
                    Log.d("All Saved Surveys", surveyRepo.getAllSurveys().toString());
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

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