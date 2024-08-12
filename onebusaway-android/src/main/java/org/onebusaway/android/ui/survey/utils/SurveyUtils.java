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
import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.ui.survey.SurveyLocalData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class SurveyUtils {

    public static final String CHECK_BOX_QUESTION = "checkbox";
    public static final String RADIO_BUTTON_QUESTION = "radio";
    public static final String LABEL = "label";
    public static final String EXTERNAL_SURVEY = "external_survey";
    public static final String TEXT_QUESTION = "text";

    public static final int EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION = 1;
    public static final int EXTERNAL_SURVEY_WITH_HERO_QUESTION = 2;


    public static String getUserUUID(Context context) {
        if (SurveyLocalData.getUserUUID(context) == null) {
            UUID uuid = UUID.randomUUID();
            SurveyLocalData.saveUserUUID(context, uuid);
        }
        return SurveyLocalData.getUserUUID(context);
    }

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

    public static String getSelectedRadioButtonAnswer(View view) {
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            return "";
        }
        RadioButton selectedRadioButton = radioGroup.findViewById(selectedId);
        return selectedRadioButton.getText().toString();
    }

    public static String getTextInputAnswer(View view) {
        EditText editText = view.findViewById(R.id.editText);
        return editText.getText().toString().trim();
    }

    /**
     * Returns the index of the first uncompleted survey in the list, based on visibility settings.
     *
     * @param studyResponse   The study response containing the list of surveys.
     * @param context         The context used to access local data.
     * @param isVisibleOnStop Indicates whether the survey view is related to stops.
     * @return The zero-based index of the current survey, or -1 if all surveys are completed or filtered out.
     */
    public static Integer getCurrentSurveyIndex(StudyResponse studyResponse, Context context, Boolean isVisibleOnStop) {
        // Map of completed surveys with survey IDs as keys and completion status as values
        HashMap<String, Boolean> completedSurveysMap = SurveyLocalData.getCompletedSurveys(context);

        List<StudyResponse.Surveys> surveys = studyResponse.getSurveys();

        // Iterate through the surveys to find the first uncompleted one
        for (int index = 0; index < surveys.size(); index++) {
            Boolean showQuestionOnStops = surveys.get(index).getShow_on_stops();
            Boolean showQuestionOnMaps = surveys.get(index).getShow_on_map();

            // Skip this survey if it shouldn't be shown on either map or stops
            if (Boolean.FALSE.equals(showQuestionOnStops) && Boolean.FALSE.equals(showQuestionOnMaps)) {
                continue;
            }

            if (isVisibleOnStop) {
                // Skip if the survey is not meant for stops
                if (Boolean.FALSE.equals(showQuestionOnStops)) continue;
            } else {
                // Skip if the survey is not meant for maps
                if (Boolean.FALSE.equals(showQuestionOnMaps)) continue;
            }

            // Return the index if the survey is uncompleted
            if (!Boolean.TRUE.equals(completedSurveysMap.get(surveys.get(index).getId().toString()))) {
                return index;
            }
        }
        // Return -1 if all surveys are completed or filtered out
        return -1;
    }

    public static void markSurveyAsCompleted(Context context, String surveyID) {
        HashMap<String, Boolean> doneSurvey = SurveyLocalData.getCompletedSurveys(context);
        doneSurvey.put(surveyID, true);
        // Save to the local storage
        SurveyLocalData.setCompletedSurveys(context, doneSurvey);
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
        if (questionAnswer.isEmpty() || "[]".equals(questionAnswer)) {
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

                // Skip "label" type questions as they don't require an answer
                if (questionType.equals("label")) {
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
     * @return 0 if it's a normal survey.
     * 1 if there is an external survey without a hero question.
     * 2 if there is an external survey with a hero question.
     */
    public static Integer checkExternalSurvey(List<StudyResponse.Surveys.Questions> questionsList) {
        if (questionsList.size() == 1) {
            if (questionsList.get(0).getContent().getType().equals("external_survey")) return 1;
        } else if (questionsList.size() >= 2) {
            if (questionsList.get(1).getContent().getType().equals("external_survey")) {
                return 2;
            }
        }
        return 0;
    }

}
