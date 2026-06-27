package org.onebusaway.android.ui.survey.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.onebusaway.android.app.Application
import org.onebusaway.android.database.survey.SurveyDbHelper
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.Survey
import org.onebusaway.android.models.SurveyQuestion
import org.onebusaway.android.ui.survey.SurveyPreferences
import java.util.Date

/**
 * Utility class for managing and processing surveys within the application.
 */
object SurveyUtils {
    // Number of app launches required to show the survey
    var launchesUntilSurveyShown = 3
    var remindMeLaterDays = 3

    const val CHECK_BOX_QUESTION = "checkbox"
    const val RADIO_BUTTON_QUESTION = "radio"
    const val LABEL = "label"
    const val EXTERNAL_SURVEY = "external_survey"
    const val TEXT_QUESTION = "text"

    const val DEFAULT_SURVEY = 0
    const val EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION = 1
    const val EXTERNAL_SURVEY_WITH_HERO_QUESTION = 2

    const val USER_ID = "user_id"
    const val REGION_ID = "region_id"
    const val ROUTE_ID = "route_id"
    const val STOP_ID = "stop_id"
    const val CURRENT_LOCATION = "current_location"
    const val RECENT_STOP_IDS = "recent_stop_ids"

    /**
     * Returns the index of the first uncompleted survey in the list, based on visibility settings.
     *
     * @param studyResponse   The study response containing the list of surveys.
     * @param context         The context used to access local data.
     * @param isVisibleOnStop Indicates whether the survey view is related to stops.
     * @return The zero-based index of the current survey, or -1 if all surveys are completed or filtered out.
     */
    fun getCurrentSurveyIndex(
        surveys: List<Survey>,
        context: Context,
        isVisibleOnStop: Boolean,
        currentStop: ObaStop?
    ): Int {
        var alwaysVisibleIndex = -1
        var oneTimeSurveyIndex = -1

        for (index in surveys.indices) {
            val survey = surveys[index]

            val showQuestionOnStops = survey.showOnStops
            val showQuestionOnMaps = survey.showOnMap
            val alwaysVisible = survey.alwaysVisible
            val allowMultipleResponses = survey.allowsMultipleResponses

            val visibleStopsList = survey.visibleStopList
            val visibleRouteList = survey.visibleRouteList

            if (survey.questions.isEmpty()) continue

            // Skip this survey if it shouldn't be shown on either map or stops
            if (showQuestionOnStops != true && showQuestionOnMaps != true) {
                continue
            }

            if (isVisibleOnStop) {
                // Skip if the survey is not meant for stops
                if (showQuestionOnStops != true) continue
                // Check for if survey available for the current stop
                if (!showSurvey(currentStop, visibleStopsList, visibleRouteList)) continue
            } else {
                // Skip if the survey is not meant for maps
                if (showQuestionOnMaps != true) continue
            }

            val isSurveyCompleted = SurveyDbHelper.isSurveyCompleted(context, survey.id)

            if (alwaysVisible == true) {
                if (allowMultipleResponses == true) {
                    // Always visible and multiple responses allowed, show always unless a one-time survey exists
                    alwaysVisibleIndex = index
                } else {
                    // Always visible but single response only, show if not completed
                    if (!isSurveyCompleted) {
                        return index
                    }
                }
            } else {
                // Normal behavior: Show if not completed
                if (!isSurveyCompleted) {
                    oneTimeSurveyIndex = index
                }
            }
        }

        return if (oneTimeSurveyIndex != -1) oneTimeSurveyIndex else alwaysVisibleIndex
    }

    /**
     * Determines whether to show a survey for the given stop based on the provided visible stops and routes lists.
     *
     * @param currentStop      The current stop for which the survey visibility is being checked.
     * @param visibleStopsList A list of stop IDs where the survey should be shown. Can be null.
     * @param visibleRouteList A list of route IDs where the survey should be shown. Can be null.
     * @return true if the survey should be shown for the current stop, otherwise false.
     */
    private fun showSurvey(
        currentStop: ObaStop?,
        visibleStopsList: List<String>?,
        visibleRouteList: List<String>?
    ): Boolean {
        if (currentStop == null || currentStop.id == null) return false
        // If both visibleStopsList and visibleRouteList are null, show the survey by default.
        if (visibleRouteList == null && visibleStopsList == null) {
            return true
        }

        // If visibleStopsList is not null, show the survey if the current stop's ID is in the list.
        if (visibleStopsList != null && visibleStopsList.contains(currentStop.id)) {
            return true
        }

        // If visibleRouteList is not empty, check if any of the current stop's route IDs are in the
        // list. If a match is found, show the survey.
        if (visibleRouteList != null) {
            for (routeID in currentStop.routeIds) {
                if (visibleRouteList.contains(routeID)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Generates a JSON array representing the survey answers request body from a list of questions.
     *
     * @param questionsList A list of SurveyResponse.Surveys.Questions objects containing the survey questions.
     * @return A JSONArray containing the answers for the survey questions.
     */
    fun getSurveyAnswersRequestBody(
        questionsList: List<SurveyQuestion>
    ): JSONArray? {
        return getAllSurveyQuestionAnswers(questionsList)
    }

    /**
     * Retrieves the answers for all survey questions except the hero question.
     *
     * @param questionsList A list of survey questions to extract answers from.
     * @return A JSON array representing the request body, or null if not all questions are answered.
     */
    fun getAllSurveyQuestionAnswers(
        questionsList: List<SurveyQuestion>
    ): JSONArray? {
        // Ensure all questions are answered before processing
        if (!checkAllQuestionsAnswered(questionsList)) {
            return null
        }

        val requestBody = JSONArray()

        for (question in questionsList) {
            try {
                val questionType = question.content.type
                var questionAnswer = question.answer

                // Skip "label" and "extern survey" type questions as they don't require an answer
                if (questionType == LABEL || questionType == EXTERNAL_SURVEY) {
                    continue
                }

                // Create a JSON object to represent the question and its answer
                val data = JSONObject()
                data.put("question_id", question.id)
                data.put("question_type", questionType)
                data.put("question_label", question.content.labelText)

                // Handle multiple answers for "checkbox" type questions
                if (questionType == "checkbox") {
                    questionAnswer = question.multipleAnswer.toString()
                }

                data.put("answer", questionAnswer)

                // Add the question data to the request body array
                requestBody.put(data)
            } catch (e: JSONException) {
                Log.e("Survey Parsing Error", "Failed to parse survey question: " + e.message)
            }
        }

        return requestBody
    }

    /**
     * Checks if all survey questions in the list have been answered.
     *
     * @param questionsList A list of survey questions to check.
     * @return true if all questions are answered, false otherwise.
     */
    fun checkAllQuestionsAnswered(
        questionsList: List<SurveyQuestion>
    ): Boolean {
        for (question in questionsList) {
            val isAnswerRequired = question.isRequired
            val questionType = question.content.type

            // Skip validating the question answer if it's optional
            if (!isAnswerRequired) {
                continue
            }

            when (questionType) {
                CHECK_BOX_QUESTION -> if (question.multipleAnswer == null) {
                    return false
                }

                TEXT_QUESTION, RADIO_BUTTON_QUESTION -> {
                    val answer = question.answer
                    if (answer.isNullOrEmpty()) {
                        return false
                    }
                }
            }
        }
        return true
    }

    /**
     * Checks if the current survey for the user has an external survey.
     *
     * @return 0 if it's a default survey.
     * 1 if there is an external survey without a hero question.
     * 2 if there is an external survey with a hero question.
     */
    fun checkExternalSurvey(questionsList: List<SurveyQuestion>): Int {
        if (questionsList.size == 1) {
            if (questionsList[0].content.type == EXTERNAL_SURVEY) return 1
        } else if (questionsList.size >= 2) {
            if (questionsList[1].content.type == EXTERNAL_SURVEY) {
                return 2
            }
        }
        return 0
    }

    fun shouldShowSurveyView(context: Context, isVisibleOnStops: Boolean): Boolean {
        // User will receive a survey every `surveyLaunchCount` app launches
        if (Application.get().appLaunchCount % launchesUntilSurveyShown != 0) return false

        // Don't show the UI if there's a reminder date that is still in the future.
        val reminderDate = getSurveyRequestReminderDate(context)
        if (reminderDate != null && reminderDate.after(Date())) {
            return false
        }

        // If the survey view is not visible on stops, perform additional checks
        if (!isVisibleOnStops) {
            // If the donation UI is visible, do not show the survey on the map
            // Otherwise, show the survey on the map
            return !Application.getDonationsManager().shouldShowDonationUI()
        }

        return true
    }

    fun remindUserLater(context: Context) {
        // Calculate the delay in milliseconds:
        // 86400 seconds in a day * remindMeLaterDays * 1000 milliseconds per second
        val dateInMilliSeconds = 86400L * remindMeLaterDays * 1000

        // Future date when the reminder should be triggered
        val futureDate = Date(Date().time + dateInMilliSeconds)

        // Save the calculated future date in SharedPreferences as the survey reminder date
        SurveyPreferences.setSurveyReminderDate(context, futureDate)
    }

    /**
     * @return Optional date at which the app should remind the user to take survey.
     */
    private fun getSurveyRequestReminderDate(context: Context): Date? {
        val timestamp = SurveyPreferences.getSurveyReminderDate(context)
        if (timestamp < 1) {
            return null
        }

        return Date(timestamp)
    }

    fun isValidEmbeddedDataList(embeddedDataList: ArrayList<String>?): Boolean {
        return !embeddedDataList.isNullOrEmpty()
    }

    /**
     * Converts an array of route IDs to an ArrayList.
     *
     * @param routes An array of route IDs.
     * @return An ArrayList containing the route IDs from the input array.
     */
    fun getRoutesIDList(routes: Array<String>): ArrayList<String> {
        return ArrayList(routes.asList())
    }

    /**
     * Returns the stop identifier if the stop is non-null, has a valid ID,
     * and is marked as visible; otherwise, returns null.
     *
     * @param currentStop    The current stop object.
     * @param visibleOnStops Indicates if the stop is visible.
     * @return The stop identifier or null if conditions are not met.
     */
    fun getCurrentStopIdentifier(currentStop: ObaStop?, visibleOnStops: Boolean): String? {
        return if (currentStop != null && currentStop.id != null && visibleOnStops) {
            currentStop.id
        } else {
            null
        }
    }

    /**
     * Returns the current stop latitude if the stop is non-null and visible.
     *
     * @param currentStop    The current stop object.
     * @param visibleOnStops Indicates if the stop is visible.
     * @return The current stop latitude or 0 if the stop is null or not visible.
     */
    fun getCurrentStopLatitude(currentStop: ObaStop?, visibleOnStops: Boolean): Double {
        if (currentStop == null || !visibleOnStops) return 0.0
        return currentStop.latitude
    }

    /**
     * Returns the current stop longitude if the stop is non-null and visible.
     *
     * @param currentStop    The current stop object.
     * @param visibleOnStops Indicates if the stop is visible.
     * @return The current stop longitude or 0 if the stop is null or not visible.
     */
    fun getCurrentStopLongitude(currentStop: ObaStop?, visibleOnStops: Boolean): Double {
        if (currentStop == null || !visibleOnStops) return 0.0
        return currentStop.longitude
    }
}
