package org.onebusaway.android.io.request.survey.submit;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onebusaway.android.R;
import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse;

public class SurveySubmissionExample implements SubmitSurveyRequestListener {

    public void submitSurvey(Context context) {
        String userIdentifier = "testuuid";
        int surveyId = 1;




    }

    @Override
    public void onSubmitSurveyResponseReceived(SubmitSurveyResponse response) {
        Log.e("Amr",response.getSurveyResponse().getUpdatePath());
    }

    @Override
    public void onSubmitSurveyFail() {

    }
}
