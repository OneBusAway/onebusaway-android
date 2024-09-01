package org.onebusaway.android.ui.survey;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onebusaway.android.R;
import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SurveyUtils {


    public static String getUserUUID(Context context) {
        if (SurveyLocalData.getUserUUID(context) == null) {
            UUID uuid = UUID.randomUUID();
            SurveyLocalData.saveUserUUID(context, uuid);
        }
        return SurveyLocalData.getUserUUID(context);
    }

    public static void showRadioGroupQuestion(Context ctx, View view, StudyResponse.Surveys.Questions question) {
        TextView surveyTitle = view.findViewById(R.id.survey_question_tv);
        RadioGroup radio = view.findViewById(R.id.radioGroup);
        radio.setVisibility(View.VISIBLE);
        for (int i = 0; i < question.getContent().getOptions().size(); ++i) {
            RadioButton radioButton = createRadioButton(ctx, question, i);
            radio.addView(radioButton);
        }
        surveyTitle.setText(question.getContent().getLabel_text());
    }


    public static void showCheckBoxQuestion(Context ctx, View view, StudyResponse.Surveys.Questions question) {
        LinearLayout checkboxContainer = view.findViewById(R.id.checkBoxContainer);
        TextView checkBoxLabel = view.findViewById(R.id.checkBoxLabel);
        TextView surveyTitle = view.findViewById(R.id.survey_question_tv);
        checkBoxLabel.setVisibility(View.VISIBLE);

        for (String item : question.getContent().getOptions()) {
            CheckBox checkBox = createCheckBox(ctx, question, question.getContent().getOptions().indexOf(item));
            checkBox.setText(item);
            checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            checkboxContainer.addView(checkBox);
        }

        surveyTitle.setText(question.getContent().getLabel_text());
    }

    public static void showTextInputQuestion(Context ctx, View view, StudyResponse.Surveys.Questions question) {
        EditText lableEditText = view.findViewById(R.id.editText);
        TextView surveyTitle = view.findViewById(R.id.survey_question_tv);

        lableEditText.setVisibility(View.VISIBLE);

        surveyTitle.setText(question.getContent().getLabel_text());
    }

    @NonNull
    private static <T extends CompoundButton> T createButton(Context ctx, StudyResponse.Surveys.Questions question, int position, Class<T> buttonClass) {
        T button;
        try {
            button = buttonClass.getDeclaredConstructor(Context.class).newInstance(ctx);
        } catch (Exception e) {
            throw new RuntimeException("Error creating button instance", e);
        }

        button.setText(question.getContent().getOptions().get(position));
        button.setId(position);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginInDp = 4, paddingInDp = 4;

        // Set margin
        float scale = ctx.getResources().getDisplayMetrics().density;
        int marginInPx = (int) (marginInDp * scale + 0.5f);
        params.setMargins(0, marginInPx, 0, marginInPx);

        // Set padding
        int paddingInPx = (int) (paddingInDp * scale + 0.5f);
        int extraPadding = (int) ((paddingInDp + 2) * scale + 0.5f);
        button.setPadding(paddingInPx + extraPadding, paddingInPx, paddingInPx, paddingInPx);
        button.setLayoutParams(params);

        // Remove default button drawable
        button.setButtonDrawable(null);

        // Set custom drawable to the right
        Drawable customCheckIcon = ContextCompat.getDrawable(ctx, R.drawable.survey_custom_button);
        if (customCheckIcon != null) {
            customCheckIcon.setBounds(0, 0, customCheckIcon.getIntrinsicWidth(), customCheckIcon.getIntrinsicHeight());
        }
        button.setCompoundDrawablesWithIntrinsicBounds(null, null, customCheckIcon, null);

        // Change button text size
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        // Set custom background for the drawable
        button.setBackgroundResource(R.drawable.survey_button_custom_background);

        return button;
    }

    @NonNull
    private static RadioButton createRadioButton(Context ctx, StudyResponse.Surveys.Questions question, int position) {
        return createButton(ctx, question, position, RadioButton.class);
    }

    @NonNull
    private static CheckBox createCheckBox(Context ctx, StudyResponse.Surveys.Questions question, int position) {
        return createButton(ctx, question, position, CheckBox.class);
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
