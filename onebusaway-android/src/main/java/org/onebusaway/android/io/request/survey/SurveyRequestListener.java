package org.onebusaway.android.io.request.survey;

import org.onebusaway.android.ui.survey.model.StudyResponse;

public interface SurveyRequestListener {
    void onSurveyResponseReceived(StudyResponse response);
    void onSurveyFail();
}