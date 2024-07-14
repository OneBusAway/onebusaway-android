package org.onebusaway.android.io.request.survey;

import android.os.AsyncTask;
import android.util.Log;

import org.onebusaway.android.ui.survey.model.StudyResponse;

public class SurveyRequestTask extends AsyncTask<ObaSurveyRequest, Void, StudyResponse> {
    private static final String TAG = "Survey Request";
    private SurveyRequestListener mListener;

    public SurveyRequestTask(SurveyRequestListener listener) {
        mListener = listener;
    }

    @Override
    protected StudyResponse doInBackground(ObaSurveyRequest... requests) {
        try {
            return requests[0].call();
        } catch (Exception e) {
            Log.e(TAG, "Error executing survey request", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(StudyResponse response) {
        if (response != null) {
            mListener.onSurveyResponseReceived(response);
        } else {
            mListener.onSurveyFail();
        }
    }
}