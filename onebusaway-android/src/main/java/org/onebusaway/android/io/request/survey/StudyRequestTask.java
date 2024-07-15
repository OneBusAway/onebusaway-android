package org.onebusaway.android.io.request.survey;

import android.os.AsyncTask;
import android.util.Log;

import org.onebusaway.android.io.request.survey.model.StudyResponse;

public class StudyRequestTask extends AsyncTask<ObaStudyRequest, Void, StudyResponse> {
    private static final String TAG = "Survey Request";
    private StudyRequestListener mListener;

    public StudyRequestTask(StudyRequestListener listener) {
        mListener = listener;
    }

    @Override
    protected StudyResponse doInBackground(ObaStudyRequest... requests) {
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