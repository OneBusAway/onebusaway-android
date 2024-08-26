package org.onebusaway.android.ui.survey.utils;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.onebusaway.android.R;
import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.ui.survey.SurveyActionsListener;
import org.onebusaway.android.ui.survey.activities.SurveyWebViewActivity;

import java.util.List;

public class SurveyViewUtils {
    private final View surveyView;
    private final Context mContext;
    private final SurveyActionsListener mSurveyActionsListener;
    public Button nextButton;
    public Button openExternalSurveyButton;
    // List of arrivals to which the survey will be added
    public ListView arrivalsList;
    private ImageButton closeSurveyButton;
    private TextView surveyTitleTextView;
    private TextView sharedInfoTextView;
    private View progressOverlay;
    private View questionsContainer;



    public SurveyViewUtils(View surveyView, Context context, SurveyActionsListener surveyActionsListener) {
        this.surveyView = surveyView;
        this.mContext = context;
        mSurveyActionsListener = surveyActionsListener;
        initViews();
    }

    public void initViews() {
        nextButton = surveyView.findViewById(R.id.nextBtn);
        openExternalSurveyButton = surveyView.findViewById(R.id.openExternalSurveyBtn);
        closeSurveyButton = surveyView.findViewById(R.id.close_btn);
        surveyTitleTextView = surveyView.findViewById(R.id.survey_question_tv);
        sharedInfoTextView = surveyView.findViewById(R.id.shared_info_tv);
        progressOverlay = surveyView.findViewById(R.id.surveyProgress);
        questionsContainer = surveyView.findViewById(R.id.questionsContainer);
    }

    public void showHeroQuestionButtons() {
        handleCLoseButton();
        nextButton.setVisibility(View.VISIBLE);
    }

    public void showExternalSurveyButtons() {
        handleCLoseButton();
        openExternalSurveyButton.setVisibility(View.VISIBLE);
    }

    private void handleCLoseButton() {
        closeSurveyButton.setVisibility(View.VISIBLE);
        closeSurveyButton.setOnClickListener(v -> createDismissSurveyDialog(mContext).show());
    }

    public void initSurveyArrivalsList(ListView arrivalsList) {
        this.arrivalsList = arrivalsList;
    }
    public void showHeroQuestion(Context context, StudyResponse.Surveys.Questions heroQuestion) {
        String questionType = heroQuestion.getContent().getType();
        switch (questionType) {
            case SurveyUtils.RADIO_BUTTON_QUESTION:
                showRadioGroupQuestion(context, surveyView, heroQuestion);
                break;
            case SurveyUtils.TEXT_QUESTION:
                showTextInputQuestion(surveyView, heroQuestion);
                break;
            case SurveyUtils.CHECK_BOX_QUESTION:
                showCheckBoxQuestion(context, surveyView, heroQuestion);
                break;
            case SurveyUtils.EXTERNAL_SURVEY:
                showExternalSurveyView(heroQuestion);
                break;
            case SurveyUtils.LABEL:
                break;
        }
    }

    private void showExternalSurveyView(StudyResponse.Surveys.Questions heroQuestion) {
        surveyTitleTextView.setText(heroQuestion.getContent().getLabel_text());
    }

    public static void setupBottomSheetBehavior(BottomSheetDialog bottomSheet) {
        bottomSheet.setOnShowListener(dialog -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
            View parentLayout = bottomSheetDialog.findViewById(R.id.design_bottom_sheet);
            if (parentLayout != null) {
                BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(parentLayout);
                // This disable bottom sheet draggable swipe
                behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    }
                });

                ViewGroup.LayoutParams layoutParams = parentLayout.getLayoutParams();
                layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
                parentLayout.setLayoutParams(layoutParams);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
    }

    public static BottomSheetDialog createSurveyBottomSheetDialog(Context context) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(context) {
            @SuppressLint("MissingSuperCall")
            @Override
            public void onBackPressed() {
                // Show a confirmation dialog when back is pressed
                handleDismissSurveyBottomSheet(context, this);
            }
        };
        bottomSheet.setContentView(R.layout.survey_questions_view);
        return bottomSheet;

    }

    public static void handleDismissSurveyBottomSheet(Context context, BottomSheetDialog surveyBottomSheet) {
        new AlertDialog.Builder(context).setMessage(R.string.are_you_sure_you_want_to_dismiss_the_survey).setPositiveButton(context.getString(R.string.rt_yes), (dialog, which) -> {
            surveyBottomSheet.hide();
        }).setNegativeButton(context.getString(R.string.rt_no), null).show();
    }

    public static void setupBottomSheetCloseButton(Context context, BottomSheetDialog bottomSheet) {
        ImageButton closeBtn = bottomSheet.findViewById(R.id.close_btn);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> handleDismissSurveyBottomSheet(context, bottomSheet));
        }
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

    public static void showTextInputQuestion(View view, StudyResponse.Surveys.Questions question) {
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
//        int extraPadding = (int) ((paddingInDp + 2) * scale + 0.5f);
        button.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx);
        button.setLayoutParams(params);

        // Remove default button drawable
        button.setButtonDrawable(null);

        // Set custom drawable to the right
        Drawable customCheckIcon = ContextCompat.getDrawable(ctx, R.drawable.survey_custom_button);
        if (customCheckIcon != null) {
            customCheckIcon.setBounds(customCheckIcon.getIntrinsicWidth(), 0, 0, customCheckIcon.getIntrinsicHeight());
        }
        int customCheckIconPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, ctx.getResources().getDisplayMetrics());
        button.setCompoundDrawablePadding(customCheckIconPadding);

        button.setCompoundDrawablesWithIntrinsicBounds(customCheckIcon, null, null, null);

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

    public void setSurveyTitleAndDescription(TextView surveyTitle, TextView surveyDescription, StudyResponse.Surveys firstSurvey) {
        if (surveyTitle != null) {
            surveyTitle.setText(firstSurvey.getStudy().getName());
        }
        if (surveyDescription != null) {
            surveyDescription.setText(firstSurvey.getStudy().getDescription());
        }
    }

    /**
     * Displays a TextView containing user information that will be shared with an external survey.
     * This method checks if there is any user information (in the form of survey questions) to be shared.
     * If available, it constructs a message that lists the shared information and separates each piece of information with a comma.
     *
     * @param context             The Context used to access resources.
     * @param embeddedDataList    A List of strings representing the user information that will be shared in the survey.
     * @param externalSurveyState With hero question or not
     */
    public void showSharedInfoDetailsTextView(Context context, List<String> embeddedDataList, int externalSurveyState) {
        if (embeddedDataList.isEmpty()) return;
        StringBuilder surveySharedInfo = new StringBuilder();

        for (int i = 0; i < embeddedDataList.size(); i++) {
            if (embeddedDataList.get(i).equals(SurveyWebViewActivity.USER_ID)) continue;
            surveySharedInfo.append(embeddedDataList.get(i).replace("_", " "));
            if (i < embeddedDataList.size() - 1) {
                surveySharedInfo.append(", ");
            }
        }
        if (SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION == externalSurveyState)
            hideQuestionsView();
        if (surveySharedInfo.length() > 0) {
            surveySharedInfo.insert(0, context.getString(R.string.sharing_survey_info_message));
            sharedInfoTextView.setVisibility(View.VISIBLE);
            sharedInfoTextView.setText(surveySharedInfo);
        }
    }

    /**
     * Creates an AlertDialog for dismissing a survey.
     * This dialog allows the user to either skip the survey, be reminded later, or cancel the action.
     *
     * @param context The context in which the dialog will be displayed.
     * @return The AlertDialog instance to be shown configured for the dismiss survey dialog.
     */
    public AlertDialog.Builder createDismissSurveyDialog(Context context) {
        AlertDialog.Builder dismissSurveyDialog = new AlertDialog.Builder(context).setTitle(R.string.dismiss_survey_dialog_title).setMessage(R.string.dismiss_survey_dialog_body).setNegativeButton(R.string.survey_dismiss_dialog_skip_this_survey, (dialog, which) -> {
            mSurveyActionsListener.onSkipSurvey();
        }).setNeutralButton(R.string.remind_me_latter, (dialog, which) -> {
            mSurveyActionsListener.onRemindMeLater();
        }).setPositiveButton(R.string.cancel, (dialog, which) -> {
            mSurveyActionsListener.onCancelSurvey();
        }).setCancelable(true);

        dismissSurveyDialog.create();
        return dismissSurveyDialog;
    }

    public static void hideSurveyView(View surveyView){
        if(surveyView == null) return;
        surveyView.setVisibility(View.GONE);
    }
    public void showProgress() {
        progressOverlay.setVisibility(View.VISIBLE);
    }

    public void hideProgress() {
        progressOverlay.setVisibility(View.GONE);
    }

    private void hideQuestionsView() {
        questionsContainer.setVisibility(View.GONE);
    }

}
