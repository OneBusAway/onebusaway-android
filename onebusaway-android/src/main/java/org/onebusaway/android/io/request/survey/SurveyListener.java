package org.onebusaway.android.io.request.survey;

import org.onebusaway.android.io.request.survey.submit.SubmitSurveyRequestListener;
import org.onebusaway.android.ui.survey.SurveyActionsListener;

public interface SurveyListener extends StudyRequestListener, SubmitSurveyRequestListener, SurveyActionsListener {
}