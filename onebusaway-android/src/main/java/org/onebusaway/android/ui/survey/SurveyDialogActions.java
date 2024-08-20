package org.onebusaway.android.ui.survey;

public class SurveyDialogActions {

    private static SurveyActionsListener listener;

    public static void setDialogActionListener(SurveyActionsListener dialogListener) {
        listener = dialogListener;
    }

    public static void handleSkipSurvey() {
        if (listener == null) return;
        listener.onSkipSurvey();
    }

    public static void handleRemindMeLater() {
        if (listener == null) return;
        listener.onRemindMeLater();
    }

    public static void handleCancel() {
        if (listener == null) return;
        listener.onCancelSurvey();
    }
}
