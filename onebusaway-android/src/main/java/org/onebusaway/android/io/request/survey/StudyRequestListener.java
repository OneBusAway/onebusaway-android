package org.onebusaway.android.io.request.survey;

import org.onebusaway.android.io.request.survey.model.StudyResponse;

/**
 * Interface to handle the callbacks for survey-related requests.
 * Implementations of this interface should define how to handle
 * successful and failed survey responses.
 */

public interface StudyRequestListener {
    void onSurveyResponseReceived(StudyResponse response);
    void onSurveyResponseFail();
}