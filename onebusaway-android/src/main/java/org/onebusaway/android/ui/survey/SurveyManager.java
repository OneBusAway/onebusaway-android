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
import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse;
import org.onebusaway.android.io.request.survey.submit.ObaSubmitSurveyRequest;
import org.onebusaway.android.io.request.survey.submit.SubmitSurveyRequestListener;
import org.onebusaway.android.ui.survey.utils.SurveyUtils;
import org.onebusaway.android.ui.survey.utils.SurveyViewUtils;
import org.onebusaway.android.ui.survey.activities.SurveyWebViewActivity;
import org.onebusaway.android.ui.survey.adapter.SurveyAdapter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SurveyManager {
    private final Context context;
    private final StudyRequestListener studyRequestListener;
    private final SubmitSurveyRequestListener submitSurveyRequestListener;
    private StudyResponse mStudyResponse;
    // Holds the current survey index, determined by the survey location (stops, supported routes/stops, map)
    private int curSurveyIndex = 0;
    private Integer curSurveyID;
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


    // TODO CHANGE STATIC API URL TO SUPPORT DIFFERENT REGIONS

    public SurveyManager(Context context, Boolean fromArrivalsList, StudyRequestListener studyRequestListener, SubmitSurveyRequestListener submitSurveyRequestListener) {
        this.context = context;
        this.studyRequestListener = studyRequestListener;
        this.submitSurveyRequestListener = submitSurveyRequestListener;
        this.isVisibleOnStops = fromArrivalsList;
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
                SurveyViewUtils.showExternalSurveyButtons(surveyView);
                handleOpenExternalSurvey(surveyView, questionsList.get(0).getContent().getUrl());
                break;
            case SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION:
                externalSurveyUrl = questionsList.get(1).getContent().getUrl();
                SurveyViewUtils.showSharedInfoDetailsTextView(context, surveyView, questionsList.get(1).getContent().getEmbedded_data_fields());
                SurveyViewUtils.showHeroQuestionButtons(surveyView);
                handleNextButton(surveyView);
                break;

            default:
                SurveyViewUtils.showHeroQuestionButtons(surveyView);
                handleNextButton(surveyView);
                break;
        }

        StudyResponse.Surveys.Questions heroQuestion = questionsList.get(0);
        SurveyViewUtils.showQuestion(context, surveyView.getRootView(), heroQuestion, heroQuestion.getContent().getType());
    }


    private void setSurveyData() {
        if (!checkValidResponse() || curSurveyIndex == -1) return;
        if (isVisibleOnStops) {
            arrivalsList.addHeaderView(surveyView);
        }
        updateSurveyData();
    }

    public void submitSurveyAnswers(StudyResponse.Surveys survey, boolean heroQuestion) {
        // TODO ADD PROGRESS BAR
        String userIdentifier = SurveyUtils.getUserUUID(context);
        String apiUrl = context.getString(R.string.submit_survey_api_url);
        if (!heroQuestion && updateSurveyPath != null) {
            apiUrl += updateSurveyPath;
        }
        JSONArray surveyResponseBody;
        if (heroQuestion) {
            surveyResponseBody = (SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions().get(0), surveyView.getRootView()));
        } else {
            surveyResponseBody = (SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions()));
        }
        // Empty questions
        if (surveyResponseBody == null) {
            Toast.makeText(context, context.getString(R.string.please_fill_all_the_questions), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("SurveyResponseBody", surveyResponseBody.toString());
        ObaSubmitSurveyRequest request = new ObaSubmitSurveyRequest.Builder(context, apiUrl).setUserIdentifier(userIdentifier).setSurveyId(survey.getId()).setResponses(surveyResponseBody).setListener(submitSurveyRequestListener).build();

        new Thread(request::call).start();
    }

    public void handleNextButton(View view) {
        Button nextBtn = view.findViewById(R.id.nextBtn);
        nextBtn.setOnClickListener(view1 -> {
            submitSurveyAnswers(mStudyResponse.getSurveys().get(curSurveyIndex), true);
        });
    }

    public void handleOpenExternalSurvey(View view, String url) {
        Button externalSurveyBtn = view.findViewById(R.id.openExternalSurveyBtn);
        externalSurveyBtn.setOnClickListener(view1 -> {
            openExternalSurvey(url);
        });
    }


    private void openExternalSurvey(String url) {
        // TODO handle passing embedded data
        Intent intent = new Intent(context, SurveyWebViewActivity.class);
        intent.putExtra("url", url);
        context.startActivity(intent);
        handleCompleteSurvey();
    }


    public void handleSubmitSurveyButton(View view) {
        submitSurveyButton = view.findViewById(R.id.submit_btn);
        submitSurveyButton.setOnClickListener(v -> submitSurveyAnswers(mStudyResponse.getSurveys().get(curSurveyIndex), false));
    }

    public void initSurveyAdapter(Context context, RecyclerView recyclerView) {
        List<StudyResponse.Surveys.Questions> surveyQuestions = mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions();
        surveyQuestions.remove(0); // Remove the hero question
        SurveyAdapter surveyAdapter = new SurveyAdapter(context, surveyQuestions);
        recyclerView.setAdapter(surveyAdapter);
    }

    public void showAllSurveyQuestions() {
        surveyBottomSheet = SurveyViewUtils.createSurveyBottomSheetDialog(context);
        initSurveyQuestionsBottomSheet(context);
        SurveyViewUtils.setupBottomSheetBehavior(surveyBottomSheet);
        SurveyViewUtils.setupBottomSheetCloseButton(surveyBottomSheet);
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

    public void initSurveyArrivalsList(ListView arrivalsList) {
        this.arrivalsList = arrivalsList;
    }

    private void initSurveyQuestionsBottomSheet(Context context) {
        if (!checkValidResponse()) return;

        StudyResponse.Surveys firstSurvey = mStudyResponse.getSurveys().get(curSurveyIndex);
        if (!isSurveyValid(firstSurvey)) {
            return;
        }

        surveyRecycleView = surveyBottomSheet.findViewById(R.id.recycleView);
        submitSurveyButton = surveyBottomSheet.findViewById(R.id.submit_btn);
        TextView surveyTitle = surveyBottomSheet.findViewById(R.id.surveyTitle);
        TextView surveyDescription = surveyBottomSheet.findViewById(R.id.surveyDescription);

        setSurveyTitleAndDescription(surveyTitle, surveyDescription, firstSurvey);
        initRecyclerView(context);
    }

    private Boolean checkValidResponse() {
        return mStudyResponse != null && mStudyResponse.getSurveys() != null && !mStudyResponse.getSurveys().isEmpty();
    }

    private Boolean isSurveyValid(StudyResponse.Surveys firstSurvey) {
        return firstSurvey != null && firstSurvey.getStudy() != null;
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
        if (surveyRecycleView != null) {
            surveyRecycleView.setLayoutManager(new LinearLayoutManager(context));
        }
    }


    public void onSurveyResponseReceived(StudyResponse response) {
        if (response == null) return;
        mStudyResponse = response;
        curSurveyIndex = SurveyUtils.getCurrentSurveyIndex(response, context, isVisibleOnStops, currentStop);
        Log.d("CurSurveyIndex", curSurveyIndex + " ");
        if (curSurveyIndex == -1) return;
        curSurveyID = mStudyResponse.getSurveys().get(curSurveyIndex).getId();
        setSurveyData();
    }

    public void onSurveyFail() {
        Log.d("SurveyManager", "Survey Fail");
    }

    public void onSubmitSurveyResponseReceived(SubmitSurveyResponse response) {
        // Switch back to the main thread to update UI elements
        ContextCompat.getMainExecutor(context).execute(() -> {
            if (updateSurveyPath != null) {
                surveyBottomSheet.hide();
                Toast.makeText(context, R.string.submitted_successfully, Toast.LENGTH_LONG).show();
                return;
            }
            // Mark survey as done
            handleCompleteSurvey();
            // Check if the external survey needs to be opened after responding to a hero question
            if (externalSurveyResult == SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION) {
                handleExternalSurvey();
                return;
            }
            // Don't show survey question bottom sheet if we don't have another questions
            if (haveOnlyHeroQuestion()) return;
            updateSurveyPath = response.getSurveyResponse().getId();
            // Display the bottom sheet containing survey questions
            showAllSurveyQuestions();
            initSurveyAdapter(context, surveyRecycleView);
        });
    }


    /**
     * Handles the external survey process after responding to a hero question.
     */
    private void handleExternalSurvey() {
        if (externalSurveyUrl == null) return;

        externalSurveyResult = SurveyUtils.DEFAULT_SURVEY;
        openExternalSurvey(externalSurveyUrl);
    }

    /**
     * Mark current survey as done
     */
    public void handleCompleteSurvey() {
        SurveyUtils.markSurveyAsCompleted(context, String.valueOf(curSurveyID));
        // Remove the hero question view
        if (isVisibleOnStops) arrivalsList.removeHeaderView(surveyView);
    }

    public void onSubmitSurveyFail() {
        Log.d("SubmitSurveyFail", curSurveyID + "");
    }

    public void setCurrentStop(ObaStop stop) {
        if (stop == null || !isVisibleOnStops) return;
        this.currentStop = stop;
        Log.d("CurrentStopID", currentStop.getId() + " ");
        Log.d("CurrentStopRoutes", Arrays.toString(currentStop.getRouteIds()));
    }
}
