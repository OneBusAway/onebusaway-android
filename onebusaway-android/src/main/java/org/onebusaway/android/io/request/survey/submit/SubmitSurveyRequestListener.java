package org.onebusaway.android.io.request.survey.submit;

import org.onebusaway.android.io.request.survey.model.StudyResponse;
import org.onebusaway.android.io.request.survey.model.SubmitSurveyResponse;

public interface SubmitSurveyRequestListener {
    void onSubmitSurveyResponseReceived(SubmitSurveyResponse response);
    void onSubmitSurveyFail();
}