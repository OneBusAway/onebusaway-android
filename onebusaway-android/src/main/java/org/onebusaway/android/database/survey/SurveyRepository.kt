package org.onebusaway.android.database.survey

import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.database.survey.dao.StudiesDao
import org.onebusaway.android.database.survey.dao.SurveysDao
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.models.Survey as SurveyModel

/**
 * Repository for the survey Study/Survey tables in the unified [org.onebusaway.android.database.AppDatabase]
 * (storage-modernization). Hilt-injected; the one-time import of any pre-existing `study-survey-db`
 * data is handled by [org.onebusaway.android.database.oba.LegacyDataImporter.importSurveyDbIfNeeded].
 */
@Singleton
class SurveyRepository @Inject constructor(
    private val studiesDao: StudiesDao,
    private val surveysDao: SurveysDao,
) {

    /**
     * The set of survey ids that already have a persisted response (completed or skipped), so a survey
     * isn't shown again. Fetched once per study response and consulted in-memory rather than querying
     * per-survey (see [org.onebusaway.android.ui.survey.utils.SurveyUtils.getCurrentSurveyIndex]).
     */
    suspend fun completedSurveyIds(): Set<Int> =
        surveysDao.getAllSurveys().map { it.survey_id }.toSet()

    /**
     * Persists a survey as completed or skipped (formerly `SurveyDbHelper.markSurveyAsCompletedOrSkipped`):
     * upserts the parent study, then records the survey row with the given [state].
     */
    suspend fun markCompletedOrSkipped(curSurvey: SurveyModel, state: Int) {
        val study = curSurvey.study ?: return
        upsertStudy(Study(study.id, study.name.orEmpty(), study.description.orEmpty(), true))
        surveysDao.insertSurvey(Survey(curSurvey.id, study.id, curSurvey.name.orEmpty(), state))
    }

    private suspend fun upsertStudy(study: Study) {
        if (studiesDao.getStudyById(study.study_id) == null) {
            studiesDao.insertStudy(study)
        } else {
            studiesDao.updateStudy(study)
        }
    }

    companion object {
        const val SURVEY_COMPLETED = 1
        const val SURVEY_SKIPPED = 2
    }
}
