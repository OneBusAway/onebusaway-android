package org.onebusaway.android.ui.survey;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.onebusaway.android.R;
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
import java.util.Objects;

public class SurveyManager implements SurveyActionsListener {
    private final Context context;
    private final StudyRequestListener studyRequestListener;
    private final SubmitSurveyRequestListener submitSurveyRequestListener;
    private StudyResponse mStudyResponse;
    // Holds the current survey index, determined by the survey location (stops, supported routes/stops, map)
    private int curSurveyIndex = 0;
    // Holds current survey ID
    private Integer curSurveyID;
    // Holds the survey view
    private View surveyView;
    private RecyclerView surveyRecycleView;
    private Button submitSurveyButton;
    // Stores the update path after the hero question is submitted
    private String updateSurveyPath;
    private ListView arrivalsList;
    private BottomSheetDialog surveyBottomSheet;
    // true if from arrivals list false if survey in the map
    private final Boolean isVisibleOnStops;
    // Cur stop if the survey visible on the stops
    private ObaStop currentStop;
    // Stores external survey URL
    private String externalSurveyUrl = null;
    // true if we have a question as an external survey
    private Integer externalSurveyResult = 0;


    public SurveyManager(Context context,SurveyListener surveyListener , Boolean isVisibleOnStops) {
        this.context = context;
        this.studyRequestListener = surveyListener;
        this.submitSurveyRequestListener = surveyListener;
        this.isVisibleOnStops = isVisibleOnStops;
        setupSurveyDismissDialog();
    }

    public void requestSurveyData() {
        ObaStudyRequest surveyRequest = ObaStudyRequest.newRequest();
        StudyRequestTask task = new StudyRequestTask(studyRequestListener);
        task.execute(surveyRequest);
        Log.d("SurveyManager", "Survey requested");
    }

    private void updateSurveyData() {
        List<StudyResponse.Surveys.Questions> questionsList = mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions();

        externalSurveyResult = SurveyUtils.checkExternalSurvey(questionsList);
        Log.d("SurveyState", "External survey result: " + externalSurveyResult);

        switch (externalSurveyResult) {
            case SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION:
                SurveyViewUtils.showSharedInfoDetailsTextView(context, surveyView, questionsList.get(0).getContent().getEmbedded_data_fields());
                SurveyViewUtils.showExternalSurveyButtons(context, surveyView);
                handleOpenExternalSurvey(surveyView, questionsList.get(0).getContent().getUrl());
                break;
            case SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION:
                externalSurveyUrl = questionsList.get(1).getContent().getUrl();
                SurveyViewUtils.showSharedInfoDetailsTextView(context, surveyView, questionsList.get(1).getContent().getEmbedded_data_fields());
                SurveyViewUtils.showHeroQuestionButtons(context, surveyView);
                handleNextButton(surveyView);
                break;

            default:
                SurveyViewUtils.showHeroQuestionButtons(context, surveyView);
                handleNextButton(surveyView);
                break;
        }

        StudyResponse.Surveys.Questions heroQuestion = questionsList.get(0);
        SurveyViewUtils.showQuestion(context, surveyView.getRootView(), heroQuestion, heroQuestion.getContent().getType());
    }


    private void addSurveyView() {
        if (!checkValidResponse() || curSurveyIndex == -1) return;
        if (isVisibleOnStops) {
            arrivalsList.addHeaderView(surveyView);
        } else {
            surveyView.setVisibility(View.VISIBLE);
        }
        updateSurveyData();
    }

    public void submitSurveyAnswers(StudyResponse.Surveys survey, boolean heroQuestion) {
        // TODO ADD PROGRESS BAR
        String userIdentifier = SurveyPreferences.getUserUUID(context);
        String submitSurveyAPIURL = context.getString(R.string.submit_survey_api_url);
        // Add the update path for updating remaining survey questions
        if (!heroQuestion && updateSurveyPath != null) {
            submitSurveyAPIURL += updateSurveyPath;
        }
        JSONArray surveyResponseBody;
        if (heroQuestion) {
            surveyResponseBody = (SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions().get(0), surveyView));
        } else {
            surveyResponseBody = (SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions()));
        }
        // Empty questions
        if (surveyResponseBody == null) {
            Toast.makeText(context, context.getString(R.string.please_fill_all_the_questions), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("SurveyResponseBody", surveyResponseBody.toString());
        ObaSubmitSurveyRequest request = new ObaSubmitSurveyRequest.Builder(context, submitSurveyAPIURL).setUserIdentifier(userIdentifier).setSurveyId(survey.getId()).setResponses(surveyResponseBody).setListener(submitSurveyRequestListener).build();

        new Thread(request::call).start();
    }

    private void handleNextButton(View view) {
        Button nextBtn = view.findViewById(R.id.nextBtn);
        nextBtn.setOnClickListener(view1 -> {
            submitSurveyAnswers(mStudyResponse.getSurveys().get(curSurveyIndex), true);
        });
    }

    private void handleOpenExternalSurvey(View view, String url) {
        Button externalSurveyBtn = view.findViewById(R.id.openExternalSurveyBtn);
        externalSurveyBtn.setOnClickListener(view1 -> {
            openExternalSurvey(url);
        });
    }


    private void openExternalSurvey(String url) {
        ArrayList<String> embeddedDataList = new ArrayList<>();
        StudyResponse.Surveys curSurvey = mStudyResponse.getSurveys().get(curSurveyIndex);
        switch (externalSurveyResult) {
            case SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION:
                embeddedDataList = curSurvey.getQuestions().get(0).getContent().getEmbedded_data_fields();
                break;
            case SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION:
                embeddedDataList = curSurvey.getQuestions().get(1).getContent().getEmbedded_data_fields();
                break;
            default:
                break;
        }


        Intent intent = new Intent(context, SurveyWebViewActivity.class);
        intent.putExtra("url", url);
        if(isVisibleOnStops && currentStop != null) {
            intent.putExtra("stop_id", currentStop.getId());
            if(currentStop.getRouteIds().length > 0){
                intent.putExtra("route_id", currentStop.getRouteIds()[0]);
            }
        }
        intent.putStringArrayListExtra("embedded_data", embeddedDataList);
        context.startActivity(intent);
        handleCompleteSurvey();
    }


    private void handleSubmitSurveyButton(View view) {
        submitSurveyButton = view.findViewById(R.id.submit_btn);
        submitSurveyButton.setOnClickListener(v -> submitSurveyAnswers(mStudyResponse.getSurveys().get(curSurveyIndex), false));
    }

    private void initSurveyAdapter(Context context, RecyclerView recyclerView) {
        List<StudyResponse.Surveys.Questions> surveyQuestions = mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions();
        surveyQuestions.remove(0); // Remove the hero question
        SurveyAdapter surveyAdapter = new SurveyAdapter(context, surveyQuestions);
        recyclerView.setAdapter(surveyAdapter);
    }

    private void showAllSurveyQuestions() {
        surveyBottomSheet = SurveyViewUtils.createSurveyBottomSheetDialog(context);
        initSurveyQuestionsBottomSheet(context);
        SurveyViewUtils.setupBottomSheetBehavior(surveyBottomSheet);
        SurveyViewUtils.setupBottomSheetCloseButton(context, surveyBottomSheet);
        handleSubmitSurveyButton(Objects.requireNonNull(surveyBottomSheet.findViewById(R.id.submit_btn)));
        surveyBottomSheet.show();
    }

    private boolean haveOnlyHeroQuestion() {
        int questionsSize = mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions().size();
        return questionsSize == 1;
    }

    @SuppressLint("InflateParams")
    public void initSurveyArrivalsHeaderView(LayoutInflater inflater) {
        surveyView = inflater.inflate(R.layout.item_survey, null);
    }

    public void setSurveyView(View surveyView) {
        this.surveyView = surveyView;
    }

    public void initSurveyArrivalsList(ListView arrivalsList) {
        this.arrivalsList = arrivalsList;
    }

    private void initSurveyQuestionsBottomSheet(Context context) {
        if (!checkValidResponse()) return;

        StudyResponse.Surveys curSurvey = mStudyResponse.getSurveys().get(curSurveyIndex);
        if (!isSurveyValid(curSurvey)) {
            return;
        }

        surveyRecycleView = surveyBottomSheet.findViewById(R.id.recycleView);
        submitSurveyButton = surveyBottomSheet.findViewById(R.id.submit_btn);
        TextView surveyTitle = surveyBottomSheet.findViewById(R.id.surveyTitle);
        TextView surveyDescription = surveyBottomSheet.findViewById(R.id.surveyDescription);

        setSurveyTitleAndDescription(surveyTitle, surveyDescription, curSurvey);
        initRecyclerView(context);
    }

    private Boolean checkValidResponse() {
        return mStudyResponse != null && mStudyResponse.getSurveys() != null && !mStudyResponse.getSurveys().isEmpty();
    }

    private Boolean isSurveyValid(StudyResponse.Surveys survey) {
        return survey != null && survey.getStudy() != null;
    }

    private void setSurveyTitleAndDescription(TextView surveyTitle, TextView surveyDescription, StudyResponse.Surveys firstSurvey) {
        if (surveyTitle != null) {
            surveyTitle.setText(firstSurvey.getStudy().getName());
        }
        if (surveyDescription != null) {
            surveyDescription.setText(firstSurvey.getStudy().getDescription());
        }
    }

    private void initRecyclerView(Context context) {
        if (surveyRecycleView == null) return;
        surveyRecycleView.setLayoutManager(new LinearLayoutManager(context));
    }


    public void onSurveyResponseReceived(StudyResponse response) {
        if (response == null) return;
        mStudyResponse = response;
        curSurveyIndex = SurveyUtils.getCurrentSurveyIndex(response, context, isVisibleOnStops, currentStop);

        Log.d("CurSurveyIndex", curSurveyIndex + " ");

        if (curSurveyIndex == -1) return;
        curSurveyID = mStudyResponse.getSurveys().get(curSurveyIndex).getId();
        addSurveyView();
    }

    public void onSurveyFail() {
        Log.d("SurveyManager", "Survey Fail");
    }

    public void onSubmitSurveyResponseReceived(SubmitSurveyResponse response) {
        // Switch back to the main thread to update UI elements
        ContextCompat.getMainExecutor(context).execute(() -> {
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
            initSurveyAdapter(context, surveyRecycleView);
        });
    }


    /**
     * Handles the external survey open process after responding to a hero question.
     */

    private void handleExternalSurvey() {
        if (externalSurveyUrl == null) return;
        // Reset state to the default survey type
        externalSurveyResult = SurveyUtils.DEFAULT_SURVEY;

        openExternalSurvey(externalSurveyUrl);
    }

    /**
     * Marks the current survey as completed.
     * If the survey was visible in the stops view, it removes the corresponding hero question view from the arrivals list.
     */
    private void handleCompleteSurvey() {
        StudyResponse.Surveys currentSurvey = mStudyResponse.getSurveys().get(curSurveyIndex);
        SurveyDbHelper.markSurveyAsCompletedOrSkipped(context, currentSurvey, SurveyDbHelper.SURVEY_COMPLETED);
        // Remove the survey view if it's visible on the arrivals list or map
        handleRemoveSurvey();
    }

    public void onSubmitSurveyFail() {
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
    }

    /**
     * Initializes and displays the survey dismiss dialog.
     * Sets the listener for survey actions callbacks
     */
    private void setupSurveyDismissDialog() {
        SurveyDialogActions.setDialogActionListener(this);
        SurveyViewUtils.createDismissSurveyDialog(context);
    }

    /**
     * Handles skipping the survey.
     * The survey will be marked as skipped in the database with a state value of 2
     * indicating it was not completed by the user and should be skipped.
     */
    @Override
    public void onSkipSurvey() {
        handleRemoveSurvey();
        StudyResponse.Surveys currentSurvey = mStudyResponse.getSurveys().get(curSurveyIndex);
        SurveyDbHelper.markSurveyAsCompletedOrSkipped(context, currentSurvey, SurveyDbHelper.SURVEY_SKIPPED);
    }

    @Override
    public void onRemindMeLater() {
        // TODO handle reminder button
        handleRemoveSurvey();
    }

    @Override
    public void onCancelSurvey() {
        // By default will dismiss the survey dialog
    }
}
