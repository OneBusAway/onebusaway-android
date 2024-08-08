package org.onebusaway.android.ui.survey;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class SurveyUtils {


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
            return null;
        }
        RadioButton selectedRadioButton = radioGroup.findViewById(selectedId);
        return selectedRadioButton.getText().toString();
    }

    public static String getTextInputAnswer(View view) {
        EditText editText = view.findViewById(R.id.editText);
        return editText.getText().toString().trim();
    }

    public static Integer getCurrentSurveyIndex(StudyResponse studyResponse,Context context){
        HashMap<String,Boolean> doneSurvey = SurveyLocalData.getAnsweredSurveys(context);
        List<StudyResponse.Surveys> surveys = studyResponse.getSurveys();
        // Get the first undone survey index
        for(int index = 0; index < surveys.size(); index++) {
            Boolean result = doneSurvey.get(surveys.get(index).getId().toString());
            if(result == null)return index;
        }
        // If all surveys are done, return -1
        return -1;
    }

    public static void markSurveyAsDone(Context context, String surveyID){
        HashMap<String,Boolean> doneSurvey = SurveyLocalData.getAnsweredSurveys(context);
        doneSurvey.put(surveyID,true);
        // Save to the local storage
        SurveyLocalData.setAnsweredSurveys(context,doneSurvey);
    }

    public static JSONArray getSurveyAnswersRequestBody(StudyResponse.Surveys.Questions questions,View questionView){
        JSONArray requestBody = new JSONArray();
        String questionAnswer = getHeroQuestionAnswers(questions.getContent().getType(),questionView);
        if(questionAnswer.isEmpty() || questionAnswer.equals("[]")) return null;
        try {
            JSONObject data = new JSONObject();
            data.put("question_id", questions.getId());
            data.put("question_type", questions.getContent().getType());
            data.put("question_label", questions.getContent().getLabel_text());
            data.put("answer", questionAnswer);
            requestBody.put(data);
        } catch (JSONException e) {
            Log.e("Parsing JSON", e.getMessage());
        }
        return requestBody;
    }
    public static JSONArray getSurveyAnswersRequestBody(List<StudyResponse.Surveys.Questions> questionsList){
        return getAllSurveyQuestionAnswers(questionsList);
    }

    /**
     * @return current survey questions answer expect the hero question
     */
    public static JSONArray getAllSurveyQuestionAnswers(List<StudyResponse.Surveys.Questions> questionsList){
        if(!checkAllQuestionsAnswered(questionsList)) return null;
        JSONArray requestBody = new JSONArray();
        for(StudyResponse.Surveys.Questions question : questionsList) {
             try{
                 String questionType = question.getContent().getType();
                 String questionAnswer = question.getQuestionAnswer();
                 if(questionType.equals("label")) continue;
                 JSONObject data = new JSONObject();
                 data.put("question_id", question.getId());
                 data.put("question_type", questionType);
                 data.put("question_label", question.getContent().getLabel_text());
                 if(questionType.equals("checkbox")) questionAnswer = question.getMultipleAnswer().toString();
                 data.put("answer", questionAnswer);
                 requestBody.put(data);
             }catch (Exception e) {
                 Log.e("Parsing Survey Error", e.getMessage());
             }
        }
        return requestBody;
    }

    public static Boolean checkAllQuestionsAnswered(List<StudyResponse.Surveys.Questions> questionsList) {
        for (StudyResponse.Surveys.Questions question : questionsList) {
            String questionType = question.getContent().getType();
            switch (questionType){
                case "checkbox":
                    if(question.getMultipleAnswer() == null) return false;
                    break;
                case "text":
                case "radio":
                    if(question.getQuestionAnswer() == null || question.getQuestionAnswer().isEmpty())return false;
                    break;
            }
        }
        return true;
    }

    private static String getHeroQuestionAnswers(String type, View view){
        switch (type) {
            case "radio":
                return  SurveyUtils.getSelectedRadioButtonAnswer(view);
            case "text":
                return  SurveyUtils.getTextInputAnswer(view);
            case "checkbox":
                return SurveyUtils.getSelectedCheckBoxAnswer(view).toString();
        }
        return "";
    }



}
