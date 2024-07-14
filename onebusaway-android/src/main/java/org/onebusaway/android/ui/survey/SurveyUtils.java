package org.onebusaway.android.ui.survey;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.onebusaway.android.R;
import org.onebusaway.android.ui.survey.model.StudyResponse;

import java.util.ArrayList;
import java.util.List;

public class SurveyUtils {

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
        checkBoxLabel.setVisibility(View.VISIBLE);

        for (String item : question.getContent().getOptions()) {
            CheckBox checkBox = createCheckBox(ctx, question, question.getContent().getOptions().indexOf(item));
            checkBox.setText(item);
            checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            checkboxContainer.addView(checkBox);
        }
    }

    public static void showTextInputQuestion(Context ctx, View view, StudyResponse.Surveys.Questions question) {
        EditText lableEditText = view.findViewById(R.id.editText);
        lableEditText.setVisibility(View.VISIBLE);

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

    private static List<String> getSelectedCheckBoxes(LinearLayout container) {
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


}
