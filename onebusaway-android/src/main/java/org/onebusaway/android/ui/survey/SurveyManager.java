package org.onebusaway.android.ui.survey;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.survey.ObaStudyRequest;
import org.onebusaway.android.io.request.survey.StudyRequestListener;
import org.onebusaway.android.io.request.survey.StudyRequestTask;
import org.onebusaway.android.io.request.survey.SurveyListener;
import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse;
import org.onebusaway.android.io.request.survey.submit.ObaSubmitSurveyRequest;
import org.onebusaway.android.io.request.survey.submit.SubmitSurveyRequestListener;
import org.onebusaway.android.ui.survey.utils.SurveyDbHelper;
import org.onebusaway.android.ui.survey.utils.SurveyUtils;
import org.onebusaway.android.ui.survey.utils.SurveyViewUtils;
import org.onebusaway.android.ui.survey.activities.SurveyWebViewActivity;
import org.onebusaway.android.ui.survey.adapter.SurveyAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SurveyManager extends SurveyViewUtils implements SurveyActionsListener {
    private final Context context;
    private final StudyRequestListener studyRequestListener;
    private final SubmitSurveyRequestListener submitSurveyRequestListener;
    private StudyResponse mStudyResponse;
    // Holds the current survey index, determined by the survey location (stops, supported routes/stops, map)
    private int curSurveyIndex = 0;
    // Holds current survey ID
    private Integer curSurveyID;
    // Holds the survey view
    private final View surveyView;
    // Bottom sheet RecyclerView for surveys
    private RecyclerView bottomSheetSurveyRecyclerView;
    // Button to submit the survey from the bottom sheet
    private Button bottomSheetSubmitSurveyButton;
    // Stores the update path after the hero question is submitted
    private String updateSurveyPath;
    // Bottom sheet dialog for displaying the remaining survey questions
    private BottomSheetDialog surveyBottomSheet;
    // true if from arrivals list false if survey in the map
    private final Boolean isVisibleOnStops;
    // Cur stop if the survey visible on the stops
    private ObaStop currentStop;
    // Stores external survey URL
    private String externalSurveyUrl = null;
    // true if we have a question as an external survey
    private Integer externalSurveyResult = 0;

    public SurveyManager(Context context, View surveyView, Boolean isVisibleOnStops, SurveyListener surveyListener) {
        super(surveyView, context, surveyListener);
        this.context = context;
        this.studyRequestListener = surveyListener;
        this.submitSurveyRequestListener = surveyListener;
        this.surveyView = surveyView;
        this.isVisibleOnStops = isVisibleOnStops;
    }

    public void requestSurveyData() {
        // Indicates whether the option to show available studies is enabled in preferences
        boolean areStudiesEnabled = Application.getPrefs().getBoolean(context.getString(R.string.preference_key_show_available_studies), true);
        boolean shouldShowSurvey = SurveyUtils.shouldShowSurveyView(context, isVisibleOnStops);

        if (!areStudiesEnabled || !shouldShowSurvey) return;

        ObaStudyRequest surveyRequest = ObaStudyRequest.newRequest(context);
        StudyRequestTask task = new StudyRequestTask(studyRequestListener);
        task.execute(surveyRequest);
        Log.d("SurveyManager", "Survey requested");
    }

    private void updateSurveyUI() {
        List<StudyResponse.Surveys.Questions> questionsList = getCurrentSurvey().getQuestions();
        externalSurveyResult = SurveyUtils.checkExternalSurvey(questionsList);
        Log.d("SurveyState", "External survey result: " + externalSurveyResult);

        handleSurveyResult(externalSurveyResult, questionsList);
        // Show the hero question
        showHeroQuestion(context, questionsList.get(0));
    }

    private void handleSurveyResult(int externalSurveyResult, List<StudyResponse.Surveys.Questions> questionsList) {
        switch (externalSurveyResult) {
            case SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION:
                handleExternalSurveyWithoutHero(questionsList);
                break;
            case SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION:
                handleExternalSurveyWithHero(questionsList);
                break;
            default:
                handleDefaultSurvey();
                break;
        }
    }

    private void handleExternalSurveyWithoutHero(List<StudyResponse.Surveys.Questions> questionsList) {
        showSharedInfoDetailsTextView(context, questionsList.get(0).getContent().getEmbedded_data_fields(), SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION);
        showExternalSurveyButtons();
        handleOpenExternalSurvey(surveyView, questionsList.get(0).getContent().getUrl());
    }

    private void handleExternalSurveyWithHero(List<StudyResponse.Surveys.Questions> questionsList) {
        externalSurveyUrl = questionsList.get(1).getContent().getUrl();
        showSharedInfoDetailsTextView(context, questionsList.get(1).getContent().getEmbedded_data_fields(), SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION);
        showHeroQuestionButtons();
        handleNextButton();
    }

    private void handleDefaultSurvey() {
        showHeroQuestionButtons();
        handleNextButton();
    }

    private void addSurveyView() {
        if (!SurveyUtils.checkValidResponse(mStudyResponse) || curSurveyIndex == -1) return;
        if (isVisibleOnStops) {
            arrivalsList.addHeaderView(surveyView);
        } else {
            surveyView.setVisibility(View.VISIBLE);
        }
        updateSurveyUI();
    }

    private void submitSurveyAnswers(StudyResponse.Surveys survey, View bottomSheetProgress, boolean heroQuestion) {
        if (!validateSurveyAnswers(survey, heroQuestion)) return;

        String userID = SurveyPreferences.getUserUUID(context);
        String apiUrl = getSubmitSurveyApiUrl(heroQuestion);

        // Reset `launchesUntilSurveyShown` for this session to trigger showing the next survey after the specified launch count
        SurveyUtils.launchesUntilSurveyShown = Integer.MAX_VALUE;

        if (bottomSheetProgress != null) {
            bottomSheetProgress.setVisibility(View.VISIBLE);
        }
        JSONArray surveyResponseBody = getFinalSurveyAnswersRequestBody(survey, heroQuestion);
        sendSurveySubmission(apiUrl, userID, survey.getId(), surveyResponseBody);
    }

    private JSONArray getFinalSurveyAnswersRequestBody(StudyResponse.Surveys survey, boolean heroQuestion) {
        if (heroQuestion) {
            return SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions().get(0), surveyView);
        } else {
            return SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions());
        }
    }

    private boolean validateSurveyAnswers(StudyResponse.Surveys survey, boolean heroQuestion) {
        JSONArray surveyResponseBody = getFinalSurveyAnswersRequestBody(survey, heroQuestion);
        if (surveyResponseBody == null) {
            Toast.makeText(context, context.getString(R.string.please_complete_all_the_questions), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String getSubmitSurveyApiUrl(boolean isHeroQuestion) {
        String apiUrl = context.getString(R.string.submit_survey_api_url);
        if (!isHeroQuestion && updateSurveyPath != null) {
            apiUrl += updateSurveyPath;
        }
        return apiUrl;
    }

    private void sendSurveySubmission(String apiUrl, String userIdentifier, int surveyId, JSONArray requestBody) {
        showProgress();
        ObaSubmitSurveyRequest request = new ObaSubmitSurveyRequest.Builder(context, apiUrl)
                .setUserIdentifier(userIdentifier)
                .setSurveyId(surveyId)
                .setResponses(requestBody)
                .setListener(submitSurveyRequestListener).build();
        new Thread(request::call).start();
    }

    private void handleNextButton() {
        nextButton.setOnClickListener(v -> submitSurveyAnswers(getCurrentSurvey(), null, true));
    }

    private void handleOpenExternalSurvey(View view, String url) {
        openExternalSurveyButton.setOnClickListener(view1 -> openExternalSurvey(url));
    }

    private void openExternalSurvey(String url) {
        ArrayList<String> embeddedDataList = getEmbeddedDataList();
        Intent intent = createOpenExternalSurveyIntent(url, embeddedDataList);
        context.startActivity(intent);
        handleCompleteSurvey();
    }

    private ArrayList<String> getEmbeddedDataList() {
        int questionIndex = (externalSurveyResult == SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION) ? 1 : 0;
        return getCurrentSurvey().getQuestions().get(questionIndex).getContent().getEmbedded_data_fields();
    }

    private Intent createOpenExternalSurveyIntent(String url, ArrayList<String> embeddedDataList) {
        Intent intent = new Intent(context, SurveyWebViewActivity.class);
        intent.putExtra("url", url);

        if (isVisibleOnStops && currentStop != null) {
            intent.putExtra("stop_id", currentStop.getId());
            // Add logic to determine if we should send the first route or all routes (TODO: clarify)
            if (currentStop.getRouteIds().length > 0) {
                intent.putExtra("route_id", currentStop.getRouteIds()[0]);
            }
        }
        if (SurveyUtils.isValidEmbeddedDataList(embeddedDataList)) {
            intent.putStringArrayListExtra("embedded_data", embeddedDataList);
        }
        return intent;
    }


    private void setSubmitSurveyButton() {
        bottomSheetSubmitSurveyButton = surveyBottomSheet.findViewById(R.id.submit_btn);
        View bottomSheetProgress = surveyBottomSheet.findViewById(R.id.surveyProgress);
        bottomSheetSubmitSurveyButton.setOnClickListener(v -> submitSurveyAnswers(getCurrentSurvey(), bottomSheetProgress, false));
    }

    private void initSurveyAdapter(Context context, RecyclerView recyclerView) {
        List<StudyResponse.Surveys.Questions> surveyQuestions = getCurrentSurvey().getQuestions();
        surveyQuestions.remove(0); // Remove the hero question
        SurveyAdapter surveyAdapter = new SurveyAdapter(context, surveyQuestions);
        recyclerView.setAdapter(surveyAdapter);
    }

    private void showAllSurveyQuestions() {
        surveyBottomSheet = SurveyViewUtils.createSurveyBottomSheetDialog(context);
        initSurveyQuestionsBottomSheet(context);
        SurveyViewUtils.setupBottomSheetBehavior(surveyBottomSheet);
        SurveyViewUtils.setupBottomSheetCloseButton(context, surveyBottomSheet);
        setSubmitSurveyButton();
        surveyBottomSheet.show();
    }

    private boolean haveOnlyHeroQuestion() {
        int questionsSize = getCurrentSurvey().getQuestions().size();
        return questionsSize == 1;
    }

    private void initSurveyQuestionsBottomSheet(Context context) {
        if (!SurveyUtils.checkValidResponse(mStudyResponse)) return;

        if (!SurveyUtils.isSurveyValid(getCurrentSurvey())) {
            return;
        }

        bottomSheetSurveyRecyclerView = surveyBottomSheet.findViewById(R.id.recycleView);
        bottomSheetSubmitSurveyButton = surveyBottomSheet.findViewById(R.id.submit_btn);
        TextView surveyTitle = surveyBottomSheet.findViewById(R.id.surveyTitle);
        TextView surveyDescription = surveyBottomSheet.findViewById(R.id.surveyDescription);

        setSurveyTitleAndDescription(surveyTitle, surveyDescription, getCurrentSurvey());
        initSurveyBottomSheetRecyclerView(context);
    }

    private void initSurveyBottomSheetRecyclerView(Context context) {
        if (bottomSheetSurveyRecyclerView == null) return;
        bottomSheetSurveyRecyclerView.setLayoutManager(new LinearLayoutManager(context));
    }


    public void onSurveyResponseReceived(StudyResponse response) {
        if (response == null) return;
        mStudyResponse = response;
        curSurveyIndex = SurveyUtils.getCurrentSurveyIndex(response, context, isVisibleOnStops, currentStop);

        Log.d("CurSurveyIndex", curSurveyIndex + " ");

        if (curSurveyIndex == -1) return;
        curSurveyID = getCurrentSurvey().getId();
        addSurveyView();
    }

    public void onSurveyResponseFail() {
        Log.d("SurveyManager", "Survey Fail");
    }

    public void onSubmitSurveyResponseReceived(SubmitSurveyResponse response) {
        // Switch back to the main thread to update UI elements
        ContextCompat.getMainExecutor(context).execute(() -> {
            hideProgress();
            // User answered the hero question and completed the survey
            if (updateSurveyPath != null) {
                surveyBottomSheet.hide();
                Toast.makeText(context, R.string.submitted_successfully, Toast.LENGTH_LONG).show();
                return;
            }
            // Mark survey as completed
            handleCompleteSurvey();
            // Check if the external survey needs to be opened after responding to a hero question
            if (externalSurveyResult == SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION) {
                handleExternalSurvey();
                return;
            }
            // Don't show survey question bottom sheet if we don't have another questions
            if (haveOnlyHeroQuestion()) return;
            // Set the URL Update path for submitting the remaining questions
            updateSurveyPath = response.getSurveyResponse().getId();
            // Display the bottom sheet containing survey questions
            showAllSurveyQuestions();
            // Init survey question list
            initSurveyAdapter(context, bottomSheetSurveyRecyclerView);
        });
    }

    private StudyResponse.Surveys getCurrentSurvey() {
        return mStudyResponse.getSurveys().get(curSurveyIndex);
    }


    /**
     * Handles the external survey open process after responding to a hero question.
     */

    private void handleExternalSurvey() {
        if (externalSurveyUrl == null) return;
        openExternalSurvey(externalSurveyUrl);
        // Reset state to the default survey type
        externalSurveyResult = SurveyUtils.DEFAULT_SURVEY;
    }

    /**
     * Marks the current survey as completed.
     * If the survey was visible in the stops view, it removes the corresponding hero question view from the arrivals list.
     */
    private void handleCompleteSurvey() {
        SurveyDbHelper.markSurveyAsCompletedOrSkipped(context, getCurrentSurvey(), SurveyDbHelper.SURVEY_COMPLETED);
        // Remove the survey view if it's visible on the arrivals list or map
        handleRemoveSurvey();
    }

    public void onSubmitSurveyFail() {
        hideProgress();
        Log.d("SubmitSurveyFail", curSurveyID + "");
    }

    /**
     * Sets the current stop only if the survey is visible at the stops.
     *
     * @param stop The current stop
     */
    public void setCurrentStop(ObaStop stop) {
        if (stop == null || !isVisibleOnStops) return;
        this.currentStop = stop;
        Log.d("CurrentStopID", currentStop.getId() + " ");
        Log.d("CurrentStopRoutes", Arrays.toString(currentStop.getRouteIds()));
    }

    /**
     * Removes the survey view from the arrivals list header if it is visible on stops.
     */
    private void handleRemoveSurvey() {
        if (arrivalsList != null && isVisibleOnStops) {
            arrivalsList.removeHeaderView(surveyView);
        }

        if (!isVisibleOnStops) {
            surveyView.setVisibility(View.GONE);
        }
        // Reset `launchesUntilSurveyShown` for this session to trigger showing the next survey after the specified launch count
        SurveyUtils.launchesUntilSurveyShown = Integer.MAX_VALUE;
    }

    /**
     * Handles skipping the survey.
     * The survey will be marked as skipped in the database with a state value of 2
     * indicating it was not completed by the user and should be skipped.
     */
    @Override
    public void onSkipSurvey() {
        handleRemoveSurvey();
        SurveyDbHelper.markSurveyAsCompletedOrSkipped(context, getCurrentSurvey(), SurveyDbHelper.SURVEY_SKIPPED);
    }

    /**
     * Handles the logic for postponing the survey reminder.
     */
    @Override
    public void onRemindMeLater() {
        SurveyUtils.remindUserLater(context);
        handleRemoveSurvey();
    }

    @Override
    public void onCancelSurvey() {
        // By default will dismiss the survey dialog
    }
}
