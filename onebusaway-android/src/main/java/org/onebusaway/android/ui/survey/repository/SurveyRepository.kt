package org.onebusaway.android.ui.survey.repository

import android.content.Context
import androidx.room.Room
import org.onebusaway.android.database.AppDatabase
import org.onebusaway.android.ui.survey.entity.Study
import org.onebusaway.android.ui.survey.entity.Survey


class SurveyRepository(context: Context) {
    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext, AppDatabase::class.java, "study-survey-db"
    ).build()

    private val studiesDao = db.studiesDao()
    private val surveysDao = db.surveysDao()

    suspend fun addOrUpdateStudy(study: Study) {
        val existingStudy = studiesDao.getStudyById(study.study_id)
        if (existingStudy == null) {
            studiesDao.insertStudy(study)
        } else {
            studiesDao.updateStudy(study)
        }
    }

    suspend fun getAllStudies(): List<Study> {
        return studiesDao.getAllStudies()
    }

    suspend fun addSurvey(survey: Survey) {
        surveysDao.insertSurvey(survey)
    }

    suspend fun getSurveysForStudy(studyId: Int): List<Survey> {
        return surveysDao.getSurveysByStudyId(studyId)
    }

    suspend fun checkSurveyCompleted(surveyId: Int): Boolean {
        return surveysDao.isSurveyIdExists(surveyId)
    }

    suspend fun getAllSurveys(): List<Survey> {
        return surveysDao.getAllSurveys();
    }
}
