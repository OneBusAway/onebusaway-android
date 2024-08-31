package org.onebusaway.android.ui.survey;

/**
 * Interface for handling actions related to dismissing survey dialogs.
 */
public interface SurveyActionsListener {
    void onSkipSurvey();
    void onRemindMeLater();
    void onCancelSurvey();
}
