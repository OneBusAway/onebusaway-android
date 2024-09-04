package org.onebusaway.android.io.request.survey.submit;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.onebusaway.android.io.request.RequestBase;
import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse;

import java.util.concurrent.Callable;

/**
 * Represents a request to submit survey data.
 * This class is responsible for creating and executing a POST request
 * to submit survey responses.
 */

public final class ObaSubmitSurveyRequest extends RequestBase implements Callable<SubmitSurveyResponse> {

    private SubmitSurveyRequestListener listener;
    private static final String TAG = "ObaSubmitSurveyRequest";

    private ObaSubmitSurveyRequest(Uri uri, String body, SubmitSurveyRequestListener listener) {
        super(uri, body);
        this.listener = listener;
    }

    public static class Builder extends PostBuilderBase {

        private static Uri URI = null;
        private SubmitSurveyRequestListener listener;

        public Builder(Context context, String path) {
            super(context, path);
            URI = Uri.parse(path);
        }

        public Builder setUserIdentifier(String userIdentifier) {
            try {
                mPostData.appendQueryParameter("user_identifier", userIdentifier);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setStopIdentifier(String stopIdentifier) {
            try {
                mPostData.appendQueryParameter("stop_identifier", stopIdentifier);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setSurveyId(int surveyId) {
            try {
                mPostData.appendQueryParameter("survey_id", Integer.toString(surveyId));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setResponses(JSONArray responses) {
            try {
                mPostData.appendQueryParameter("responses", responses.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setListener(SubmitSurveyRequestListener listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public String buildPostData() {
            return mPostData.build().getEncodedQuery();
        }

        public ObaSubmitSurveyRequest build() {
            return new ObaSubmitSurveyRequest(URI, buildPostData(), listener);
        }
    }

    public static ObaSubmitSurveyRequest newRequest(Context context, String path) {
        return new Builder(context, path).build();
    }

    @Override
    public SubmitSurveyResponse call() {
        SubmitSurveyResponse response = call(SubmitSurveyResponse.class);
        if (response != null) {
            Log.d(TAG, "Response received: " + response);
            if (listener != null) {
                listener.onSubmitSurveyResponseReceived(response);
            }
        } else {
            Log.d(TAG, "No response received or response is null");
            if (listener != null) {
                listener.onSubmitSurveyFail();
            }
        }
        return response;
    }

    @NonNull
    @Override
    public String toString() {
        return "ObaSubmitSurveyRequest[mUri=" + mUri + "]";
    }
}
