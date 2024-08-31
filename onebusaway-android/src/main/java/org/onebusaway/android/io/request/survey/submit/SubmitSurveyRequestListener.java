package org.onebusaway.android.io.request.survey.submit;

import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse;

/**
 * Interface to handle the callbacks for survey-related requests.
 * Implementations of this interface should define how to handle
 * successful and failed survey responses.
 */

public interface SubmitSurveyRequestListener {
    void onSubmitSurveyResponseReceived(SubmitSurveyResponse response);
    void onSubmitSurveyFail();
}