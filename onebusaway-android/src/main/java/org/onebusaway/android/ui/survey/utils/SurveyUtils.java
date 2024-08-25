package org.onebusaway.android.ui.survey.utils;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.donations.DonationsManager;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.ui.survey.SurveyPreferences;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SurveyUtils {
    // Number of app launches required to show the survey
    public static int launchesUntilSurveyShown = 3;
    public static int remindMeLaterDays = 3;

    public static final String CHECK_BOX_QUESTION = "checkbox";
    public static final String RADIO_BUTTON_QUESTION = "radio";
    public static final String LABEL = "label";
    public static final String EXTERNAL_SURVEY = "external_survey";
    public static final String TEXT_QUESTION = "text";

    public static final int DEFAULT_SURVEY = 0;
    public static final int EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION = 1;
    public static final int EXTERNAL_SURVEY_WITH_HERO_QUESTION = 2;


    /**
     * Retrieves the answers from selected checkboxes in the provided view.
     *
     * @param view The view containing the checkboxes.
     * @return A list of selected checkbox answers as Strings.
     */
    public static List<String> getSelectedCheckBoxAnswer(View view) {
        LinearLayout container = view.findViewById(R.id.checkBoxContainer);

        List<String> selectedItems = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {

            if (container.getChildAt(i) instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) container.getChildAt(i);

                if (checkBox.isChecked()) {
                    selectedItems.add(checkBox.getText().toString());
                }
            }
        }
        return selectedItems;
    }

    /**
     * Retrieves the answer from the selected radio button in the provided view.
     *
     * @param view The view containing the RadioGroup.
     * @return The text of the selected radio button, or an empty String if none is selected.
     */
    public static String getSelectedRadioButtonAnswer(View view) {
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        int selectedId = radioGroup.getCheckedRadioButtonId();
        // Return an empty string if no radio button is selected.
        if (selectedId == -1) {
            return "";
        }
        RadioButton selectedRadioButton = radioGroup.findViewById(selectedId);
        return selectedRadioButton.getText().toString();
    }

    /**
     * Retrieves the text input answer from an EditText in the provided view.
     *
     * @param surveyView The view containing the EditText.
     * @return The text from the EditText, trimmed of leading and trailing whitespace.
     */
    public static String getTextInputAnswer(View surveyView) {
        EditText answerEditText = surveyView.findViewById(R.id.editText);
        return answerEditText.getText().toString().trim();
    }


    /**
     * Returns the index of the first uncompleted survey in the list, based on visibility settings.
     *
     * @param studyResponse   The study response containing the list of surveys.
     * @param context         The context used to access local data.
     * @param isVisibleOnStop Indicates whether the survey view is related to stops.
     * @return The zero-based index of the current survey, or -1 if all surveys are completed or filtered out.
     */
    public static Integer getCurrentSurveyIndex(StudyResponse studyResponse, Context context, Boolean isVisibleOnStop, ObaStop currentStop) {
        List<StudyResponse.Surveys> surveys = studyResponse.getSurveys();
        if (surveys == null) return -1;

        // Iterate through the surveys to find the first uncompleted one
        for (int index = 0; index < surveys.size(); index++) {
            Boolean showQuestionOnStops = surveys.get(index).getShow_on_stops();
            Boolean showQuestionOnMaps = surveys.get(index).getShow_on_map();

            List<String> visibleStopsList = surveys.get(index).getVisible_stop_list();
            List<String> visibleRouteList = surveys.get(index).getVisible_route_list();

            // Skip if there is not questions
            if (surveys.get(index).getQuestions().isEmpty()) continue;

            // Skip this survey if it shouldn't be shown on either map or stops
            if (!showQuestionOnStops && !showQuestionOnMaps) {
                continue;
            }

            if (isVisibleOnStop) {
                // Skip if the survey is not meant for stops
                if (!showQuestionOnStops) continue;
                // Check for if survey available for the current stop
                boolean showSurvey = showSurvey(currentStop, visibleStopsList, visibleRouteList);
                Log.d("SurveyStopState", "Show survey: " + showSurvey);
                if (!showSurvey) continue;
            } else {
                // Skip if the survey is not meant for maps
                if (!showQuestionOnMaps) continue;
            }

            boolean isSurveyCompleted = SurveyDbHelper.isSurveyCompleted(context, surveys.get(index).getId());

            Log.d("isSurveyCompleted", isSurveyCompleted + " ");

            // Return the index if the survey is uncompleted
            if (!isSurveyCompleted) {
                return index;
            }
        }
        // Return -1 if all surveys are completed or filtered out
        return -1;
    }

    /**
     * Determines whether to show a survey for the given stop based on the provided visible stops and routes lists.
     *
     * @param currentStop      The current stop for which the survey visibility is being checked.
     * @param visibleStopsList A list of stop IDs where the survey should be shown. Can be null.
     * @param visibleRouteList A list of route IDs where the survey should be shown. Can be null.
     * @return true if the survey should be shown for the current stop, otherwise false.
     */
    private static boolean showSurvey(ObaStop currentStop, List<String> visibleStopsList, List<String> visibleRouteList) {
        if (currentStop == null || currentStop.getId() == null) return false;
        // If both visibleStopsList and visibleRouteList are null, show the survey by default.
        if (visibleRouteList == null && visibleStopsList == null) {
            return true;
        }

        // If visibleStopsList is not null, show the survey if the current stop's ID is in the list.
        if (visibleStopsList != null && visibleStopsList.contains(currentStop.getId())) {
            return true;
        }

        // If visibleRouteList is not empty, check if any of the current stop's route IDs are in the list.
        // If a match is found, show the survey.
        if (visibleRouteList != null) {
            for (String routeID : currentStop.getRouteIds()) {
                if (visibleRouteList.contains(routeID)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Extracts answers from a question view and constructs a JSON array as a request body.
     *
     * @param questions    The survey questions containing metadata.
     * @param questionView The view containing the user's answer.
     * @return A JSON array representing the request body, or null if no valid answer is found.
     */
    public static JSONArray getSurveyAnswersRequestBody(StudyResponse.Surveys.Questions questions, View questionView) {
        JSONArray requestBody = new JSONArray();

        // Extract the answer from the question view
        String questionAnswer = getHeroQuestionAnswers(questions.getContent().getType(), questionView);

        // If the answer is empty or invalid, return null
        if (questionAnswer.isEmpty() || questionAnswer.equals("[]")) {
            return null;
        }

        try {
            // Create a JSON object to hold the question data and the answer
            JSONObject data = new JSONObject();
            data.put("question_id", questions.getId());
            data.put("question_type", questions.getContent().getType());
            data.put("question_label", questions.getContent().getLabel_text());
            data.put("answer", questionAnswer);

            // Add the JSON object to the request body array
            requestBody.put(data);
        } catch (JSONException e) {
            Log.e("JSON Parsing Error", "Failed to create JSON object: " + e.getMessage());
        }

        return requestBody;
    }


    /**
     * Generates a JSON array representing the survey answers request body from a list of questions.
     *
     * @param questionsList A list of SurveyResponse.Surveys.Questions objects containing the survey questions.
     * @return A JSONArray containing the answers for the survey questions.
     */
    public static JSONArray getSurveyAnswersRequestBody(List<StudyResponse.Surveys.Questions> questionsList) {
        return getAllSurveyQuestionAnswers(questionsList);
    }

    /**
     * Retrieves the answers for all survey questions except the hero question.
     *
     * @param questionsList A list of survey questions to extract answers from.
     * @return A JSON array representing the request body, or null if not all questions are answered.
     */
    public static JSONArray getAllSurveyQuestionAnswers(List<StudyResponse.Surveys.Questions> questionsList) {
        // Ensure all questions are answered before processing
        if (!checkAllQuestionsAnswered(questionsList)) {
            return null;
        }

        JSONArray requestBody = new JSONArray();

        for (StudyResponse.Surveys.Questions question : questionsList) {
            try {
                String questionType = question.getContent().getType();
                String questionAnswer = question.getQuestionAnswer();

                // Skip "label" and "extern survey" type questions as they don't require an answer
                if (questionType.equals(LABEL) || questionType.equals(EXTERNAL_SURVEY)) {
                    continue;
                }

                // Create a JSON object to represent the question and its answer
                JSONObject data = new JSONObject();
                data.put("question_id", question.getId());
                data.put("question_type", questionType);
                data.put("question_label", question.getContent().getLabel_text());

                // Handle multiple answers for "checkbox" type questions
                if (questionType.equals("checkbox")) {
                    questionAnswer = question.getMultipleAnswer().toString();
                }

                data.put("answer", questionAnswer);

                // Add the question data to the request body array
                requestBody.put(data);
            } catch (JSONException e) {
                Log.e("Survey Parsing Error", "Failed to parse survey question: " + e.getMessage());
            }
        }

        return requestBody;
    }

    /**
     * Checks if all survey questions in the list have been answered.
     *
     * @param questionsList A list of survey questions to check.
     * @return true if all questions are answered, false otherwise.
     */
    public static boolean checkAllQuestionsAnswered(List<StudyResponse.Surveys.Questions> questionsList) {
        for (StudyResponse.Surveys.Questions question : questionsList) {
            String questionType = question.getContent().getType();

            switch (questionType) {
                case CHECK_BOX_QUESTION:
                    if (question.getMultipleAnswer() == null) {
                        return false;
                    }
                    break;

                case TEXT_QUESTION:
                case RADIO_BUTTON_QUESTION:
                    String answer = question.getQuestionAnswer();
                    if (answer == null || answer.isEmpty()) {
                        return false;
                    }
                    break;

                default:
                    break;
            }
        }
        return true;
    }

    /**
     * Retrieves the answer from the view based on the type of question.
     *
     * @param type The type of the question (e.g., RADIO_BUTTON_QUESTION, TEXT_QUESTION, CHECK_BOX_QUESTION).
     * @param view The view from which to retrieve the answer.
     * @return The answer as a String, or an empty String if the question type is unknown.
     */

    private static String getHeroQuestionAnswers(String type, View view) {
        Log.d("QuestionType", type);
        switch (type) {
            case RADIO_BUTTON_QUESTION:
                return SurveyUtils.getSelectedRadioButtonAnswer(view);

            case TEXT_QUESTION:
                return SurveyUtils.getTextInputAnswer(view);

            case CHECK_BOX_QUESTION:
                return SurveyUtils.getSelectedCheckBoxAnswer(view).toString();

            default:
                Log.d("UnknownQuestionType", "Unrecognized question type: " + type);
                return "";
        }
    }


    /**
     * Checks if the current survey for the user has an external survey.
     *
     * @return 0 if it's a default survey.
     * 1 if there is an external survey without a hero question.
     * 2 if there is an external survey with a hero question.
     */
    public static Integer checkExternalSurvey(List<StudyResponse.Surveys.Questions> questionsList) {
        if (questionsList.size() == 1) {
            if (questionsList.get(0).getContent().getType().equals(EXTERNAL_SURVEY)) return 1;
        } else if (questionsList.size() >= 2) {
            if (questionsList.get(1).getContent().getType().equals(EXTERNAL_SURVEY)) {
                return 2;
            }
        }
        return 0;
    }

    public static Boolean shouldShowSurveyView(Context context,boolean isVisibleOnStops) {
        // User will receive a survey every `surveyLaunchCount` app launches
        if (Application.get().getAppLaunchCount() % launchesUntilSurveyShown != 0) return false;

        // Don't show the UI if there's a reminder date that is still in the future.
        Date reminderDate = getSurveyRequestReminderDate(context);
        if (reminderDate != null && reminderDate.after(new Date())) {
            return false;
        }

        // If the survey view is not visible on stops, perform additional checks
        if (!isVisibleOnStops) {
            DonationsManager donationsManager = Application.getDonationsManager();
            // If the donation UI is visible, do not show the survey on the map
            // Otherwise, show the survey on the map
            return !donationsManager.shouldShowDonationUI();
        }

        return true;
    }

    public static void remindUserLater(Context context) {
        // Calculate the delay in milliseconds:
        // 86400 seconds in a day * remindMeLaterDays * 1000 milliseconds per second
        long dateInMilliSeconds = 86400L * remindMeLaterDays * 1000;

        // Future date when the reminder should be triggered
        Date futureDate = new Date((new Date()).getTime() + dateInMilliSeconds);

        // Save the calculated future date in SharedPreferences as the survey reminder date
        SurveyPreferences.setSurveyReminderDate(context, futureDate);
    }

    /**
     * @return Optional date at which the app should remind the user to take survey.
     */
    private static Date getSurveyRequestReminderDate(Context context) {
        long timestamp = SurveyPreferences.getSurveyReminderDate(context);
        if (timestamp < 1) {
            return null;
        }

        return new Date(timestamp);
    }

    public static Boolean isSurveyValid(StudyResponse.Surveys survey) {
        return survey != null && survey.getStudy() != null;
    }
}
