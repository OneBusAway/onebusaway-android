package org.onebusaway.android.ui.survey;

import android.annotation.SuppressLint;
import android.content.Context;
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
import org.onebusaway.android.ui.survey.adapter.SurveyAdapter;

import java.util.List;
import java.util.Objects;

public class SurveyManager {
    private final Context context;
    private final StudyRequestListener studyRequestListener;
    private final SubmitSurveyRequestListener submitSurveyRequestListener;
    private StudyResponse mStudyResponse;
    private int curSurveyIndex = 0;
    private Integer curSurveyID;
    private View surveyHeaderView;
    private RecyclerView surveyRecycleView;
    private Button submitSurveyButton;
    private String updateSurveyPath;
    private ListView arrivalsList;
    private BottomSheetDialog surveyBottomSheet;


    public SurveyManager(Context context, StudyRequestListener studyRequestListener, SubmitSurveyRequestListener submitSurveyRequestListener) {
        this.context = context;
        this.studyRequestListener = studyRequestListener;
        this.submitSurveyRequestListener = submitSurveyRequestListener;
    }

    public void requestSurveyData() {
        ObaStudyRequest surveyRequest = ObaStudyRequest.newRequest();
        StudyRequestTask task = new StudyRequestTask(studyRequestListener);
        task.execute(surveyRequest);
        Log.d("SurveyManager", "Survey requested");
    }

    private void updateData() {
        if (mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions().isEmpty()) {
            // No hero question, remove the header.
            arrivalsList.removeHeaderView(surveyHeaderView);
            return;
        }
        SurveyViewUtils.showButtons(surveyHeaderView);
        handleNextButton(surveyHeaderView);

        StudyResponse.Surveys.Questions heroQuestion = mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions().get(0);
        String questionType = heroQuestion.getContent().getType();

        SurveyViewUtils.showQuestion(context, surveyHeaderView.getRootView(), heroQuestion, questionType);
    }

    private void setSurveyData() {
        if (isResponseValid() || curSurveyIndex == -1) return;
        arrivalsList.addHeaderView(surveyHeaderView);
        updateData();
    }

    public void submitSurveyAnswers(StudyResponse.Surveys survey, boolean heroQuestion) {
        String userIdentifier = SurveyUtils.getUserUUID(context);
        String apiUrl = context.getString(R.string.submit_survey_api_url);
        if (!heroQuestion && updateSurveyPath != null) {
            apiUrl += updateSurveyPath;
        }
        JSONArray surveyResponseBody;
        if (heroQuestion) {
            surveyResponseBody = (SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions().get(0), surveyHeaderView.getRootView()));
        } else {
            surveyResponseBody = (SurveyUtils.getSurveyAnswersRequestBody(survey.getQuestions()));
        }
        if (surveyResponseBody == null) {
            Toast.makeText(context, "Please fill all the questions", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d("SurveyResponseBody", surveyResponseBody.toString());
        //((Activity) context).findViewById(R.id.refreshProgressContainer).setVisibility(View.VISIBLE);
        ObaSubmitSurveyRequest request = new ObaSubmitSurveyRequest.Builder(context, apiUrl).setUserIdentifier(userIdentifier).setSurveyId(survey.getId()).setResponses(surveyResponseBody).setListener(submitSurveyRequestListener).build();

        new Thread(request::call).start();
    }

    public void handleNextButton(View view) {
        Button next = view.findViewById(R.id.nextBtn);
        next.setOnClickListener(v -> submitSurveyAnswers(mStudyResponse.getSurveys().get(curSurveyIndex), true));
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

    private boolean haveOnlyHeroQuestion(){
        int questionsSize = mStudyResponse.getSurveys().get(curSurveyIndex).getQuestions().size();
        return questionsSize == 1;
    }
    @SuppressLint("InflateParams")
    public void initSurveyArrivalsHeaderView(LayoutInflater inflater) {
        surveyHeaderView = inflater.inflate(R.layout.item_survey, null);
    }

    public void initSurveyArrivalsList(ListView arrivalsList) {
        this.arrivalsList = arrivalsList;
    }

    private void initSurveyQuestionsBottomSheet(Context context) {
        if (isResponseValid()) {
            return;
        }

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

    private boolean isResponseValid() {
        return mStudyResponse == null || mStudyResponse.getSurveys() == null || mStudyResponse.getSurveys().isEmpty();
    }

    private boolean isSurveyValid(StudyResponse.Surveys firstSurvey) {
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
        curSurveyIndex = SurveyUtils.getCurrentSurveyIndex(response, context);
        if (curSurveyIndex == -1) return;
        Log.d("CurSurveyIndex", curSurveyIndex + " ");
        curSurveyID = mStudyResponse.getSurveys().get(curSurveyIndex).getId();
        setSurveyData();
    }

    public void onSurveyFail() {
        Log.d("SurveyManager", "Survey Fail");
    }

    public void onSubmitSurveyResponseReceived(SubmitSurveyResponse response) {
        ContextCompat.getMainExecutor(context).execute(() -> {
            if (updateSurveyPath != null) {
                surveyBottomSheet.hide();
                Toast.makeText(context, "Submitted Successfully", Toast.LENGTH_LONG).show();
                return;
            }
            SurveyUtils.markSurveyAsDone(context, String.valueOf(curSurveyID));
            // Remove the hero question
            arrivalsList.removeHeaderView(surveyHeaderView);

            if(haveOnlyHeroQuestion()) return;

            updateSurveyPath = response.getSurveyResponse().getId();
            showAllSurveyQuestions();
            initSurveyAdapter(context, surveyRecycleView);
        });
    }

    public void onSubmitSurveyFail() {
        Log.e("SubmitSurveyFail", curSurveyID + "");
    }
}
