package org.onebusaway.android.io.request.survey;

import org.onebusaway.android.io.request.survey.model.StudyResponse;

public interface StudyRequestListener {
    void onSurveyResponseReceived(StudyResponse response);
    void onSurveyResponseFail();
}