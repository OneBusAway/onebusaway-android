package org.onebusaway.android.ui.survey;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
    private final Boolean fromArrivalsList;

    // TODO CHANGE STATIC API URL TO SUPPORT DIFFERENT REGIONS
    public SurveyManager(Context context, Boolean fromArrivalsList, StudyRequestListener studyRequestListener, SubmitSurveyRequestListener submitSurveyRequestListener) {
        this.context = context;
        this.studyRequestListener = studyRequestListener;
        this.submitSurveyRequestListener = submitSurveyRequestListener;
        this.fromArrivalsList = fromArrivalsList;
    }

    public void requestSurveyData() {
        ObaStudyRequest surveyRequest = ObaStudyRequest.newRequest();
        StudyRequestTask task = new StudyRequestTask(studyRequestListener);
        task.execute(surveyRequest);
        Log.d("SurveyManager", "Survey requested");
    }

    private void updateSurveyData() {
        List<StudyResponse.Surveys.Questions> questionsList = mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions();

        if (questionsList.isEmpty()) {
            // TODO REMOVE THIS BEFORE RELEASING WE CAN'T HAVE EMPTY QUESTION (for testing purposes).
            handleCompleteSurvey();
            Log.d("SurveyState", "No questions found, survey marked as done.");
            arrivalsList.removeHeaderView(surveyView);
            return;
        }

        int externalSurveyResult = SurveyUtils.checkExternalSurvey(questionsList);
        Log.d("SurveyState", "External survey result: " + externalSurveyResult);

        switch (externalSurveyResult) {
            case SurveyUtils.EXTERNAL_SURVEY_WITHOUT_HERO_QUESTION:
                SurveyViewUtils.showExternalSurveyButtons(surveyView);
                handleOpenExternalSurvey(surveyView, questionsList.get(0).getContent().getUrl());
                break;
            case SurveyUtils.EXTERNAL_SURVEY_WITH_HERO_QUESTION:
                SurveyViewUtils.showHeroQuestionButtons(surveyView);
                handleNextButton(surveyView, externalSurveyResult, questionsList.size() > 1 ? questionsList.get(1).getContent().getUrl() : "");
                break;

            default:
                SurveyViewUtils.showHeroQuestionButtons(surveyView);
                handleNextButton(surveyView, externalSurveyResult, "");
                break;
        }

        StudyResponse.Surveys.Questions heroQuestion = questionsList.get(0);
        SurveyViewUtils.showQuestion(context, surveyView.getRootView(), heroQuestion, heroQuestion.getContent().getType());
    }


    private void setSurveyData() {
        if (!checkValidResponse() || curSurveyIndex == -1) return;
        if (fromArrivalsList){
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

    public void handleNextButton(View view, int externalSurveyResult, String externalSurveyUrl) {
        Button nextBtn = view.findViewById(R.id.nextBtn);
        nextBtn.setOnClickListener(view1 -> {
            if (externalSurveyResult == 2) {
                showExternalSurveyDialog(externalSurveyUrl);
            } else {
                submitSurveyAnswers(mStudyResponse.getSurveys().get(curSurveyIndex), true);
            }
        });
    }

    public void handleOpenExternalSurvey(View view, String url) {
        Button externalSurveyBtn = view.findViewById(R.id.openExternalSurveyBtn);
        externalSurveyBtn.setOnClickListener(view1 -> {
            showExternalSurveyDialog(url);
        });
    }

    private void showExternalSurveyDialog(String url) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        // TODO remove external survey dialog
        builder.setTitle("OneBusAway Survey");
        builder.setMessage("Are you sure you want to proceed? we will share this information \n BLA BLA BLA BLA BLA");

        builder.setPositiveButton("GO !", (dialog, which) -> {
            handleCompleteSurvey();
            Intent intent = new Intent(context, SurveyWebViewActivity.class);
            intent.putExtra("url", url);
            context.startActivity(intent);
        });

        builder.setNegativeButton("CANCEL", (dialog, which) -> {
            // TODO perform survey dismiss
            dialog.cancel();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
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
        curSurveyIndex = SurveyUtils.getCurrentSurveyIndex(response, context,fromArrivalsList);
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
            // Don't show survey question bottom sheet if we don't have another questions
            if (haveOnlyHeroQuestion()) return;
            updateSurveyPath = response.getSurveyResponse().getId();
            // Display the bottom sheet containing survey questions
            showAllSurveyQuestions();
            initSurveyAdapter(context, surveyRecycleView);
        });
    }

    /**
     * Mark current survey as done
     */
    public void handleCompleteSurvey() {
        SurveyUtils.markSurveyAsCompleted(context, String.valueOf(curSurveyID));
        // Remove the hero question view
        if (fromArrivalsList) arrivalsList.removeHeaderView(surveyView);
    }

    public void onSubmitSurveyFail() {
        Log.d("SubmitSurveyFail", curSurveyID + "");
    }
}
